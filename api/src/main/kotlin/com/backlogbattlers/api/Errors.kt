package com.backlogbattlers.api

import com.backlogbattlers.api.auth.GoogleTokenException
import com.backlogbattlers.api.games.IgdbException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/** The body of every error response, so the app always receives errors in one shape. */
@Serializable
data class ApiError(val error: String)

/**
 * Turns exceptions thrown anywhere in a route into JSON error responses.
 * Call once from module(); routes can then simply throw.
 */
fun Application.configureErrors() {
    install(StatusPages) {
        exception<IgdbException> { call, cause ->
            // The detail goes in the log; the app only learns that the catalogue is unavailable
            call.application.log.warn("IGDB problem: ${cause.message}")
            call.respond(HttpStatusCode.BadGateway, ApiError("The game catalogue is unavailable right now"))
        }
        exception<GoogleTokenException> { call, cause ->
            call.application.log.warn("Google token verification failed: ${cause.message}")
            call.respond(HttpStatusCode.Unauthorized, ApiError(cause.message ?: "Invalid Google ID token"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn("Authentication failed: ${cause.message}")
            call.respond(HttpStatusCode.Unauthorized, ApiError(cause.message ?: "Invalid authentication request"))
        }
    }
}
//------------------------------EOF------------------------------\\