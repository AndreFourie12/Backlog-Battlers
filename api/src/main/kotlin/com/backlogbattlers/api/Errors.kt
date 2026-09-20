package com.backlogbattlers.api

import com.backlogbattlers.api.games.IgdbException
import com.backlogbattlers.api.games.SteamException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
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

        exception<SteamException> { call, cause ->
            call.application.log.warn("Steam problem: ${cause.message}")
            call.respond(HttpStatusCode.BadGateway, ApiError("Achievement data is unavailable right now"))
        }

        // A request body that is missing, is not JSON, or has a wrong or unknown value
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("The request body is missing or not valid for this endpoint"))
        }

        // Anything unexpected: log everything, tell the app nothing about the internals
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error on ${call.request.local.method.value} ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong on the server"))
        }
    }
}
