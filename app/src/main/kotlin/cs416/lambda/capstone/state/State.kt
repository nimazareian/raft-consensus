package cs416.lambda.capstone.state

import com.tinder.StateMachine
import io.github.oshai.kotlinlogging.KotlinLogging


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

fun initializeNodeState(
    startElection: () -> Unit,
    startSendingHeartBeats: () -> Unit,
    listenForHeartbeats: () -> Unit
) = StateMachine.create<NodeState, Event, SideEffect> {
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
