package cs416.lambda.capstone

import org.checkerframework.checker.guieffect.qual.UI

enum class NodeState {
    FOLLOWER, CANDIDATE, LEADER
}

class Node(
    private val nodeId: UInt,
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
    constructor(nodeId: UInt, currentTerm: ULong, votedFor: UInt, log: NodeLogs) : this(nodeId) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logs = log;
    }
}