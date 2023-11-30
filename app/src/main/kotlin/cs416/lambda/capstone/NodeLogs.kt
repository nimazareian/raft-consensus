package cs416.lambda.capstone

import cs416.lambda.capstone.util.removeRange
import kotlin.math.min

class NodeLogs {
    @Volatile
    private var entries: MutableList<LogEntry> = mutableListOf()

    fun isNotEmpty(): Boolean = entries.isNotEmpty()
    fun lastIndex() = entries.lastIndex // returns -1 on empty
    fun lastTerm(): Long? = entries.lastOrNull()?.term

    fun indexInRange(idx: Int): Boolean = 0 <= idx && idx < entries.size
    fun starting(index: Int): List<LogEntry> {
        return entries.filterIndexed { i, _ -> i >= index }
    }

    var commitIndex: Int = -1
        private set

    /**
     * Appends log to end of entries list.
     */
    fun append(log: LogEntry): Int {
        val index = entries.size
        entries.add(log)
//        return the index of log entry that was inserted to the caller.
//        The caller may want to check the state for the index to update before proceeding
//        Eg.
//        while (index > state.log.commitIndex -> refering to cluster state) {
//            delay(50)
//        }

        return index
    }

    operator fun get(prevLogIndex: Int) = entries.getOrNull(prevLogIndex)


    /*
     Sets log entry. Not that this may overwrite old entries.
     */
    operator fun set(index: Int, entry: LogEntry) {
        if (index == entries.size) {
            entries.add(entry)
        } else {
            entries[index] = entry
        }
    }

    fun commit(index: Int): Boolean {
        val idx = min(lastIndex(), index)
        commitIndex = idx
        return true
    }

    fun prune(startIndex: Int) {
        entries.removeRange(IntRange(startIndex, entries.size))
    }

    /**
     * Checks if the given entry's term matches the log entry's term at the given index
     */
    fun checkIndexTerm(index: Int, term: Long) : Boolean {
        return isNotEmpty() && entries[index].term != term
    }
}
