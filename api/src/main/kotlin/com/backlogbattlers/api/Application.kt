package com.backlogbattlers.api

import com.backlogbattlers.api.auth.GoogleIdTokenVerifier
import com.backlogbattlers.api.auth.JwtService
import com.backlogbattlers.api.auth.bearerCurrentUser
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.routes.achievementRoutes
import com.backlogbattlers.api.routes.authRoutes
import com.backlogbattlers.api.routes.friendRoutes
import com.backlogbattlers.api.routes.gameRoutes
import com.backlogbattlers.api.routes.healthRoutes
import com.backlogbattlers.api.routes.libraryRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

/**
 * Ktor application module entry point loading database, routes, and environment configuration.
 */
fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) { json() }
    configureErrors()

    // jwt throw states
    val jwtSecret = Config.default.get("JWT_SECRET").orEmpty().ifBlank {
        throw IllegalStateException("JWT_SECRET environment variable not set. The server won't start without a JWT secret.")
    }
    val jwtIssuer = Config.default.get("JWT_ISSUER").orEmpty().ifBlank {
        throw IllegalStateException("JWT_ISSUER environment variable is not set. The server won't start without explicit JWT issuer.")
    }
    val googleClientId = Config.default.get("GOOGLE_OAUTH_CLIENT_ID").orEmpty().ifBlank {
        throw IllegalStateException("GOOGLE_OAUTH_CLIENT_ID environment variable not set. The server won't start with hardcoded fallback client ID")
    }

    val httpClient = igdbHttpClient()
    val googleVerifier = GoogleIdTokenVerifier(httpClient, googleClientId)
    val jwtService = JwtService(jwtSecret, jwtIssuer)
    val igdb = IgdbClient.fromConfig()

    // @Dylan, @Andre
    // To add a feature, create : routes/YourFeatureRoutes.kt with `fun Route.yourFeatureRoutes()`,
    // and call it below.
    routing {
        healthRoutes()
        gameRoutes(igdb)
        achievementRoutes(igdb, SteamClient.fromConfig())
        authRoutes(googleVerifier, jwtService)
        libraryRoutes(igdb, currentUser = bearerCurrentUser(jwtService))
        friendRoutes(currentUser = bearerCurrentUser(jwtService))
    }
}
//------------------------------EOF------------------------------\\