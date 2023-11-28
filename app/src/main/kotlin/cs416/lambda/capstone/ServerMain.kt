package cs416.lambda.capstone

fun main() {
    val env = System.getenv()

    val nodeId = env["ID"]?.toInt() ?: throw IllegalArgumentException("ID environment variable needs to be set.")
    val serverPort = env.getOrDefault("PORT", DEFAULT_SERVER_GRPC_PORT).toInt()

    val configs: List<NodeConfig>? = env["NODES"]
        ?.split(",")
        ?.map {
            val uri = it.split(":")
            if (uri.size != 3) {
                throw IllegalArgumentException("Invalid NODES environment variable, each comma separated node config " +
                        "should be in the format of <id>:<host>:<port>. Got: $it")
            }
            NodeConfig(uri[0].toInt(), uri[1], uri[2].toInt())
        }?.toList()

    if (configs.isNullOrEmpty()) {
        throw IllegalArgumentException("NODES environment variable needs to have atleast one node config.")
    }

    println("Node $nodeId started, listening on $serverPort for node requests")
    println("Other nodes: $configs")

    val server = Server(nodeId, serverPort, CLIENT_GRPC_PORT, configs)
    server.start()
    server.blockUntilShutdown()
}
