package cs416.lambda.capstone

import com.tinder.StateMachine
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val nodes: ArrayList<StubNode>
) {
    init {
        logger.info { "Node $nodeId created" }
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
        delay = MIN_HEART_BEAT_TIMEOUT_MS + nodeId * 500L,
        startNow = true,
        initialDelay = MIN_HEART_BEAT_TIMEOUT_MS + nodeId * 500L,
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
        logger.debug { "Vote request received: $request" }
        nodeId = this@Node.nodeId

        // Response to client
        currentTerm = request.currentTerm

        if (request.currentTerm >= this@Node.currentTerm &&
            (votedFor == null || votedFor == request.candidateId) &&
            request.lastLogIndex >= logs.commitIndex
        ) {
            // TODO: When do we reset votedFor
            votedFor = request.candidateId
            voteGranted = true
        } else {
            voteGranted = false
        }
    }

    fun appendEntries(request: AppendEntriesRequest) = appendEntriesResponse {
        logger.debug { "Append request received: $request from leader ${request.leaderId}" }

        // Reset the heartbeat timeout
        if (stateMachine.state != NodeState.Leader)
        {
            logger.debug { "Resetting timer!" }
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
        logger.info { "Sending heartbeat" }

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
                        logger.debug { "Sending heartbeat to node ${n.host}:${n.port}" }
                        val response = n.appendEntries(appendEntriesRequest {
                            currentTerm = this@Node.currentTerm
                            leaderId = this@Node.nodeId
                            prevLogIndex = 0 // TODO: update these fields
                            commitLength = 0 // TODO:
                            // Ignoring entries
                        })

                        logger.debug { "Received response back from node ${n.host}:${n.port}" }
                        mutex.withLock {
                            if (response.isSuccessful) {
                                ackedLength[response.nodeId] = response.logAckLen;
                            }

                            if (response.currentTerm != currentTerm) {
                                stateMachine.transition(Event.NewTermDiscovered);
                            }
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
        logger.debug { "Starting election" }

        // Reset votes (we vote for ourself by default)
        votesReceived = mutableSetOf(nodeId);
        votedFor = nodeId;

        // Increment term
        currentTerm += 1;

        // Start timer
        electionTimoutTimer.resetTimer();

        runBlocking {
            // Asynchronously send heartbeats to all nodes and update the tracked state
            nodes.map { n ->
                logger.debug { "Requesting vote from node ${n.host}:${n.port}" }
                coroutineScope {
                    launch {
                        val response = n.requestVote(voteRequest {
                            candidateId = this@Node.nodeId
                            currentTerm = this@Node.currentTerm
                            lastLogIndex = 0
                            lastLogTerm = 0
                        });

                        logger.debug { "Received vote response back from node ${n.host}:${n.port}" }
                        if (response.voteGranted && response.currentTerm == currentTerm) {
                            mutex.withLock {
                                votesReceived.add(response.nodeId);
                                if (votesReceived.size > nodes.size / 2) {
                                    stateMachine.transition(Event.ReceivedQuorum);
                                }
                            }
                        }
                    }
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
        // Cancel timers for previous states
        heartBeatTimeoutTimer.cancel();
        electionTimoutTimer.cancel();

        sendHeartBeatTimer.resetTimer();
        sendHeartbeat();
    }

    private fun listenForHeartbeats() {
        // Cancel timers for other states
        sendHeartBeatTimer.cancel();
        electionTimoutTimer.cancel();

        // Only listen for heartbeats if we are a follower
        if (stateMachine.state != NodeState.Follower) {
            logger.warn { "Trying to listen for heartbeats while in state ${stateMachine.state}" }
            return;
        }

        // Reset the heartbeat timeout
        heartBeatTimeoutTimer.resetTimer();
    }
}