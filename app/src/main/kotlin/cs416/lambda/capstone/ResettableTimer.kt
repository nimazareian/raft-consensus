package cs416.lambda.capstone

import java.util.*
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timerTask

class ResettableTimer(private val callback: suspend () -> Int, private val delay: Long)
{
    private var timer = startTimer()

    fun resetTimer() {
        timer.cancel()
        timer = startTimer()
    }

    private fun startTimer(): Timer {
        val newTimer = Timer()
        newTimer.schedule(timerTask {
            runBlocking {
                callback()
            }
        }, delay)
        return newTimer
    }
}
