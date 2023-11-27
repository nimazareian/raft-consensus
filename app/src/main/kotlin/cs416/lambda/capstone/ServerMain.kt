package cs416.lambda.capstone

fun main(args: Array<String>) {
    // TODO: Added for initial testing - Remove

    val env = System.getenv()
    val nodeId = env.getOrDefault("ID", "50").toInt()
    val configs: List<NodeConfig> = env.getOrDefault("NODES", "50:localhost:4040")
        .split(",")
        .map {
            val uri = it.split(":")
            if (uri.size != 3) {
                throw IllegalArgumentException("Invalid NODES environment variable, each comma separated node config " +
                        "should be in the format of <id>:<host>:<port>. Got: $it")
            }
            NodeConfig(uri[0].toInt(), uri[1], uri[2].toInt())
        }.toList()

    val server = Server(nodeId, GRPC_PORT, configs)
    server.start()
    server.blockUntilShutdown()
}
