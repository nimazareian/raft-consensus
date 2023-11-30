package cs416.lambda.capstone.util

import cs416.lambda.capstone.AppendEntriesRequest
import cs416.lambda.capstone.AppendEntriesResponse
import cs416.lambda.capstone.BuyRequest
import cs416.lambda.capstone.ClientAction
import cs416.lambda.capstone.LogEntry
import cs416.lambda.capstone.SellRequest
import cs416.lambda.capstone.VoteRequest
import cs416.lambda.capstone.VoteResponse
import org.jetbrains.annotations.ApiStatus

/**
 * AppendEntriesRequest toString methods
 */

private fun AppendEntriesRequest.getShortString(): String {
    return "current_term=${this.currentTerm}, leader_id=${this.leaderId}"
}

private fun AppendEntriesRequest.getFullString(): String {
    return this.getShortString().plus(
        " prev_log_index=${this.prevLogIndex}, prev_log_term=${this.prevLogTerm}, leader_commit_index=${this.leaderCommitIndex} entries_count=${this.entriesCount}"
    )
}

@ApiStatus.Experimental()
fun AppendEntriesRequest.asFullDebugString(): String {
    return "AppendEntriesRequest(${this.getFullString()} entries=${this.entriesList})"
}

fun AppendEntriesRequest.asFullString(): String {
    return "AppendEntriesRequest(${this.getFullString()})"
}

fun AppendEntriesRequest.asShortString(): String {
    return "AppendEntriesRequest(${getShortString()})"
}

// ====================================================================================================

/**
 * AppendEntriesResponse toString methods
 */

private fun AppendEntriesResponse.getString(): String {
    return "node_id=${this.nodeId}, current_term=${this.currentTerm}, log_ack_len=${this.logAckIndex}, is_successful=${this.isSuccessful}"
}

fun AppendEntriesResponse.asString(): String {
    return "AppendEntriesResponse(${this.getString()})"
}

// ====================================================================================================

/**
 * VoteRequest toString methods
 */

private fun VoteRequest.getShortString(): String {
    return "candidate_id=${this.candidateId}, current_term=${this.currentTerm}"
}

private fun VoteRequest.getFullString(): String {
    return this.getShortString().plus(
        " last_log_index=${this.lastLogIndex}, last_log_term=${this.lastLogTerm}"
    )
}

fun VoteRequest.asFullString(): String {
    return "VoteRequest(${this.getFullString()})"
}

fun VoteRequest.asShortString(): String {
    return "VoteRequest(${getShortString()})"
}

// ====================================================================================================

/**
 * VoteResponse toString methods
 */

private fun VoteResponse.getShortString(): String {
    return "candidate_id=${this.nodeId}, current_term=${this.currentTerm}, vote_granted=${this.voteGranted}"
}

fun VoteResponse.asString(): String {
    return "VoteRequest(${getShortString()})"
}


// ====================================================================================================

/**
 * LogEntry toString methods
 */

fun LogEntry.asString(): String {
    return "LogEntry(term=${this.term}, action=${this.action.asString()})"
}

fun BuyRequest.asString(): String {
    return "BuyRequest(stock=${this.stock}, amount=${this.amount})"
}

fun SellRequest.asString(): String {
    return "SellRequest(stock=${this.stock}, amount=${this.amount})"
}

fun ClientAction.asString(): String {
    val requestString = if (this.hasBuyRequest()) this.buyRequest.asString() else this.sellRequest.asString()
    return "ClientAction($requestString)"
}

// ====================================================================================================

fun MutableList<LogEntry>.removeRange(range: IntRange) {
    range.forEach { idx ->
        this.removeAt(idx)
    }
}