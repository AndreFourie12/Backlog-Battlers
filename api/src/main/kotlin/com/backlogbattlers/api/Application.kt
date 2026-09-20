package com.backlogbattlers.api

import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.routes.achievementRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import com.backlogbattlers.api.routes.gameRoutes
import com.backlogbattlers.api.routes.healthRoutes
import com.backlogbattlers.api.routes.libraryRoutes

/**
 * Ktor module, loaded by name from application.yaml.
 * Every later feature (auth, library, leaderboard) plugs in here.
 */
fun Application.module() {
    // Connect to the database and create any missing tables before serving requests
    DatabaseFactory.init()

    // Serialise/deserialise all request and response bodies as JSON
    install(ContentNegotiation) { json() }
    configureErrors()

    // @Dylan, @Andre
    // To add a feature, create : routes/YourFeatureRoutes.kt with `fun Route.yourFeatureRoutes()`,
    // and call it below.
    val igdb = IgdbClient.fromEnvironment()
    routing {
        healthRoutes()
        gameRoutes(igdb)
        achievementRoutes(igdb, SteamClient.fromEnvironment())
        // Until login is built nobody can be identified, so every library request answers 401.
        // The login feature replaces `{ null }` with the real "who is calling" function.
        libraryRoutes(igdb, currentUser = { null })
    }
}
