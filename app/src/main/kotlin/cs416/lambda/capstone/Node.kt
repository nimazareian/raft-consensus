package cs416.lambda.capstone

import com.tinder.StateMachine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    nodeConfigs: List<NodeConfig>
) {
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

    private val requestVoteResponseObserver: StreamObserver<VoteResponse> = object : StreamObserver<VoteResponse> {
        override fun onNext(response: VoteResponse?) {
            if (response == null) {
                logger.warn { "Received null VoteResponse" }
                return;
            }

            // TODO: Add mutex lock
            if (response.voteGranted && response.currentTerm == currentTerm) {
                logger.debug { "Received vote response back from node ${response.nodeId}" }
                votesReceived.add(response.nodeId);
                if (votesReceived.size > (nodes.size + 1) / 2.0) {
                    logger.debug { "Received quorum ${votesReceived.size}/${nodes.size + 1}" }
                    stateMachine.transition(Event.ReceivedQuorum);
                }
            } else {
                logger.debug { "Received vote response back from node ${response.nodeId} but it was not granted" }
            }

            if (response.currentTerm > currentTerm) {
                logger.debug { "Discovered new term ${response.currentTerm} from VoteResponse from node ${response.nodeId}" }
                currentTerm = response.currentTerm;
                stateMachine.transition(Event.NewTermDiscovered);
            }
        }

        override fun onError(t: Throwable?) {
            println("StubNode VoteResponse onError $t")
        }

        override fun onCompleted() {
            println("StubNode VoteResponse onCompleted")
        }
    }

    private val appendEntriesResponseStreamObserver: StreamObserver<AppendEntriesResponse> = object :
        StreamObserver<AppendEntriesResponse> {
        override fun onNext(response: AppendEntriesResponse?) {
            if (response == null) {
                logger.warn { "Received null AppendEntriesResponse" }
                return;
            }

            // TODO: Add mutex lock
            logger.debug { "Received response back from node ${response.nodeId}" }
            if (response.isSuccessful) {
                ackedLength[response.nodeId] = response.logAckLen;
            }

            if (response.currentTerm > currentTerm) {
                logger.debug { "Discovered new term ${response.currentTerm} from AppendEntriesResponse ${response.nodeId}" }
                currentTerm = response.currentTerm;
                stateMachine.transition(Event.NewTermDiscovered);
            }
        }

        override fun onError(t: Throwable?) {
            println("StubNode AppendEntriesResponse onError $t")
        }

        override fun onCompleted() {
            println("StubNode AppendEntriesResponse onCompleted")
        }
    }

    // RPC Sender
    // stub class for communicating with other nodes
    private val nodes = ArrayList<StubNode>(nodeConfigs.map{n -> StubNode(n.host, n.port, requestVoteResponseObserver, appendEntriesResponseStreamObserver)});

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
        nodeConfigs: List<NodeConfig>
    ) : this(nodeId, nodeConfigs) {
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

    fun appendEntries(request: AppendEntriesRequest) = appendEntriesResponse {
        logger.debug { "Append request received: $request from leader ${request.leaderId}" }

        if (request.currentTerm > this@Node.currentTerm) {
            logger.debug { "Discovered new term ${request.currentTerm} from AppendEntriesRequest from leader ${request.leaderId}" }
            this@Node.currentTerm = request.currentTerm;
            stateMachine.transition(Event.NewTermDiscovered);
        }

        // Reset the heartbeat timeout
        if (stateMachine.state != NodeState.Leader)
        {
            logger.debug { "Received AppendEntriesRequest resetting heartbeat timer" }
            heartBeatTimeoutTimer.resetTimer()
        }

        // Response to client
        nodeId = this@Node.nodeId
        currentTerm = this@Node.currentTerm
        if (request.currentTerm > this@Node.currentTerm) {
            isSuccessful = false
        } else {
            isSuccessful = true
        }

        // TODO: Implement AppendEntries RPC Receiver Implementation logic steps 2-5
        //       https://web.stanford.edu/~ouster/cgi-bin/papers/raft-atc14

        logAckLen = 0 // TODO
    }

    private fun sendHeartbeat() {
        // Only send heartbeats if we are the leader
        if (stateMachine.state != NodeState.Leader) {
            logger.warn { "Trying to send heartbeat while in state ${stateMachine.state}" }
            return;
        }

        // TODO: Note that doing the timer like this could result in the heartbeats
        //       not be the exact same duration apart.
        // Set up a timer for the next heartbeat
        sendHeartBeatTimer.resetTimer();

        // Asynchronously send heartbeats to all nodes and update the tracked state
        nodes.map { n ->
            logger.debug { "Sending heartbeat to node ${n.host}:${n.port}" }
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
        currentTerm += 1;

        // Start timer
        electionTimoutTimer.resetTimer();

        logger.debug { "Starting election for term $currentTerm" }

        // Asynchronously send heartbeats to all nodes and update the tracked state
        logger.debug { "Mapping over nodes $nodes" }
        nodes.map { n ->
            logger.debug { "Requesting vote from node ${n.host}:${n.port}" }
            n.requestVote(voteRequest {
                candidateId = this@Node.nodeId
                currentTerm = this@Node.currentTerm
                lastLogIndex = 0
                lastLogTerm = 0
            });
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
        logger.debug { "Listening" }

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
}