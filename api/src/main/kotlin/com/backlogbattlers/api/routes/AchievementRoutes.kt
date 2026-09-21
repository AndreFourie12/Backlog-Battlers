package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.games.achievementsFor
import com.backlogbattlers.api.games.ensureGameSaved
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** A game's achievements with their global rarity, so the app can list and unlock them. */
fun Route.achievementRoutes(igdb: IgdbClient, steam: SteamClient) {

    // GET /games/{id}/achievements
    get("/games/{id}/achievements") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Game id must be a number"))

        if (!ensureGameSaved(igdb, id)) {
            return@get call.respond(HttpStatusCode.NotFound, ApiError("Game $id was not found"))
        }
        call.respond(achievementsFor(id, igdb, steam))
    }
}
