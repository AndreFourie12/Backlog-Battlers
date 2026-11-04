package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.steam.LinkResult
import com.backlogbattlers.api.steam.SteamLinkDto
import com.backlogbattlers.api.steam.isValidSteamId
import com.backlogbattlers.api.steam.linkSteamAccount
import com.backlogbattlers.api.steam.linkedSteamId
import com.backlogbattlers.api.steam.unlinkSteamAccount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

/** The Steam account the signed-in user links, so their achievements can be checked against Steam. */
fun Route.steamRoutes(currentUser: CurrentUser) {
    route("/users/me/steam") {

        // GET /users/me/steam
        get {
            val me = call.signedInUser(currentUser) ?: return@get
            val steamId = linkedSteamId(me)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No Steam account is linked"))
            call.respond(SteamLinkDto(steamId))
        }

        // PUT /users/me/steam   {"steamId": "76561198000000000"}
        put {
            val me = call.signedInUser(currentUser) ?: return@put
            val request = call.receive<SteamLinkDto>()

            if (!isValidSteamId(request.steamId)) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiError("steamId must be a 17 digit Steam ID that starts with 7656"))
            }
            when (linkSteamAccount(me, request.steamId)) {
                LinkResult.Linked -> call.respond(SteamLinkDto(request.steamId))
                LinkResult.TakenByAnotherUser -> call.respond(
                    HttpStatusCode.Conflict,
                    ApiError("That Steam account is already linked to another user"),
                )
            }
        }

        // DELETE /users/me/steam
        delete {
            val me = call.signedInUser(currentUser) ?: return@delete
            if (unlinkSteamAccount(me)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError("No Steam account is linked"))
            }
        }
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to link your Steam account"))
    return user
}
//------------------------------EOF------------------------------\\
