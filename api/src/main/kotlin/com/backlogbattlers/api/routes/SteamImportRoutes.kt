package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.steam.SteamImportResult
import com.backlogbattlers.api.steam.SteamImporter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

/** Bringing the games a user owns on Steam into their library. */
fun Route.steamImportRoutes(importer: SteamImporter, currentUser: CurrentUser) {

    // POST /users/me/steam/import
    // Adds every Steam game the catalogue knows to the library (PC, backlog) with Steam's hours,
    // and raises the hours of PC entries that are behind Steam. Steam or IGDB being down is a 502
    // (see Errors.kt) and changes nothing
    post("/users/me/steam/import") {
        val me = call.signedInUser(currentUser) ?: return@post

        when (val result = importer.import(me)) {
            is SteamImportResult.Imported -> call.respond(result.result)
            SteamImportResult.NotLinked -> call.respond(HttpStatusCode.NotFound, ApiError("No Steam account is linked"))
            SteamImportResult.PrivateProfile -> call.respond(
                HttpStatusCode.Forbidden,
                ApiError("Your Steam profile or its game details are private, so Steam won't list your games"),
            )
            is SteamImportResult.TooSoon -> {
                call.response.header(HttpHeaders.RetryAfter, result.retryAfterSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError("Your Steam games were imported moments ago. Try again in ${result.retryAfterSeconds} seconds"),
                )
            }
        }
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to import your Steam games"))
    return user
}
//------------------------------EOF------------------------------\\
