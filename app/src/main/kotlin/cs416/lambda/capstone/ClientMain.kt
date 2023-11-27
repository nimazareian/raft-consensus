package cs416.lambda.capstone

suspend fun main() {
    val client = Client(CLIENT_GRPC_PORT)
    client.buyStock("GME", 100)
    client.close()
}
