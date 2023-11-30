package cs416.lambda.capstone.util

import cs416.lambda.capstone.AppendEntriesRequest
import cs416.lambda.capstone.AppendEntriesResponse
import cs416.lambda.capstone.BuyRequest
import cs416.lambda.capstone.LogEntry
import cs416.lambda.capstone.SellRequest
import cs416.lambda.capstone.VoteRequest
import org.jetbrains.annotations.ApiStatus

/**
 * AppendEntriesRequest toString methods
 */

private fun AppendEntriesRequest.getShortInfo(): String {
    return "current_term=${this.currentTerm}, leader_id=${this.leaderId}"
}

private fun AppendEntriesRequest.getFullInfo(): String {
    return this.getShortInfo().plus(
        " prev_log_index=${this.prevLogIndex}, prev_log_term=${this.prevLogTerm}, leader_commit_index=${this.leaderCommitIndex} entries_count=${this.entriesCount}"
    )
}

@ApiStatus.Experimental()
fun AppendEntriesRequest.asFullDebugString(): String {
    return "AppendEntriesRequest(${this.getFullInfo()} entries=${this.entriesList})"
}

fun AppendEntriesRequest.asFullInfoString(): String {
    return "AppendEntriesRequest(${this.getFullInfo()})"
}

fun AppendEntriesRequest.asShortInfoString(): String {
    return "AppendEntriesRequest(${getShortInfo()})"
}

// ====================================================================================================

/**
 * AppendEntriesResponse toString methods
 */

private fun AppendEntriesResponse.getInfoString(): String {
    return "node_id=${this.nodeId}, current_term=${this.currentTerm}, log_ack_len=${this.logAckLen}, is_successful=${this.isSuccessful}"
}

fun AppendEntriesResponse.asInfoString(): String {
    return "AppendEntriesResponse(${this.getInfoString()})"
}

// ====================================================================================================

/**
 * VoteRequest toString methods
 */

private fun VoteRequest.getShortInfo(): String {
    return "candidate_id=${this.candidateId}, current_term=${this.currentTerm}"
}

private fun VoteRequest.getFullInfo(): String {
    return this.getShortInfo().plus(
        " last_log_index=${this.lastLogIndex}, last_log_term=${this.lastLogTerm}"
    )
}

fun VoteRequest.asFullInfoString(): String {
    return "VoteRequest(${this.getFullInfo()})"
}

fun VoteRequest.asShortInfoString(): String {
    return "VoteRequest(${getShortInfo()})"
}


fun MutableList<LogEntry>.removeRange(range: IntRange) {
    range.forEach { idx ->
        this.removeAt(idx)
    }
}

// ====================================================================================================

/**
 * VoteRequest toString methods
 */


fun LogEntry.asString(): String {
    val action = this.action
    val requestString = if (action.hasBuyRequest()) action.buyRequest.asString() else action.sellRequest.asString()
    return "LogEntry(term=${this.term}, request=${requestString})"
}


fun BuyRequest.asString(): String {
    return "LogEntry(term=${this.stock}, action=${this.amount})"
}

fun SellRequest.asString(): String {
    return "LogEntry(term=${this.stock}, action=${this.amount})"
}