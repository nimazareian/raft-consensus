package cs416.lambda.capstone

suspend fun main() {
    val client = Client(GRPC_PORT)
    client.buyStock("GME", 100)
    client.close()
}
