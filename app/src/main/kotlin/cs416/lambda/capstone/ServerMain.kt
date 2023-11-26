package cs416.lambda.capstone

fun main(args: Array<String>) {
    // TODO: Added for initial testing - Remove

    if (!args.contains("--nodeId") || args.size < 2) {
        println("Please provide a node id with --nodeId <id>")
        return
    }

    val configs = arrayOf(NodeConfig(1, "abc", 4000))

    val nodeId = args[args.indexOf("--nodeId") + 1].toInt();
    val server = Server(nodeId, GRPC_PORT, configs)
    server.start()
    server.blockUntilShutdown()
}
