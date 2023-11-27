package cs416.lambda.capstone

class NodeLogs {
    @Volatile
    private var entries: MutableList<LogEntry> = mutableListOf()
        get() = field

    var commitIndex: Int = -1

    // TODO: Implement
    fun append(log: LogEntry) {

    }

    // TODO: Implement
    fun commit(index: Int): Boolean {
        commitIndex = index;
        return false;
    }


}
