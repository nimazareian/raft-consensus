package cs416.lambda.capstone

import java.util.*
import kotlin.concurrent.timerTask

class ResettableTimer(
    private val callback: () -> Unit,
    private val delay: Long,
    startNow: Boolean,
    private val initialDelay: Long = 0L
) {
    private var timer = if (startNow) startTimer() else null

    fun resetTimer() {
        timer?.cancel()
        timer = restartTimer()
    }

    fun cancel() {
        timer?.cancel()
    }

    private fun restartTimer(): Timer {
        val newTimer = Timer()
        newTimer.schedule(timerTask {
            callback()
        }, delay)
        return newTimer
    }

    private fun startTimer(): Timer {
        val newTimer = Timer()
        newTimer.schedule(timerTask {
            callback()
        }, initialDelay)
        return newTimer
    }
}
