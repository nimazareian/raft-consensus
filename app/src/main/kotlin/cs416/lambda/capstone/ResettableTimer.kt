package cs416.lambda.capstone

import java.util.*
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timerTask

class ResettableTimer(private val callback: () -> Unit, private val delay: Long, startNow: Boolean)
{
    private var timer = if (startNow) startTimer() else null

    fun resetTimer() {
        timer?.cancel()
        timer = startTimer()
    }

    fun cancel() {
        timer?.cancel()
    }

    private fun startTimer(): Timer {
        val newTimer = Timer()
        newTimer.schedule(timerTask {
            callback()
        }, delay)
        return newTimer
    }
}
