package cs416.lambda.capstone

class NodeLogs {
    @Volatile
    var entries: MutableList<LogEntry> = mutableListOf();
        private set

    private var commitIndex: Int? = null;

    // TODO: Implement
    fun append(log: LogEntry) {

    }

    // TODO: Implement
    fun commit(index: Int): Boolean {
        commitIndex = index;
        return false;
    }


}
