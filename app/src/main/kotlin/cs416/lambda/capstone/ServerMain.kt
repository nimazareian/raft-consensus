package cs416.lambda.capstone

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import cs416.lambda.capstone.config.ClusterConfig
import cs416.lambda.capstone.config.NodeConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.Path

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {


    val configPath: Path = args
        .toList()
        .windowed(2)
        .map { it.first() to it.last() }
        .find { (key, _) -> key in listOf("-c", "--config") }
        ?.second
        ?.let { Path(it) }
        ?: Path(DEFAULT_CONFIG_PATH)
            .also { logger.warn { "No config flag given, using default [$it]" } }
    logger.info { configPath }
    val config = ConfigLoaderBuilder
        .default()
        .addDefaultParsers()
        .addSource(PropertySource.path(configPath))
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .withReport()
        .build()
        .loadConfigOrThrow<ClusterConfig>()

    val nodeId = runCatching { config.id.toInt() }
        .getOrElse { throw IllegalArgumentException("ID environment variable needs to be set, and must be an Integer within 1024:69420") }
    val serverPort = runCatching { config.port.toInt() }.getOrElse { DEFAULT_SERVER_GRPC_PORT.toInt() }


    val configs: List<NodeConfig> = config.cluster

    println("Node $nodeId started, listening on $serverPort for node requests")
    println("Other nodes: $configs")

    val server = Server(nodeId, serverPort, CLIENT_GRPC_PORT, configs)
    server.start()
    server.blockUntilShutdown()
}
