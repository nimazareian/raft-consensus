package cs416.lambda.capstone

import kotlin.concurrent.fixedRateTimer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async


enum class NodeState {
    FOLLOWER, CANDIDATE, LEADER
}

class Node(
    private val nodeId: Int,
    private val nodes: ArrayList<StubNode>
) {
    private var state = NodeState.FOLLOWER;
    private var currentTerm: ULong = 0u;
    private var currentLeader: UInt? = null;
    private var logs = NodeLogs();

    // How many log entries since the start have we committed
    // TODO: Can we remove this and read it from NodeLog?
    private var commitLength: ULong = 0u;

    /**
     * Fields used for leader election
     */
    private var votesReceived = setOf<UInt>();
    private var votedFor: UInt? = null;

    /**
     * Fields used by the Leader node
     */

    // Map of node ID to the number of log entries we have sent to a follower
    private var sentLength = mapOf<UInt, ULong>();

    // Map of node ID to the number of log entries that a follower has acknowledged
    // to have received
    private var ackedLength = mapOf<UInt, ULong>();

    /**
     * Node constructor used for loading a node from disk
     */
    constructor(
        nodeId: Int,
        currentTerm: ULong,
        votedFor: UInt,
        log: NodeLogs,
        nodes: ArrayList<StubNode>
    ) : this(nodeId, nodes) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logs = log;
    }

    private val sendHeartbeatTimer = fixedRateTimer("heartbeat", initialDelay = 0, period = SEND_HEART_BEAT_TIMER, daemon = true) {
        sendHeartbeat();
    }

    private fun sendHeartbeat() {
        // Only send heartbeats if we are the leader
        if (state == NodeState.LEADER) {
            // TODO: GlobalScope is a "delicate API" should make sure this is right
            nodes.map { n -> GlobalScope.async {
                n.appendEntries(appendEntriesRequest {
                    leaderId = nodeId
                    currentTerm = currentTerm
                    prefixLen = 0
                    prefixTerm = 0
                    commitLength = 0u
                    logs = NodeLogs()
                })
            }
            }
        }
    }
}