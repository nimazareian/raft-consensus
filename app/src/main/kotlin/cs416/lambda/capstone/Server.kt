package cs416.lambda.capstone

import io.grpc.Server
import io.grpc.ServerBuilder

data class NodeConfig(val id: Int, val host: String, val port: Int)

/**
 * Server that manages communication between nodes and clients
 * and passes messages to Raft Node for processing.
 */
class Server(
    private val nodeId: Int,
    private val port: Int,
    nodeConfigs: Array<NodeConfig>,
) {
    // RPC Sender
    // stub class for node
    val nodes = ArrayList<StubNode>(nodeConfigs.map{n -> StubNode(n.host, n.port)});

    // RPC Listener
    val server: Server = ServerBuilder
        .forPort(port)
        .addService(TradeService())
        .build()

    // Node for handling Raft state machine
    val node = Node(nodeId, nodes)

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

    internal class TradeService : TradeGrpcKt.TradeCoroutineImplBase() {
        override suspend fun buyStock(request: BuyRequest) = buyReply {
            println("Buy request received: $request")

            // Response to client
            purchased = false
        }

    }
}