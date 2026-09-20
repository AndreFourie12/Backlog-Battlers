package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.games.GameDto
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

private const val MAX_QUERY_LENGTH = 100

/** Game catalogue: search IGDB, and fetch one game with its time-to-beat. */
fun Route.gameRoutes(igdb: IgdbClient) {

    // GET /games/search?q=hades&page=1
    get("/games/search") {
        val query = call.request.queryParameters["q"]?.trim()
        if (query.isNullOrEmpty() || query.length > MAX_QUERY_LENGTH) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("q is required and must be 1 to $MAX_QUERY_LENGTH characters"))
        }

        val pageParam = call.request.queryParameters["page"]
        val page = if (pageParam == null) 1 else pageParam.toIntOrNull()
        if (page == null || page < 1) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("page must be a whole number of 1 or more"))
        }

        // Search results skip time-to-beat: that would cost one extra IGDB call per result
        call.respond(igdb.search(query, page).map { it.toDto() })
    }

    // GET /games/{id}
    get("/games/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Game id must be a number"))

        val game = igdb.game(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Game $id was not found"))

        val dto = game.toDto(igdb.timeToBeat(id))
        saveGame(dto)
        call.respond(dto)
    }
}

/** Keeps our own copy of the game so leaderboards and scoring can use it without calling IGDB. */
private fun saveGame(game: GameDto) {
    transaction {
        // upsert = insert, or update if this game id is already stored
        Games.upsert {
            it[id] = game.gameId
            it[title] = game.title
            it[avgCompletionHours] = game.avgCompletionHours
            it[avg100PercentHours] = game.avg100PercentHours
        }
    }
}
