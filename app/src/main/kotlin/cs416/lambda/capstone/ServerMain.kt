package cs416.lambda.capstone

fun main(args: Array<String>) {
    // TODO: Added for initial testing - Remove

    if (!args.contains("--nodeId") || args.size < 2) {
        println("Please provide a node id with --nodeId <id>")
        return
    }

    val nodeId = args[args.indexOf("--nodeId") + 1].toUInt();
    val server = Server(GRPC_PORT, nodeId)
    server.start()
    server.blockUntilShutdown()
}
