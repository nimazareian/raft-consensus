package cs416.lambda.capstone

import cs416.lambda.capstone.config.NodeConfig
import cs416.lambda.capstone.state.Event
import cs416.lambda.capstone.state.NodeState
import cs416.lambda.capstone.state.initializeNodeState
import cs416.lambda.capstone.util.ObserverFactory
import cs416.lambda.capstone.util.asFullString
import cs416.lambda.capstone.util.asString
import cs416.lambda.capstone.util.asShortString
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

    /** Timer setup **/

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

    /**
     * Process AppendEntriesResponses from followers
     */
    private fun handleAppendEntriesResponseCallback() = { response: AppendEntriesResponse ->
        logger.debug { "Processing ${response.asString()}" }
        val node = nodes.firstOrNull { it.stubNodeId == response.nodeId }
        if (node == null) {
            logger.error { "Received response from unknown node ${response.nodeId}" }
        } else {
            if (response.isSuccessful) {
                val ackIndex = response.logAckIndex.toInt()
                logger.debug { "Updating $node with matchIndex=${node.matchIndex}, nextIndex=${node.nextIndex}" }
                node.matchIndex = ackIndex
                node.nextIndex = ackIndex + 1

                // Update the commit index to the highest log index which the quorum has acknowledged
                val matchIndices: MutableList<Int> = nodes.map { it.matchIndex }.toMutableList()
                matchIndices.add(logs.lastIndex())
                matchIndices.sort()
                val quorumIndex = matchIndices[(matchIndices.size - 1) / 2]
                if (logs.commitIndex < quorumIndex) {
                    val commitIndex = min(logs.lastIndex(), quorumIndex)
                    logs.commit(commitIndex)
                    logger.debug { "Committing to index $commitIndex" }
                } else {
                    logger.debug { "Not updating quorum index on ${logs.commitIndex} as current quorum index is $quorumIndex. matchIndices=$matchIndices" }
                }
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
        logger.debug { "Processing ${response.asString()}" }
        // Ignore vote responses if we are not a candidate
        if (stateMachine.state == NodeState.Candidate) {
            if (response.voteGranted && response.currentTerm == currentTerm) {
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
            logger.debug { "Cannot accept vote response from node ${response.nodeId}, I am not a candidate" }
        }
    }

    /**
     * Process a VoteRequest
     */
    fun handleVoteRequest(request: VoteRequest): VoteResponse {
        return runBlocking {
            mutex.withLock {
                logger.debug { "Processing ${request.asFullString()}" }

                val response = VoteResponse
                    .newBuilder()
                    .setNodeId(nodeId)

                if (stateMachine.state != NodeState.Leader) {
                    logger.debug { "Resetting heartbeat timer" }
                    heartBeatTimeoutTimer.resetTimer()
                }

                // if we have already voted then wait for next election.
                // update our term so we are ready to vote
                if (this@Node.currentTerm < request.currentTerm) {
                    stateMachine.transition(Event.NewTermDiscovered)
                        .also {
                            logger.debug { "This node's term (${this@Node.currentTerm}) is smaller than the term of the request ${request.currentTerm}, setting current term to request's term" }
                        }
                }

                if (currentTerm <= request.currentTerm &&
                    (votedFor == null || votedFor == request.candidateId) &&
                    request.lastLogIndex >= logs.commitIndex
                ) {
                    votedFor = request.candidateId
                    return@runBlocking response
                        .setCurrentTerm(request.currentTerm)
                        .setVoteGranted(true)
                        .build()
                        .also {
                            logger.debug { "Accepted vote request and voting for ${request.candidateId}" }
                        }
                } else {
                    return@runBlocking response
                        .setCurrentTerm(currentTerm)
                        .setVoteGranted(false)
                        .build()
                        .also {
                            logger.debug { "Denied vote request to ${request.candidateId} for term ${request.currentTerm} since I have already voted for $votedFor" }
                        }
                }
            }
        }
    }

    /**
     * Process a AppendEntriesRequest
     */
    fun handleAppendEntriesRequest(request: AppendEntriesRequest): AppendEntriesResponse {
        return runBlocking {
            mutex.withLock {
                logger.debug { "Processing: ${request.asFullString()}" }
//                logger.debug { "Processing: ${request.asFullDebugString()}" }

                val response = AppendEntriesResponse
                    .newBuilder()
                    .setNodeId(this@Node.nodeId)

                // Reset the heartbeat timeout
                if (stateMachine.state != NodeState.Leader) {
                    logger.debug { "Resetting heartbeat timer" }
                    heartBeatTimeoutTimer.resetTimer()
                }

                if (request.currentTerm < this@Node.currentTerm) {
                    return@runBlocking response
                        .setCurrentTerm(this@Node.currentTerm)
                        .setLogAckIndex(logs.lastIndex().toLong())
                        .setIsSuccessful(false)
                        .build()
                        .also {
                            logger.debug {
                                "This node's term (${this@Node.currentTerm}) exceeds the term of the request (${request.asShortString()}), replying with response unsuccessful message"
                            }
                        }
                }

                if (currentTerm < request.currentTerm) {
                    currentTerm = request.currentTerm
                    votedFor = null
                    currentLeader = request.leaderId
                    stateMachine
                        .transition(Event.NewTermDiscovered)
                        .also {
                            logger.debug { "This node's term (${this@Node.currentTerm}) is smaller than the term of the request (${request.asShortString()}), setting current term to request's term" }
                        }
                }

                // check that prev log entry term of our state matches log entry term of request
                if (!logs.checkIndexTerm(request.prevLogIndex.toInt(), request.prevLogTerm)) {
                    return@runBlocking response
                        .setCurrentTerm(currentTerm)
                        .setLogAckIndex(logs.lastIndex().toLong())
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
                (startIdx..<endIdx)
                    .toList()
                    .zip(request.entriesList)
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
                // Assumes that all logs up to end idx is committed
                val leaderIndex = request.leaderCommitIndex.toInt()
                // if leader has a greater commit index, then commit up to thew minimum his index, or the last index (write errors may have occured)
                // if the index is less, just commit to the last index
                val lastIndex = logs.lastIndex()
                if (logs.commitIndex < leaderIndex) {
                    val commitIndex = min(lastIndex, leaderIndex)
                    logs.commit(commitIndex)
                    logger.debug { "Committing to index $commitIndex" }
                }


                val responseProto = response
                    .setCurrentTerm(this@Node.currentTerm)
                    // TODO: Implement AppendEntries RPC Receiver Implementation logic steps 2-5
                    //       https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14
                    //  -- step 2 [x] checked by the first checkIndexTerm (4th if statement) - das a lot of if statements hmm
                    //  -- step 3 [x] occurs by log pruning when updating entries
                    // rest of steps should also be done
                    .setLogAckIndex(lastIndex.toLong())
                    .setIsSuccessful(true)
                    .build()

                logger.debug { "Sending response ${responseProto.asString()}, $lastIndex" }
                return@runBlocking responseProto
            }
        }
    }

    suspend fun handleClientRequest(request: ClientAction): ActionResponse {
        logger.info { "Handling ${request.asString()}" }
        if (stateMachine.state != NodeState.Leader) {
            logger.warn { "Trying to handle client request while in state ${stateMachine.state}" }
            return actionResponse {
                type = ActionResponse.ActionResult.INVALID_NODE
                nodes.firstOrNull { it.stubNodeId == currentLeader }?.let { leader ->
                    leaderAddress = leader.address
                    leaderPort = leader.port
                }
            }
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
        // Pull on the current commit index until the entry has been committed
        // or we timeout.
        val commitStartTime = System.currentTimeMillis()
        while (logs.commitIndex < index) {
            delay(5)
            if (System.currentTimeMillis() - commitStartTime > 5000) {
                logger.warn { "Timed out waiting for log entry to be committed" }
                return actionResponse {
                    type = ActionResponse.ActionResult.REQUEST_TIMEOUT
                }
            }
        }

        return actionResponse {
            type = ActionResponse.ActionResult.SUCCESS
        }
    }

    private fun propagateLogToFollowers() {
        logger.debug { "Starting broadcasting to $nodes" }
        // Set up a timer for the next heartbeat
        runCatching {
            sendHeartBeatTimer.resetTimer()
        }.onFailure {
            logger.debug { "Error occurred resetting heartbeat timer: $it." }
        }


        nodes.map { stubNode ->
            val prevLogIndexForFollowerNode = stubNode.nextIndex - 1
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
                .addAllEntries(this@Node.logs.starting(prevLogIndexForFollowerNode + 1))
                .build()

            logger.debug { "Sending ${appendRequest.asShortString()} to $stubNode" }
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


