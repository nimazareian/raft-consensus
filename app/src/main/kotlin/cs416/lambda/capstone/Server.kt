package cs416.lambda.capstone

import cs416.lambda.capstone.config.ClusterConfig
import cs416.lambda.capstone.config.NodeConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder

private val logger = KotlinLogging.logger {}

/**
 * Server that manages communication between nodes and clients
 * and passes messages to Raft Node for processing.
 */
class Server(
    private val config: ClusterConfig,
    private val clientPort: Int,
) {
    private val nodeId = runCatching { config.id.toInt() }
        .getOrElse { throw IllegalArgumentException("invalid ID for node, and must be an Integer within 0 and the max number of nodes") }

    private val serverPort = config.cluster
        .find { n -> n.id == nodeId }?.port ?: DEFAULT_SERVER_GRPC_PORT
        .also {
            logger.warn { "No port given for node, defaulting to $DEFAULT_SERVER_GRPC_PORT" }
        }

    private val configs: List<NodeConfig> = config.cluster
        .filter { n -> n.id != nodeId } // filter this node out

    // Node for handling Raft state machine of this node
    private val node = Node(nodeId, configs)

    // RPC Listener for Raft
    private val raftService: Server = ServerBuilder
        .forPort(serverPort)
        .addService(RaftService(node))
        .build()

    // RPC Listener for trading with client
    private val tradingService: Server = ServerBuilder
        .forPort(clientPort)
        .addService(TradeService(node::handleClientRequest))
        .build()

    fun start() {
        tradingService.start()
        logger.info { "Node $nodeId started, listening on $clientPort for client requests" }

        raftService.start()
        logger.info { "Node $nodeId started, listening on $serverPort for node requests" }

        logger.debug { "Other nodes: $configs" }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                this@Server.stop()
                logger.warn { "Node $nodeId stopped listening on port $serverPort" }
            }
        )
    }

    private fun stop() {
        tradingService.shutdown()
        raftService.shutdown()
        node.close()
    }

    fun blockUntilShutdown() {
        tradingService.awaitTermination()
        raftService.awaitTermination()
    }

    internal class TradeService(val callback: suspend (ClientAction) -> ActionResponse) : TradeGrpcKt.TradeCoroutineImplBase() {
        override suspend fun buyStock(request: BuyRequest): BuyResponse {
            val response = BuyResponse.newBuilder()
            runCatching {
                val actionResult = callback(clientAction {
                    buyRequest = request
                })
                response.setPurchased(actionResult.type == ActionResponse.ActionResult.SUCCESS)
                response.setServerResponse(actionResult)
            }.onFailure {
                logger.debug { "Caught error: ${it.cause} and ${it.printStackTrace()}" }
                response.setPurchased(false)
                response.setServerResponse(actionResponse {
                    type = ActionResponse.ActionResult.FAILED
                })
            }
            return response.build()
        }
    }


    // Receive RPCs from other nodes and forward to Node implementation
    internal class RaftService(private val node: Node) : RaftServiceGrpcKt.RaftServiceCoroutineImplBase() {
        override suspend fun requestVote(request: VoteRequest): VoteResponse {
            return node.handleVoteRequest(request)
        }

        override suspend fun appendEntries(request: AppendEntriesRequest): AppendEntriesResponse {
            return node.handleAppendEntriesRequest(request)
        }
    }
}

