package cs416.lambda.capstone

import cs416.lambda.capstone.config.ClusterConfig
import cs416.lambda.capstone.config.NodeConfig
import cs416.lambda.capstone.util.NodeConnectionInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.ServerBuilder
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import cs416.lambda.capstone.BuyRequest as GrpcBuyRequest
import cs416.lambda.capstone.json.BuyRequest as JsonBuyRequest
import cs416.lambda.capstone.json.SellRequest as JsonSellRequest

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
        .getOrElse {
            throw IllegalArgumentException("invalid ID for node, and must be an Integer within 0 and the max number of nodes")
        }

    private val serverPort = config.cluster
        .find { n -> n.id == nodeId }?.port ?: DEFAULT_SERVER_GRPC_PORT
        .also {
            logger.warn { "No port given for node, defaulting to $DEFAULT_SERVER_GRPC_PORT" }
        }
    private val thisNode: NodeConfig = config.cluster
        .find { n -> n.id != nodeId }
        ?: throw IllegalArgumentException("Node Configuration missing for this id $nodeId")

    // filter this node out
    private val configs: List<NodeConfig> = config.cluster
        .filter { n -> n.id != nodeId }

    // Node for handling Raft state machine of this node
    private val node = Node(nodeId, NodeConnectionInfo(thisNode.address, thisNode.port), configs)

    // RPC Listener for Raft
    private val raftService: Server = ServerBuilder
        .forPort(serverPort)
        .addService(RaftService(node))
        .build()

    // Server for handling incoming client http requests
    private val tradingService = embeddedServer(Netty, port = clientPort) {}


    fun start() {
        addShutdownHooks()

        setupServices()

        startServices()
    }

    private fun startServices() {
        raftService.start()
        node.start()
        tradingService.start(wait = true).also {
            logger.info { "Exchange server started, listening on $clientPort for client requests" }
        }
    }

    private fun addShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                this@Server.stop()
                logger.warn { "Node $nodeId stopped listening on port $serverPort" }
            }
        )
    }

    private fun setupServices() {
        // Service class for handling client requests
        val tradeServiceImpl = TradeServiceImpl(node)
        // handle raw json
        tradingService.application.install(ContentNegotiation) {
            json()
        }
        // configure CORS
        tradingService.application.install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Accept)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            allowNonSimpleContentTypes = true
            allowCredentials = true
            allowSameOrigin = true
        }
        // Request routing
        tradingService.application.routing {
            post("/buy") {
                call.receive<JsonBuyRequest>().let {
                    call.respond(tradeServiceImpl.buyStock(it))
                }
            }
            post("/sell") {
                call.receive<JsonSellRequest>().let {
                    call.respond(tradeServiceImpl.sellStock(it))
                }
            }
            post("/portfolio") {
                call.receive<TradeServiceImpl.GetStocksRequest>().let {
                    call.respond(tradeServiceImpl.getStocks(it))
                }
            }
            post("/deposit") {
                call.receive<TradeServiceImpl.DepositStocksRequest>().let {
                    call.respond(tradeServiceImpl.deposit(it))
                }
            }
        }
    }

    private fun stop() {
        tradingService.stop()
        raftService.shutdown()
        node.close()
    }

    fun blockUntilShutdown() {
        raftService.awaitTermination()
    }

    internal class TradeService(val callback: suspend (ClientAction) -> ActionResponse) :
        TradeGrpcKt.TradeCoroutineImplBase() {
        override suspend fun buyStock(request: GrpcBuyRequest): BuyResponse {
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

