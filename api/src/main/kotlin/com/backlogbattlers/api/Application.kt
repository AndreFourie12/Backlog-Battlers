package com.backlogbattlers.api

import com.backlogbattlers.api.db.DatabaseFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

/** Response body for [GET /health]. Hosts poll this to know the service is alive. */
@Serializable
data class Health(val status: String)

/**
 * Ktor module, loaded by name from application.yaml.
 * Every later feature (auth, library, leaderboard) plugs in here.
 */
fun Application.module() {
    // Connect to the database and create any missing tables before serving requests
    DatabaseFactory.init()

    // Serialise/deserialise all request and response bodies as JSON
    install(ContentNegotiation) { json() }

    routing {
        get("/health") { call.respond(Health("ok")) }
    }
}
