package cs416.lambda.capstone

import io.grpc.Server
import io.grpc.ServerBuilder

/**
 * Server that manages communication between nodes and clients
 * and passes messages to Raft Node for processing.
 */
class Server(
    private val port: Int,
    private val nodeId: UInt,
) {
    val server: Server = ServerBuilder
        .forPort(port)
        .addService(HelloWorldService())
        .build()
    val node = Node(nodeId)

    fun start() {
        server.start()
        println("Server $nodeId started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                this@Server.stop()
                println("Server $nodeId stopped listening on port $port")
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