package cs416.lambda.capstone

import com.tinder.StateMachine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration


sealed class NodeState {
    object Follower : NodeState()
    object Candidate : NodeState()
    object Leader : NodeState()
}

sealed class Event {
    object LeaderFailureSuspected : Event()
    object ReceivesQuorum : Event()
    object NewTermDiscovered : Event()
    object ElectionTimeout : Event()
}

sealed class SideEffect {
    object SendRequestVote : SideEffect()
    object StartSendingHeartbeats : SideEffect()

}

class Node(
    private val nodeId: Int,
    private val nodes: ArrayList<StubNode>
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
                transitionTo(NodeState.Candidate, SideEffect.SendRequestVote)
            }
        }

        state<NodeState.Candidate> {
            on<Event.ReceivesQuorum> {
                transitionTo(NodeState.Leader, SideEffect.StartSendingHeartbeats)
            }
            on<Event.NewTermDiscovered> {
                transitionTo(NodeState.Follower)
            }
            on<Event.ElectionTimeout> {
                transitionTo(NodeState.Candidate, SideEffect.SendRequestVote)
            }
        }

        state<NodeState.Leader> {
            on<Event.NewTermDiscovered> {
                transitionTo(NodeState.Follower)
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            when (validTransition.sideEffect) {
                SideEffect.SendRequestVote -> print("Sending request vote side effect")
                SideEffect.StartSendingHeartbeats -> print("Sending heartbeat side effect")
                null -> print("No side effect")
            }
        }
    }

    /**
     * Heartbeat setup
     */
    val scope = CoroutineScope(Dispatchers.Default)
    val test = tickerFlow(SEND_HEART_BEAT_TIMER).onEach {
        sendHeartbeat()
    }.launchIn(scope)

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

    private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }
    private suspend fun sendHeartbeat() {
        // Only send heartbeats if we are the leader
        if (stateMachine.state != NodeState.Leader) {
            return;
        }

        // Asynchronously send heartbeats to all nodes and update the tracked state
        nodes.map { n -> coroutineScope {
            print("Sending heartbeat to node ${n.host}:${n.port}")
            launch {
                val response = n.appendEntries(appendEntriesRequest {
                    leaderId = nodeId
                    currentTerm = currentTerm
                    prevLogIndex = 0
                    commitLength = 0
                    commitLength = 0
                    logs = NodeLogs()
                })

                print("Received response back from node ${n.host}:${n.port}")
                mutex.withLock {
                    if (response.isSuccessful) {
                        ackedLength[response.nodeId] = response.logAckLen;
                    }

                    if (response.currentTerm != currentTerm)
                    {
                        stateMachine.transition(Event.NewTermDiscovered);
                    }
                }
            }
        }
        }
    }

    private fun becomeCandidate() {
        if (stateMachine.state != NodeState.Follower) {
            print("Node $nodeId can't become candidate as it is already a candidate or leader")
        }

        currentTerm += 1;
        votedFor = nodeId;
        votesReceived = mutableSetOf<Int>();
    }
}