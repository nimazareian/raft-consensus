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

    // RPC Listener
    private val tradingService: Server = ServerBuilder
        .forPort(port)
        .addService(TradeService())
        .build()

    private val raftService: Server = ServerBuilder
        .forPort(port)
        .addService(RaftService())
        .build()

    // Node for handling Raft state machine
    private val node = Node(nodeId, nodes)

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

    internal class RaftService : RaftServiceGrpcKt.RaftServiceCoroutineImplBase() {
        override suspend fun requestVote(request: VoteRequest) = voteResponse {
            println("Vote request received: $request")

            // Response to client
            nodeId = this.nodeId
            currentTerm = 0 // TODO
            voteGranted = false
        }

        override suspend fun appendEntries(request: AppendEntriesRequest) = appendEntriesResponse {
            println("Append request received: $request")

            // Response to client
            nodeId = this.nodeId
            currentTerm = 0 // TODO
            logAckLen = 0 // TODO
            isSuccessful = false // TODO
        }
    }
}

