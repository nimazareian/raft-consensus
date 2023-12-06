package cs416.lambda.capstone

import cs416.lambda.capstone.util.errorActionResponse
import cs416.lambda.capstone.util.toGrpcRequest
import cs416.lambda.capstone.util.toJsonResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import kotlinx.serialization.Serializable
import cs416.lambda.capstone.json.BuyRequest as JsonBuyRequest
import cs416.lambda.capstone.json.SellRequest as JsonSellRequest
import cs416.lambda.capstone.json.BuyResponse as JsonBuyResponse
import cs416.lambda.capstone.json.ActionResponse as JsonActionResponse
import cs416.lambda.capstone.ActionResponse as GrpcActionResponse
import cs416.lambda.capstone.json.SellResponse as JsonSellResponse

typealias Portfolio = MutableMap<String, Int>

typealias User = String

private val logger = KotlinLogging.logger {}


class TradeServiceImpl(private val node: Node) {
    private val portfolios = mutableMapOf<User, UserProfile>()

    private fun errorBuyResponse(mode: JsonActionResponse.ActionResult = JsonActionResponse.ActionResult.FAILED) =
        JsonBuyResponse(purchased = false, errorActionResponse(node.getLeaderInfo(), mode))

    private fun errorSellResponse(mode: JsonActionResponse.ActionResult = JsonActionResponse.ActionResult.FAILED) =
        JsonSellResponse(sold = false, errorActionResponse(node.getLeaderInfo(), mode))


    suspend fun buyStock(request: JsonBuyRequest): JsonBuyResponse {
        logger.info { "Buy request received: $request" }
        // https://query2.finance.yahoo.com/v8/finance/chart/GME

        request.toGrpcRequest()
            .onFailure {
                logger.error {
                    it.message
                }
            }.getOrNull().let {
                return when (it) {
                    null -> {
                        logger.error { "Couldn't parse buy request: $request" }
                        errorBuyResponse()
                    }
                    else -> {
                        if (portfolios.containsKey(request.name)) {
                            val user = portfolios[request.name]
                                ?: return errorBuyResponse()
                            val stockPrice = (10..100).random()

                            if (stockPrice * request.amount <= user.balance) {
                                val action = ClientAction.newBuilder().setBuyRequest(it).build()
                                val resp = node.handleClientRequest(action)
                                JsonBuyResponse(
                                    purchased = resp.type == GrpcActionResponse.ActionResult.SUCCESS,
                                    serverResponse = resp.toJsonResponse()
                                )
                            } else {
                                logger.warn { "Not enough balance in account for user $user" }
                                errorBuyResponse()
                            }
                        } else {
                            throw NotFoundException("User not found")
                        }
                    }
                }
            }
    }

    suspend fun sellStock(request: JsonSellRequest): JsonSellResponse {
        logger.info { "Sell request received: $request" }
        request.toGrpcRequest()
            .onFailure {
                logger.error {
                    it.message
                }
            }.getOrNull().let {
                return when (it) {
                    null -> {
                        errorSellResponse(JsonActionResponse.ActionResult.FAILED)
                    }
                    else -> {
                        if (portfolios.containsKey(request.name)) {
                            val user = portfolios[request.name] ?: return errorSellResponse()
                            val stock = user.portfolio[request.stock] ?: throw NotFoundException("Stock not found")
                            if (request.amount <= stock) {
                                val action = ClientAction.newBuilder().setSellRequest(it).build()
                                val resp = node.handleClientRequest(action)
                                JsonSellResponse(
                                    sold = resp.type == GrpcActionResponse.ActionResult.SUCCESS,
                                    serverResponse = resp.toJsonResponse()
                                )
                            } else {
                                logger.warn { "Not enough assets in portfolio to sell in user account $user" }
                                errorSellResponse()
                            }
                        } else {
                            throw NotFoundException("User not found")
                        }
                    }
                }
            }
    }

    fun getStocks(request: GetStocksRequest): GetStocksReply {
        logger.info { "Get stock price request received: $request" }
        logger.info { "Returning successful reply" }
        return GetStocksReply(marketPrice = (10..100).random())

    }

    fun getPortfolio(request: GetPortfolioRequest): GetPortfolioReply {
        logger.info { "Get stock price request received: $request" }
        val user = portfolios[request.userName] ?: throw NotFoundException("User not found")
        logger.info { "Returning successful reply" }
        return GetPortfolioReply(UserProfile(user.balance, user.portfolio))

    }

    fun deposit(request: DepositStocksRequest): DepositStocksReply {
        logger.info { "Deposit request received: $request" }
        if (portfolios.containsKey(request.name)) {
            logger.info { "Deposit into existing account" }
            portfolios[request.name]?.balance?.plus(request.amount)
        } else {
            logger.info { "Deposit into new account" }
            portfolios[request.name] = UserProfile(balance = request.amount, mutableMapOf())
        }
        logger.info { "Returning successful reply" }
        return DepositStocksReply(success = true)
    }

    @Serializable
    data class UserProfile(var balance: Int, val portfolio: Portfolio)
    @Serializable
    data class DepositStocksRequest(val name: String, val amount: Int)
    @Serializable
    data class DepositStocksReply(val success: Boolean)
    @Serializable
    data class GetStocksRequest(val stockName: String)
    @Serializable
    data class GetStocksReply(val marketPrice: Int)
    @Serializable
    data class GetPortfolioRequest(val userName: String)
    @Serializable
    data class GetPortfolioReply(val portfolio: UserProfile)
}