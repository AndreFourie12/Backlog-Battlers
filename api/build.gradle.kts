plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor) // provides `run` and `buildFatJar` (used later for deployment)
}

group = "com.backlogbattlers"
version = "0.0.1"

application {
    // EngineMain reads src/main/resources/application.yaml (port + which modules to load)
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages) // turns exceptions into JSON error responses
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)

    // HTTP client, used by the API to call IGDB
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Database: Exposed (Kotlin SQL DSL) + Hikari connection pool
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    runtimeOnly(libs.postgresql) // production database (Neon)
    implementation(libs.h2) // in-memory database for local runs and unit tests

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock) // fake IGDB responses in tests
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
