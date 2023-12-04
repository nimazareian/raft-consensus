package cs416.lambda.capstone

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import cs416.lambda.capstone.config.ClusterConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.Path

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {

    print(BANNER)

    println()

    val configPath = loadConfig(args)


    val config = ConfigLoaderBuilder
        .default()
        .addDefaultParsers()
        .addSource(PropertySource.path(configPath))
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .withReport()
        .build()
        .loadConfigOrThrow<ClusterConfig>()

    val server = Server(config, CLIENT_GRPC_PORT)
    server.start()
    server.blockUntilShutdown()
}

fun loadConfig(args: Array<String>): Path {
    return args
        .toList()
        .windowed(2)
        .map { it.first() to it.last() }
        .find { (key, _) -> key in listOf("-c", "--config") }
        ?.second
        ?.let { Path(it) }
        ?: Path(DEFAULT_CONFIG_PATH)
            .also { logger.warn { "No config flag given, using default [$it]" } }

}
