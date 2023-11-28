package cs416.lambda.capstone

import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

class Client(port: Int) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
    private val stub: TradeGrpcKt.TradeCoroutineStub = TradeGrpcKt.TradeCoroutineStub(channel)

    suspend fun buyStock(stockTicker: String, amountToBuy: Int) {
        val request = buyRequest {
            stock = stockTicker
            amount = amountToBuy
        }
        println("Sending buyStock request: $request")
        val response = stub.buyStock(request)
        println("Received response: $response")
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
