package cs416.lambda.capstone.util

import cs416.lambda.capstone.AppendEntriesRequest
import cs416.lambda.capstone.LogEntry
import cs416.lambda.capstone.VoteRequest
import org.jetbrains.annotations.ApiStatus

fun AppendEntriesRequest.getShortInfo(): String {
    return "current_term=${this.currentTerm}, leader_id=${this.leaderId}"
}

fun AppendEntriesRequest.getFullInfo(): String {
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


fun VoteRequest.getShortInfo(): String {
    return "candidate_id=${this.candidateId}, current_term=${this.currentTerm}"
}

fun VoteRequest.getFullInfo(): String {
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