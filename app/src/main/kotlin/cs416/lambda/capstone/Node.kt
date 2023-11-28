package cs416.lambda.capstone

import com.tinder.StateMachine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

private val logger = KotlinLogging.logger {}

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
    private val nodes: ArrayList<StubNode>
) {
    init {
        logger.debug { "Node $nodeId created" }
        logger.debug { "Known stub nodes include $nodes" }
    }

    private var currentTerm: Long = 0;
    private var currentLeader: Int? = null;
    private var logs = NodeLogs();

    // How many log entries since the start have we committed
    // TODO: Can we remove this and read it from NodeLog?
    private var commitLength: Long = 0;

    /**
     * Fields used for leader election
     */
    private var votesReceived = mutableSetOf<Int>();

    // The node that we voted for in the *current term*
    private var votedFor: Int? = null;

    /**
     * Fields used by the Leader node
     */

    // Map of node ID to the number of log entries we have sent to a follower
    private var sentLength = mutableMapOf<Int, Long>();

    // Map of node ID to the number of log entries that a follower has acknowledged
    // to have received
    private var ackedLength = mutableMapOf<Int, Long>();

    private val mutex = Mutex();

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

    /** Heartbeats setup **/
    // TODO: Whenever we stop using one timer to use another, we might
    //       have to cancel all previous timers

    // Used by Follower to detect leader failure
    private val heartBeatTimeoutTimer = ResettableTimer(
        callback = this::heartBeatTimeout,
        delay = MIN_HEART_BEAT_TIMEOUT_MS + nodeId * 1000L,
        startNow = true,
        initialDelay = MIN_HEART_BEAT_TIMEOUT_MS + nodeId * 1000L,
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
        nodes: ArrayList<StubNode>
    ) : this(nodeId, nodes) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logs = log;
    }

    fun requestVote(request: VoteRequest) = voteResponse {
        logger.debug { "VoteRequest received: $request" }
        nodeId = this@Node.nodeId

        if (request.currentTerm > this@Node.currentTerm) {
            logger.debug { "Discovered new term ${request.currentTerm} from VoteRequest from candidate ${request.candidateId}" }
            votedFor = null
            this@Node.currentTerm = request.currentTerm;
            stateMachine.transition(Event.NewTermDiscovered);
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
        logger.debug { "AppendRequest received: $request from leader ${request.leaderId}" }

        if (request.currentTerm < this.currentTerm) return appendEntriesResponse { isSuccessful = false }
            .also { logger.debug { "AppendRequest term exceeds this node's term, replying with error response" } }

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
            return;
        }
        logger.debug { "Broadcasting heartbeats to $nodes" }

        // TODO: Note that doing the timer like this could result in the heartbeats
        //       not be the exact same duration apart.
        // Set up a timer for the next heartbeat
        sendHeartBeatTimer.resetTimer();


        // TODO: Consider making this non blocking (could look into using coroutineScope)
        // TODO: Will this block forever if a node is down?
        runBlocking {
            // Asynchronously send heartbeats to all nodes and update the tracked state
            nodes.map { n ->
                coroutineScope {
                    launch {
                        logger.debug { "Sending heartbeat to node ${n.address}:${n.port}" }
                        val response = n.appendEntries(appendEntriesRequest {
                            currentTerm = this@Node.currentTerm
                            leaderId = this@Node.nodeId
                            prevLogIndex = 0 // TODO: update these fields
                            commitLength = 0 // TODO:
                            // Ignoring entries
                        })

                        logger.debug { "Received response back from node ${n.address}:${n.port}" }
                        if (response.isSuccessful) {
                            ackedLength[response.nodeId] = response.logAckLen;
                        }

                        if (response.currentTerm > currentTerm) {
                            logger.debug { "Discovered new term ${response.currentTerm} from AppendEntriesResponse ${response.nodeId}" }
                            currentTerm = response.currentTerm;
                            stateMachine.transition(Event.NewTermDiscovered);
                        }
                    }
                }
            }
        }
    }

    private fun startElection() {
        // Cancel timers for other states
        heartBeatTimeoutTimer.cancel();
        electionTimoutTimer.cancel();

        // Only send heartbeats if we are the leader
        if (stateMachine.state != NodeState.Candidate) {
            logger.warn { "Trying to start an election while in state ${stateMachine.state}" }
            return;
        }

        // Reset votes (we vote for ourself by default)
        votesReceived = mutableSetOf(nodeId);
        votedFor = nodeId;

        // Increment term
        currentTerm += 1

        // Start timer
        electionTimoutTimer.resetTimer();

        logger.debug { "Starting election for term $currentTerm" }

        runBlocking {
            // Asynchronously send heartbeats to all nodes and update the tracked state
            logger.debug { "Mapping over nodes $nodes" }
            nodes.map { n ->
                logger.debug { "Requesting vote from node ${n.address}:${n.port}" }
                val response = n.requestVote(voteRequest {
                    candidateId = this@Node.nodeId
                    currentTerm = this@Node.currentTerm
                    lastLogIndex = 0
                    lastLogTerm = 0
                });

                if (response.voteGranted && response.currentTerm == currentTerm) {
                    logger.debug { "Received vote response back from node ${n.address}:${n.port}" }
                    votesReceived.add(response.nodeId);
                    if (votesReceived.size > (nodes.size + 1) / 2.0) {
                        logger.debug { "Received quorum ${votesReceived.size}/${nodes.size + 1}" }
                        stateMachine.transition(Event.ReceivedQuorum)
                        return@runBlocking
                    }
                } else {
                    logger.debug { "Received vote response back from node ${n.address}:${n.port} but it was not granted" }
                }

                if (response.currentTerm > currentTerm) {
                    logger.debug { "Discovered new term ${response.currentTerm} from VoteResponse from node ${response.nodeId}" }
                    currentTerm = response.currentTerm;
                    stateMachine.transition(Event.NewTermDiscovered)
                }
            }
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
        heartBeatTimeoutTimer.cancel();
        electionTimoutTimer.cancel();

        sendHeartBeatTimer.resetTimer();
        sendHeartbeat();
    }

    private fun listenForHeartbeats() {
        logger.debug { "Listening for Heartbeats" }

        // Cancel timers for other states
        sendHeartBeatTimer.cancel();
        electionTimoutTimer.cancel();

        // Only listen for heartbeats if we are a follower
        if (stateMachine.state == NodeState.Leader) {
            logger.warn { "Trying to listen for heartbeats while in state ${stateMachine.state}" }
            return;
        }

        // Reset the heartbeat timeout
        heartBeatTimeoutTimer.resetTimer();
    }

    override fun toString(): String {
        return "Node(id='$nodeId', state=${stateMachine.state}, leader=$currentLeader currentTerm=$currentTerm)"
    }
}