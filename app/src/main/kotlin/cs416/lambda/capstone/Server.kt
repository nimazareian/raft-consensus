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
    private val nodes = ArrayList<StubNode>(nodeConfigs.map{n -> StubNode(n.host, n.port)});

    // Node for handling Raft state machine
    private val node = Node(nodeId, nodes)

    // RPC Listener
    private val tradingService: Server = ServerBuilder
        .forPort(port)
        .addService(TradeService())
        .build()

    private val raftService: Server = ServerBuilder
        .forPort(port)
        .addService(RaftService(node))
        .build()

    fun start() {
        tradingService.start()
        println("Node $nodeId started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                this@Server.stop()
                println("Node $nodeId stopped listening on port $port")
            }
        )
    }

    private fun stop() {
        tradingService.shutdown()
        raftService.shutdown()
    }

    fun blockUntilShutdown() {
        tradingService.awaitTermination()
        raftService.awaitTermination()
    }

    internal class TradeService : TradeGrpcKt.TradeCoroutineImplBase() {
        override suspend fun buyStock(request: BuyRequest) = buyReply {
            println("Buy request received: $request")

            // Response to client
            purchased = false
        }
    }

    // Receive RPCs from other nodes and forward to Node implementation
    internal class RaftService(private val node: Node) : RaftServiceGrpcKt.RaftServiceCoroutineImplBase() {
        override suspend fun requestVote(request: VoteRequest): VoteResponse {
            return node.requestVote(request)
        }

        override suspend fun appendEntries(request: AppendEntriesRequest): AppendEntriesResponse {
            return node.appendEntries(request)
        }
    }
}

