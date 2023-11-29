package cs416.lambda.capstone

import com.tinder.StateMachine
import cs416.lambda.capstone.config.NodeConfig
import cs416.lambda.capstone.util.asShortInfoString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

/**
 * Classes used to represent the States, Events, and SideEffects of the Raft state machine
 */
sealed class NodeState {
    object Follower : NodeState()
    object Candidate : NodeState()
    object Leader : NodeState()
}

sealed class Event {
    object LeaderFailureSuspected : Event()
    object ReceivedQuorum : Event()
    object NewTermDiscovered : Event()
    object ElectionTimeout : Event()
}

sealed class SideEffect {
    object StartElection : SideEffect()
    object StartSendingHeartbeats : SideEffect()
    object ListenForHeartbeats : SideEffect()
}

class Node(
    private val nodeId: Int,
    nodeConfigs: List<NodeConfig>
) {
    private var currentTerm: Long = 0
    private var currentLeader: Int? = null
    private var logs = NodeLogs()

    // How many log entries since the start have we committed
    // TODO: Can we remove this and read it from NodeLog?
    private var commitLength: Long = 0

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

    private val stateMachine = StateMachine.create<NodeState, Event, SideEffect> {
        // Start in the follower state
        initialState(NodeState.Follower)

        state<NodeState.Follower> {
            on<Event.LeaderFailureSuspected> {
                transitionTo(NodeState.Candidate, SideEffect.StartElection)
            }
        }

        state<NodeState.Candidate> {
            on<Event.ReceivedQuorum> {
                transitionTo(NodeState.Leader, SideEffect.StartSendingHeartbeats)
            }
            on<Event.NewTermDiscovered> {
                transitionTo(NodeState.Follower, SideEffect.ListenForHeartbeats)
            }
            on<Event.ElectionTimeout> {
                transitionTo(NodeState.Candidate, SideEffect.StartElection)
            }
        }

        state<NodeState.Leader> {
            on<Event.NewTermDiscovered> {
                transitionTo(NodeState.Follower, SideEffect.ListenForHeartbeats)
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            when (validTransition.sideEffect) {
                SideEffect.StartElection -> startElection()
                SideEffect.StartSendingHeartbeats -> startSendingHeartBeats()
                SideEffect.ListenForHeartbeats -> listenForHeartbeats()
                null -> logger.debug { "No side effect" }
            }
        }
    }

    private val requestVoteResponseObserver: StreamObserver<VoteResponse> = object : StreamObserver<VoteResponse> {
        override fun onNext(response: VoteResponse) {
            runBlocking {
                mutex.withLock {
                    // Ignore vote responses if we are not a candidate
                    if (stateMachine.state != NodeState.Candidate) {
                        logger.debug { "Received vote response back from node ${response.nodeId} but I am not a candidate" }
                        return@withLock
                    }

                    if (response.voteGranted && response.currentTerm == currentTerm) {
                        logger.debug { "Received vote response back from node ${response.nodeId}" }
                        votesReceived.add(response.nodeId)
                        if (votesReceived.size > (nodes.size + 1) / 2.0) {
                            logger.debug { "Received quorum ${votesReceived.size}/${nodes.size + 1}" }
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
                }
            }
        }

        override fun onError(t: Throwable) {
            logger.error { "Error occurred processing StubNode VoteResponse: $t" }
        }

        override fun onCompleted() {
            logger.debug { "Communication completed with StubNode VoteResponse" }
        }
    }

    private val appendEntriesResponseObserver: StreamObserver<AppendEntriesResponse> = object :
        StreamObserver<AppendEntriesResponse> {
        override fun onNext(response: AppendEntriesResponse) {
            runBlocking {
                mutex.withLock {
                    logger.debug { "Received response back from node ${response.nodeId}" }
                    if (response.isSuccessful) {
                        ackedLength[response.nodeId] = response.logAckLen
                    }

                    if (response.currentTerm > currentTerm) {
                        logger.debug { "Discovered new term ${response.currentTerm} from AppendEntriesResponse ${response.nodeId}" }
                        currentTerm = response.currentTerm
                        stateMachine.transition(Event.NewTermDiscovered)
                    }
                }
            }
        }

        override fun onError(t: Throwable) {
            logger.error { "Error occurred processing StubNode AppendEntriesResponse: $t" }
        }

        override fun onCompleted() {
            logger.debug { "Communication completed with StubNode AppendEntriesResponse" }
        }
    }

    // List of RPC Senders
    // stub class for communicating with other nodes
    private val nodes = ArrayList<StubNode>(nodeConfigs
        .map{n -> StubNode(n.address, n.port, requestVoteResponseObserver, appendEntriesResponseObserver)})

    init {
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
    private val electionTimoutTimer = ResettableTimer(
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

    // TODO: refactor this function by removing the inline object construction for voteResponse,
    //  so that we don't need to specify this@ for when we need to use accessors
    fun requestVote(request: VoteRequest) = voteResponse {
        logger.debug { "Received: ${request.asShortInfoString()}" }
        nodeId = this@Node.nodeId

        if (request.currentTerm > this@Node.currentTerm) {
            logger.debug { "Discovered new term in request ${request.asShortInfoString()}, which exceeds current node term: ${this@Node.currentTerm}" }
            votedFor = null
            this@Node.currentTerm = request.currentTerm
            stateMachine.transition(Event.NewTermDiscovered)
        }

        // Response to client
        currentTerm = request.currentTerm

        if (request.currentTerm >= this@Node.currentTerm &&
            (votedFor == null || votedFor == request.candidateId) &&
            request.lastLogIndex >= logs.commitIndex
        ) {
            // TODO: When do we reset votedFor
            logger.debug { "Accepted vote request and voting for ${request.candidateId}" }
            votedFor = request.candidateId
            voteGranted = true
        } else {
            logger.debug { "Denied vote request to ${request.candidateId} for term ${request.currentTerm} since I have already voted for $votedFor" }
            voteGranted = false
        }
    }

    fun appendEntries(request: AppendEntriesRequest): AppendEntriesResponse {
        logger.debug { "Received: ${request.asShortInfoString()}" }

        if (request.currentTerm < this.currentTerm) return appendEntriesResponse {
            // TODO: Include log_ack_len
            nodeId = this@Node.nodeId
            currentTerm = this@Node.currentTerm
            isSuccessful = false
        }
            .also {
                logger.debug { "This node's term (${this.currentTerm}) exceeds the term of the request (${request.asShortInfoString()}), replying with response unsuccessful message"
                }
            }

        if (request.currentTerm > this@Node.currentTerm) {
            this.currentTerm = request.currentTerm
            this@Node.votedFor = null
            this@Node.currentLeader = request.leaderId
            stateMachine.transition(Event.NewTermDiscovered)
            // TODO potential bug as we are not replying. maybe reply with a response and then transition to follower.
        }

        // Reset the heartbeat timeout
        if (stateMachine.state != NodeState.Leader) {
            logger.debug { "Resetting heartbeat timer" }
            heartBeatTimeoutTimer.resetTimer()
        }

        return appendEntriesResponse {
            nodeId = this@Node.nodeId
            // Response to client
            currentTerm = this@Node.currentTerm
            isSuccessful = request.currentTerm <= this@Node.currentTerm

            // TODO: Implement AppendEntries RPC Receiver Implementation logic steps 2-5
            //       https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14

            logAckLen = 0 // TODO
        }
    }

    private fun sendHeartbeat() {
        // Only send heartbeats if we are the leader
        if (stateMachine.state != NodeState.Leader) {
            logger.warn { "Trying to send heartbeat while in state ${stateMachine.state}" }
            return
        }
        logger.debug { "Broadcasting heartbeats to $nodes" }

        // TODO: Note that doing the timer like this could result in the heartbeats
        //       not be the exact same duration apart.
        // Set up a timer for the next heartbeat
        sendHeartBeatTimer.resetTimer()

        // Asynchronously send heartbeats to all nodes and update the tracked state
        nodes.map { n ->
            logger.debug { "Sending heartbeat to node ${n.address}:${n.port}" }
            n.appendEntries(appendEntriesRequest {
                currentTerm = this@Node.currentTerm
                leaderId = this@Node.nodeId
                prevLogIndex = 0 // TODO: update these fields
                commitLength = 0 // TODO:
                // Ignoring entries
            })
        }
    }

    private fun startElection() {
        // Cancel timers for other states
        heartBeatTimeoutTimer.cancel()
        electionTimoutTimer.cancel()

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
        electionTimoutTimer.resetTimer()

        logger.debug { "Starting election for term $currentTerm" }

        // Asynchronously send heartbeats to all nodes and update the tracked state
        nodes.map { n ->
            logger.debug { "Requesting vote from node ${n.address}:${n.port}" }
            n.requestVote(voteRequest {
                candidateId = this@Node.nodeId
                currentTerm = this@Node.currentTerm
                // TODO: update these fields
                lastLogIndex = 0
                lastLogTerm = 0
            })
        }
    }

    private fun electionTimeout() {
        stateMachine.transition(Event.ElectionTimeout)
    }

    private fun heartBeatTimeout() {
        stateMachine.transition(Event.LeaderFailureSuspected)
    }

    private fun startSendingHeartBeats() {
        logger.debug { "Starting to send heartbeats" }

        // Cancel timers for previous states
        heartBeatTimeoutTimer.cancel()
        electionTimoutTimer.cancel()

        // Send a heartbeat immediately and restart the timer
        // to send heartbeats periodically
        sendHeartBeatTimer.resetTimer()
        sendHeartbeat()
    }

    private fun listenForHeartbeats() {
        logger.debug { "Listening for Heartbeats" }

        // Cancel timers for other states
        sendHeartBeatTimer.cancel()
        electionTimoutTimer.cancel()

        // Only listen for heartbeats if we are a follower
        if (stateMachine.state == NodeState.Leader) {
            logger.warn { "Trying to listen for heartbeats while in state ${stateMachine.state}" }
            return
        }

        // Reset the heartbeat timeout
        heartBeatTimeoutTimer.resetTimer()
    }

    fun close() {
        nodes.forEach { n -> n.close() }
    }

    override fun toString(): String {
        return "Node(id='$nodeId', state=${stateMachine.state}, leader=$currentLeader currentTerm=$currentTerm)"
    }
}


