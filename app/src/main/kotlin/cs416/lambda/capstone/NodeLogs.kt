package cs416.lambda.capstone

class NodeLogs {
    @Volatile
    private var log: MutableList<LogEntry> = mutableListOf();
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
