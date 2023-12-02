import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.google.protobuf.gradle.*
org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.js

val grpcVersion = "1.57.2"
val grpcKotlinVersion = "1.4.0"
val protobufVersion = "3.24.1"

plugins {
    // For building a jar
    id("com.github.johnrengelman.shadow") version "8.1.1"

    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.9.10"

    id("com.google.protobuf") version "0.8.19"

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

application {
    // Configure which main class should be run. This should be added to your gradle
    // run configuration. E.g. `run -Plaunch=Server` or `run -Plaunch=Client`
    if (hasProperty("launch")) {
        mainClass.set("cs416.lambda.capstone.${property("launch")}MainKt")
    } else {
        // Default to launching Server
        mainClass.set("cs416.lambda.capstone.ServerMainKt")
    }
}


repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // This dependency is used by the application.
    implementation("com.google.guava:guava:30.1.1-jre")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    // To use coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // To define state machines
    implementation("com.tinder.statemachine:statemachine:0.2.0")

    // For config loading
    implementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
    implementation("com.sksamuel.hoplite:hoplite-json:2.7.5")

    // Logger
    // Can enable DEBUG level logging by setting the environment variable LOG_LEVEL=DEBUG
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-core:1.4.11")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // Protobuf and gRPC
    implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
    implementation("com.google.protobuf:protobuf-kotlin:${protobufVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    implementation("io.grpc:grpc-kotlin-stub:${grpcKotlinVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")

    //yahoo finance
    implementation("com.yahoofinance-api:YahooFinanceAPI:3.5.0")


    runtimeOnly("io.grpc:grpc-netty:${grpcVersion}")

    if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        implementation("javax.annotation:javax.annotation-api:1.3.1")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protobufVersion}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${grpcKotlinVersion}:jdk8@jar"
        }
        id("grpc-web") {
            path = "/usr/local/bin/protoc-gen-grpc-web"
        }
    }
    generateProtoTasks {
        all().forEach {
            /**
             * Inspired from https://github.com/grpc/grpc-kotlin/blob/master/compiler/README.md
             */
            it.builtins {
                id("kotlin")
                id("js") {
                    option("import_style=commonjs")
                }
            }
            it.plugins {
                id("grpc")
                id("grpckt")
                id("grpc-web") {
                    option("import_style=commonjs")
                    option("mode=grpcwebtext")
                }
            }
        }
    }
}

// required for correct build execution tasks order
tasks.named("startScripts").configure { dependsOn("shadowJar") }
tasks.named("startShadowScripts").configure { dependsOn("jar") }

// packages a fat jar with all dependencies included.
tasks.withType<ShadowJar> {
    manifest.attributes["Main-Class"] = application.mainClass.get()
    archiveFileName.set("app.jar")
    mergeServiceFiles()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "${JavaVersion.VERSION_17}"
    }
}

tasks.withType<JavaCompile> {
    targetCompatibility = "${JavaVersion.VERSION_17}"
    sourceCompatibility = "${JavaVersion.VERSION_17}"
    inputs.files(tasks.processResources)
}
