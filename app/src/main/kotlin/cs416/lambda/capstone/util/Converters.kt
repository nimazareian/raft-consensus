package cs416.lambda.capstone.util

import cs416.lambda.capstone.ActionResponse

import cs416.lambda.capstone.BuyRequest as GrpcBuyRequest
import cs416.lambda.capstone.SellRequest as GrpcSellRequest
import cs416.lambda.capstone.json.BuyRequest as JsonBuyRequest
import cs416.lambda.capstone.json.SellRequest as JsonSellRequest
import cs416.lambda.capstone.json.ActionResponse as JsonActionResponse

fun JsonBuyRequest.toGrpcRequest(): Result<GrpcBuyRequest> {
    return try {
        Result.Companion.success(
            GrpcBuyRequest
                .newBuilder()
                .setName(this.name)
                .setStock(this.stock)
                .setAmount(this.amount)
                .build()
        )
    } catch (t: Throwable) {
        Result.Companion.failure(t)
    }
}

fun JsonSellRequest.toGrpcRequest(): Result<GrpcSellRequest> {
    return try {
        Result.Companion.success(
            GrpcSellRequest
                .newBuilder()
                .setName(this.name)
                .setStock(this.stock)
                .setAmount(this.amount)
                .build()
        )
    } catch (t: Throwable) {
        Result.Companion.failure(t)
    }
}

data class NodeConnectionInfo(val address: String, val port: Int)

fun ActionResponse.toJsonResponse() = JsonActionResponse(
    type = JsonActionResponse.ActionResult.valueOf(this.type.name),
    leaderAddress = this.leaderAddress,
    leaderPort = this.leaderPort.toUInt(),
)

fun errorActionResponse(info: NodeConnectionInfo?, mode: JsonActionResponse.ActionResult) = JsonActionResponse(
    type = mode,
    leaderAddress = info?.address ?: "NO LEADER",
    leaderPort = info?.port?.toUInt() ?: 0U
)