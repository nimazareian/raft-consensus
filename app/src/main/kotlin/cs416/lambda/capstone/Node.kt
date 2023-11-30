package cs416.lambda.capstone

import cs416.lambda.capstone.config.NodeConfig
import cs416.lambda.capstone.state.Event
import cs416.lambda.capstone.state.NodeState
import cs416.lambda.capstone.state.initializeNodeState
import cs416.lambda.capstone.util.ObserverFactory
import cs416.lambda.capstone.util.asFullDebugString
import cs416.lambda.capstone.util.asInfoString
import cs416.lambda.capstone.util.asShortInfoString
import cs416.lambda.capstone.util.asString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.RuntimeException
import kotlin.math.min

private val logger = KotlinLogging.logger {}

class Node(
    private val nodeId: Int,
    nodeConfigs: List<NodeConfig>
) {
    private var currentTerm: Long = 0
    private var currentLeader: Int? = null
    private var logs = NodeLogs()

    /**
     * Fields used for leader election
     */
    private var votesReceived = mutableSetOf<Int>()

    // The node that we voted for in the *current term*
    private var votedFor: Int? = null

    /**
     * Fields used by the Leader node
     */

    // Map of node ID to the number of log entries we have sent to a follower
    private var sentLength = mutableMapOf<Int, Long>()

    // Map of node ID to the number of log entries that a follower has acknowledged
    // to have received
    private var ackedLength = mutableMapOf<Int, Long>()

    private val mutex = Mutex()

    private val stateMachine = initializeNodeState(
        ::startElection,
        ::startSendingHeartBeats,
        ::listenForHeartbeats
    )

    private val nodes: ArrayList<StubNode> = arrayListOf()


    init {
        val requestVoteResponseObserver = ObserverFactory.buildProtoObserver(mutex, handleVoteResponseCallback())

        val appendEntriesResponseStreamObserver =
            ObserverFactory.buildProtoObserver(mutex, handleAppendEntriesResponseCallback())

        // List of RPC Senders
        // stub class for communicating with other nodes
        nodes.addAll(
            ArrayList(nodeConfigs
                .map { n ->
                    StubNode(
                        n.id,
                        n.address,
                        n.port,
                        requestVoteResponseObserver,
                        appendEntriesResponseStreamObserver
                    )
                }
            )
        )

        logger.debug { "Node $nodeId created" }
        logger.debug { "Known stub nodes include $nodes" }
    }

    /** Heartbeats setup **/
    // TODO: Whenever we stop using one timer to use another, we might
    //       have to cancel all previous timers

    // Used by Follower to detect leader failure
    private val heartBeatTimeoutTimer = ResettableTimer(
        callback = this::heartBeatTimeout,
        delay = MIN_HEART_BEAT_TIMEOUT_MS + nodeId * HEART_BEAT_TIMEOUT_MULTIPLIER_MS,
        startNow = true,
        initialDelay = MIN_HEART_BEAT_TIMEOUT_MS + nodeId * HEART_BEAT_TIMEOUT_MULTIPLIER_MS,
    )

    // Used by Candidate during elections
    private val electionTimeoutTimer = ResettableTimer(
        callback = this::electionTimeout,
        delay = MIN_ELECTION_TIMEOUT_MS,
        startNow = false,
    )

    // Used by Leader to send heartbeats
    private val sendHeartBeatTimer = ResettableTimer(
        callback = this::sendHeartbeat,
        delay = SEND_HEART_BEAT_TIMER_MS,
        startNow = false,
    )

    /**
     * Node constructor used for loading a node from disk
     */
    constructor(
        nodeId: Int,
        currentTerm: Long,
        votedFor: Int,
        log: NodeLogs,
        nodeConfigs: List<NodeConfig>
    ) : this(nodeId, nodeConfigs) {
        this.currentTerm = currentTerm
        this.votedFor = votedFor
        this.logs = log
    }

    private fun handleAppendEntriesResponseCallback() = { response: AppendEntriesResponse ->
        logger.debug { "Received response back from node ${response.nodeId}" }
        val node = nodes.firstOrNull { it.stubNodeId == response.nodeId }
        if (node == null) {
            logger.error { "Received response from unknown node ${response.nodeId}" }
        } else {
            if (response.isSuccessful) {
                val ackLen = response.logAckLen.toInt()
                node.matchIndex = ackLen
                node.nextIndex = ackLen + 1
                logger.debug { "Updated node ${node.stubNodeId}: matchIndex=${node.matchIndex}, nextIndex=${node.nextIndex}" }

                // Update the commit index to the highest log index which the
                // quorum of nodes have acknowledged
                nodes.sortBy { it.matchIndex }
                logs.commit(nodes[(nodes.size - 1) / 2].matchIndex)
            } else {
                node.decreaseIndex()
            }

            if (response.currentTerm > currentTerm) {
                logger.debug { "Discovered new term ${response.currentTerm} from AppendEntriesResponse ${response.nodeId}" }
                currentTerm = response.currentTerm
                stateMachine.transition(Event.NewTermDiscovered)
            }
        }
    }

    private fun handleVoteResponseCallback() = { response: VoteResponse ->
        // Ignore vote responses if we are not a candidate
        if (stateMachine.state == NodeState.Candidate) {
            if (response.voteGranted && response.currentTerm == currentTerm) {
                logger.debug { "Received vote response back from node ${response.nodeId}" }
                votesReceived.add(response.nodeId)
                if (votesReceived.size > (nodes.size + 1) / 2.0) {
                    logger.debug { "Received quorum ${votesReceived.size}/${nodes.size + 1}. Votes from $votesReceived" }
                    stateMachine.transition(Event.ReceivedQuorum)
                }
            } else {
                logger.debug { "Received vote response back from node ${response.nodeId} but it was not granted" }
            }

            if (response.currentTerm > currentTerm) {
                logger.debug { "Discovered new term ${response.currentTerm} from VoteResponse from node ${response.nodeId}" }
                currentTerm = response.currentTerm
                stateMachine.transition(Event.NewTermDiscovered)
            }
        } else {
            logger.debug { "Received vote response back from node ${response.nodeId} but I am not a candidate" }
        }
    }

    /**
     * Process a VoteRequest
     */
    fun handleVoteRequest(request: VoteRequest): VoteResponse {
        val response = VoteResponse
            .newBuilder()
            .setNodeId(nodeId)

        logger.debug { "Received: ${request.asShortInfoString()}" }

        if (stateMachine.state != NodeState.Leader) {
            logger.debug { "Resetting heartbeat timer" }
            heartBeatTimeoutTimer.resetTimer()
        }

        // if we have already voted then wait for next election.
        // update our term so we are ready to vote
        if (this.currentTerm < request.currentTerm) {
            stateMachine.transition(Event.NewTermDiscovered)
        }

        if (currentTerm <= request.currentTerm &&
            (votedFor == null || votedFor == request.candidateId) &&
            request.lastLogIndex >= logs.commitIndex
        ) {
            // TODO: When do we reset votedFor
            votedFor = request.candidateId
            return response
                .setCurrentTerm(request.currentTerm)
                .setVoteGranted(true)
                .build()
                .also {
                    logger.debug { "Accepted vote request and voting for ${request.candidateId}" }
                }
        } else {
            return response
                .setCurrentTerm(currentTerm)
                .setVoteGranted(false)
                .build()
                .also {
                    logger.debug { "Denied vote request to ${request.candidateId} for term ${request.currentTerm} since I have already voted for $votedFor" }
                }
        }

    }

    /**
     * Process a AppendEntriesRequest
     */
    fun handleAppendEntriesRequest(request: AppendEntriesRequest): AppendEntriesResponse {
        return runBlocking {
            mutex.withLock {
                logger.debug { "Received: ${request.asFullDebugString()}" }

                val response = AppendEntriesResponse
                    .newBuilder()
                    .setNodeId(this@Node.nodeId)

                // Reset the heartbeat timeout
                if (stateMachine.state != NodeState.Leader) {
                    logger.debug { "Resetting heartbeat timer" }
                    heartBeatTimeoutTimer.resetTimer()
                }

                if (request.currentTerm < this@Node.currentTerm) {
                    // TODO: Include log_ack_len, missing fields
                    return@runBlocking response
                        .setCurrentTerm(this@Node.currentTerm)
                        .setLogAckLen(logs.lastIndex().toLong())
                        .setIsSuccessful(false)
                        .build()
                        .also {
                            logger.debug {
                                "This node's term (${this@Node.currentTerm}) exceeds the term of the request (${request.asShortInfoString()}), replying with response unsuccessful message"
                            }
                        }
                }

                // TODO maybe use greater or equal
                if (currentTerm < request.currentTerm) {
                    currentTerm = request.currentTerm
                    votedFor = null
                    currentLeader = request.leaderId
                    stateMachine
                        .transition(Event.NewTermDiscovered)
                        .also {
                            logger.debug { "This node's term (${this@Node.currentTerm}) is smaller than the term of the request (${request.asShortInfoString()}), setting current term to request's term" }
                        }
                }

                // TODO: need to test later with leader failures
                // check that prev log entry term of our state matches log entry term of request
                if (!logs.checkIndexTerm(request.prevLogIndex.toInt(), request.prevLogTerm)) {
                    return@runBlocking response
                        .setCurrentTerm(currentTerm)
                        .setLogAckLen(logs.lastIndex().toLong())
                        .setIsSuccessful(false)
                        .build()
                        .also {
                            logger.warn { "Leader is tweaking fr" }
                        }
                }

                // initialize the start and end indexes that will get updated in this node's logs
                val startIdx = request.prevLogIndex.toInt() + 1
                val endIdx = startIdx + request.entriesCount

                logger.debug { "Starting log update with log = $logs and start = $startIdx and end = $endIdx" }

                // loop through request entries and update the log
                // TODO bug? endIdx is inclusive
                (startIdx..<endIdx)
                    .toList()
                    .zip(request.entriesList) // TODO do we need to reverse list
                    .forEach { (idx, entry) ->
                        if (!logs.checkIndexTerm(idx, entry.term)) {
                            // Existing entry conflicts with new one (same index but different terms)
                            // Delete existing entry and all that follow it
                            logger.debug { "Pruning logs starting at idx=$idx" }
                            logs.prune(idx)
                        }
                        logger.debug { "Setting log entry=${entry.asString()} to index=$idx" }
                        logs[idx] = entry
                    }


                currentLeader = request.leaderId
                // TODO BUG?: logs.commit(request.leaderCommitIndex.toInt()) // leader commit index is -1
                // Assumes that all logs up to end idx is committed
                val leaderIndex = request.leaderCommitIndex.toInt()
                // if leader has a greater commit index, then commit up to thew minimum his index, or the last index (write errors may have occured)
                // if the index is less, just commit to the last index
                val lastIndex = logs.lastIndex()
                val commitIndex = if (logs.commitIndex < leaderIndex) min(logs.lastIndex(), leaderIndex) else lastIndex
                logger.debug { "Committing to index $commitIndex" }
                logs.commit(commitIndex)


                val responseProto = response
                    .setCurrentTerm(this@Node.currentTerm)
                    // TODO: Implement AppendEntries RPC Receiver Implementation logic steps 2-5
                    //       https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14
                    //  -- step 2 [x] checked by the first checkIndexTerm (4th if statement) - das a lot of if statements hmm
                    //  -- step 3 [x] occurs by log pruning when updating entries
                    // rest of steps should also be done
                    .setLogAckLen(lastIndex.toLong())
                    .setIsSuccessful(true)
                    .build()

                logger.debug { "Sending response ${responseProto.asInfoString()}, $lastIndex" }
                return@runBlocking responseProto
            }
        }
    }

    suspend fun handleClientRequest(request: ClientAction): Boolean {
        logger.info { "handling client request" }
        // TODO: Forward request as a LogEntry to all stub nodes and
        //       once the majority of the nodes have acknowledged the request
        //       commit the request and respond back to the client
        if (stateMachine.state != NodeState.Leader) {
            // TODO: Respond back to the client that we are not the leader
            logger.warn { "Trying to handle client request while in state ${stateMachine.state}" }
            return false
        }

        val entry = LogEntry
            .newBuilder()
            .setTerm(currentTerm)
            .setAction(request)
            .build()

        logger.info { "Appending log entry ${entry.asString()}" }

        val index = logs.append(entry)

        // Asynchronously send updated log entries to all nodes and update the tracked state
        propagateLogToFollowers()

        // TODO: Consider using a CompletableDeferred instead of polling
        while (logs.commitIndex < index) {
            delay(5)
        }

        return true
    }

    private fun propagateLogToFollowers() {
        logger.debug { "Starting broadcasting to $nodes" }
        // TODO: Note that doing the timer like this could result in the heartbeats
        //       not be the exact same duration apart?!
        // Set up a timer for the next heartbeat
        runCatching {
            sendHeartBeatTimer.resetTimer()
        }.onSuccess {
            logger.debug { "Successfully reset heartbeat timer" }
        }.onFailure {
            logger.debug { "Error occurred resetting heartbeat timer: $it." }
        }


        nodes.map { stubNode ->
            logger.debug { "Sending AppendEntriesRequest to ${stubNode.address}:${stubNode.port}" }

            val prevLogIndexForFollowerNode =
                stubNode.nextIndex - 1 // TODO index of log entry that precedes entries this node is sending
            val prevLogTermForFollowerNode = logs[prevLogIndexForFollowerNode]?.term

            if (prevLogIndexForFollowerNode != -1 && prevLogTermForFollowerNode == null) {
                throw RuntimeException("Term in previous log entry is null. prevLogIndexForFollowerNode = $prevLogIndexForFollowerNode")
            }
            val appendRequest = AppendEntriesRequest
                .newBuilder()
                .setCurrentTerm(this@Node.currentTerm)
                .setLeaderId(this@Node.nodeId)
                .setLeaderCommitIndex(this@Node.logs.commitIndex.toLong())
                .setPrevLogIndex(prevLogIndexForFollowerNode.toLong())
                .setPrevLogTerm(prevLogTermForFollowerNode ?: -1L)
                .addAllEntries(this@Node.logs.starting(prevLogIndexForFollowerNode + 1)) // TODO: Include entries
                .build()
            stubNode.appendEntries(appendRequest)
        }
    }

    private fun sendHeartbeat() {
        // Only send heartbeats if we are the leader
        if (stateMachine.state != NodeState.Leader) {
            logger.warn { "Trying to send heartbeat while in state ${stateMachine.state}" }
            return
        }

        // Asynchronously send heartbeats (AppendEntriesRequest) to all nodes and update the tracked state
        propagateLogToFollowers()
    }

    private fun startElection() {
        // Cancel timers for other states
        heartBeatTimeoutTimer.cancel()
        electionTimeoutTimer.cancel()

        // Only send heartbeats if we are the leader
        if (stateMachine.state != NodeState.Candidate) {
            logger.warn { "Trying to start an election while in state ${stateMachine.state}" }
            return
        }

        // Reset votes (we vote for ourself by default)
        votesReceived = mutableSetOf(nodeId)
        votedFor = nodeId

        // Increment term
        currentTerm += 1

        // Start timer
        electionTimeoutTimer.resetTimer()

        logger.debug { "Starting election for term $currentTerm" }

        // Asynchronously send vote requests to all nodes and update the tracked state
        nodes.map { n ->
            logger.debug { "Requesting vote from node ${n.address}:${n.port}" }
            n.requestVote(voteRequest {
                candidateId = this@Node.nodeId
                currentTerm = this@Node.currentTerm
                lastLogIndex = this@Node.logs.lastIndex().toLong()
                lastLogTerm = (this@Node.logs.lastTerm() ?: -1).toLong()
            })
        }
    }

    private fun startSendingHeartBeats() {
        logger.debug { "Starting to send heartbeats" }

        // Cancel timers for previous states
        heartBeatTimeoutTimer.cancel()
        electionTimeoutTimer.cancel()

        // Send a heartbeat immediately and restart the timer
        // to send heartbeats periodically
        sendHeartBeatTimer.resetTimer()
        sendHeartbeat()
    }

    private fun listenForHeartbeats() {
        logger.debug { "Listening for Heartbeats" }

        // Cancel timers for other states
        sendHeartBeatTimer.cancel()
        electionTimeoutTimer.cancel()

        // Only listen for heartbeats if we are a follower
        if (stateMachine.state != NodeState.Leader) {
            // Reset the heartbeat timeout
            heartBeatTimeoutTimer.resetTimer()
        } else {
            logger.warn { "Trying to listen for heartbeats while in state ${stateMachine.state}" }
        }
    }

    private fun electionTimeout() {
        logger.debug { "Election timer timed out" }
        stateMachine.transition(Event.ElectionTimeout)
    }

    private fun heartBeatTimeout() {
        logger.debug { "Heartbeat timer timed out" }
        stateMachine.transition(Event.LeaderFailureSuspected)
    }

    fun close() {
        nodes.forEach { n -> n.close() }
    }

    override fun toString(): String {
        return "Node(id='$nodeId', state=${stateMachine.state}, leader=$currentLeader currentTerm=$currentTerm, votedFor=$votedFor)"
    }
}


