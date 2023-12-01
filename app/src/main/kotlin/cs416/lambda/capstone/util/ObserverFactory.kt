package cs416.lambda.capstone.util

import com.google.protobuf.GeneratedMessageV3
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}


class ObserverFactory {

    companion object {
        fun <T : GeneratedMessageV3> buildProtoObserver(mutex: Mutex, callbackFn: (T) -> Unit) =
            object : StreamObserver<T> {
                override fun onNext(response: T) {
                    runBlocking {
                        mutex.withLock {
                            callbackFn(response)
                        }
                    }
                }

                override fun onError(t: Throwable) {
                    logger.error { "Error occurred proccessing response $t" }
                }

                override fun onCompleted() {
                    logger.debug { "Finished processing response" }
                }
            }
    }
}