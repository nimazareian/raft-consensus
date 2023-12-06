package cs416.lambda.capstone

import cs416.lambda.capstone.ActionResponse
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

    // TODO better error bubbling
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
                                ?: throw NotFoundException("User not found")

                            val stockPrice = (10..100).random()

                            if (stockPrice * request.amount <= user.balance) {
                                // up[date user balance
                                logger.info { "Propagating to  client request nodes" }
                                val action = ClientAction.newBuilder().setBuyRequest(it).build()
                                val resp = node.handleClientRequest(action)
                                if (resp.type == ActionResponse.ActionResult.SUCCESS) {
                                    logger.info { "Updating user portfolio" }
                                    user.portfolio.merge(request.stock, request.amount, Int::plus)
                                    user.balance -= request.amount * stockPrice
                                    JsonBuyResponse(
                                        purchased = true,
                                        serverResponse = resp.toJsonResponse()
                                    )
                                } else {
                                    errorBuyResponse()
                                }

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
                            val user = portfolios[request.name] ?: throw NotFoundException("User portfolio not found")
                            val stockPrice = user.portfolio[request.stock] ?: throw NotFoundException("Stock not found")
                            if (request.amount <= stockPrice) {
                                val action = ClientAction.newBuilder().setSellRequest(it).build()
                                val resp = node.handleClientRequest(action)
                                if (resp.type == GrpcActionResponse.ActionResult.SUCCESS) {
                                    user.portfolio.merge(request.stock, request.amount, Int::minus)
                                    user.balance += request.amount * stockPrice
                                    JsonSellResponse(
                                        sold = resp.type == GrpcActionResponse.ActionResult.SUCCESS,
                                        serverResponse = resp.toJsonResponse()
                                    )
                                } else {
                                    errorSellResponse()
                                }
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