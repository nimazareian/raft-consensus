package cs416.lambda.capstone

import io.grpc.Server
import io.grpc.ServerBuilder


class HelloWorldServer(private val port: Int) {
    val server: Server = ServerBuilder
        .forPort(port)
        .addService(HelloWorldService())
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this@HelloWorldServer.stop()
                println("*** server shut down")
            }
        )
    }

    private fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    internal class HelloWorldService : GreeterGrpcKt.GreeterCoroutineImplBase() {
        override suspend fun buyStock(request: BuyRequest) = buyReply {
            println("Buy request received for the amount ${request.amount}")

            // Set purchased field to True and reply
            purchased = false
        }
    }
}

fun main() {
    val server = HelloWorldServer(5000)
    server.start()
    server.blockUntilShutdown()
}
