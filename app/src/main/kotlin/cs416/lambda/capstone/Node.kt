package cs416.lambda.capstone

enum class NodeState {
    FOLLOWER, CANDIDATE, LEADER
}

class Node(
    private val nodeId: Int,
) {
    private var state = NodeState.FOLLOWER;
    private var currentTerm = 0;
    private var currentLeader: Int? = null;
    private var logs = NodeLogs();
    // How many log entries since the start have we committed
    // TODO: Can we remove this and read it from NodeLog?
    private var commitLength = 0;

    /**
     * Fields used for leader election
     */
    private var votesReceived = setOf<Int>();
    private var votedFor: Int? = null;

    /**
     * Fields used by the Leader node
     */

    // Map of node ID to the number of log entries we have sent to a follower
    private var sentLength = mapOf<Int, Int>();

    // Map of node ID to the number of log entries that a follower has acknowledged
    // to have received
    private var ackedLength = mapOf<Int, Int>();

    /**
     * Node constructor used for loading a node from disk
     */
    constructor(nodeId: Int, currentTerm: Int, votedFor: Int, log: NodeLogs) : this(nodeId) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logs = log;
    }
}