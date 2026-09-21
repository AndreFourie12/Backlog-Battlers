package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.games.BrowseCategory
import com.backlogbattlers.api.games.GameDto
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.IgdbGame
import com.backlogbattlers.api.games.StarterGames
import com.backlogbattlers.api.games.saveGame
import com.backlogbattlers.api.games.steamArtUrl
import com.backlogbattlers.api.games.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_QUERY_LENGTH = 100
private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 50

/** Game catalogue: search IGDB, and fetch one game with its time-to-beat. */
fun Route.gameRoutes(igdb: IgdbClient) {
    val starterGames = StarterGames(igdb)

    // GET /games/starter
    get("/games/starter") {
        call.respond(starterGames.load())
    }

    // GET /games/search?q=hades&page=1
    get("/games/search") {
        val pageParam = call.request.queryParameters["page"]
        val page = if (pageParam == null) 1 else pageParam.toIntOrNull()
        if (page == null || page < 1) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("page must be a whole number of 1 or more"))
        }

        val limitParam = call.request.queryParameters["limit"]
        val limit = if (limitParam == null) DEFAULT_LIMIT else limitParam.toIntOrNull()
        if (limit == null || limit < 1 || limit > MAX_LIMIT) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("limit must be a whole number of 1 to $MAX_LIMIT"))
        }

        // Search results skip time-to-beat: that would cost one extra IGDB call per result
        val categoryKey = call.request.queryParameters["category"]?.trim()
        if (!categoryKey.isNullOrEmpty()) {
            val category = BrowseCategory.fromKey(categoryKey)
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("category must be one of: ${BrowseCategory.keys()}"),
                )
            return@get call.respond(withSteamArt(igdb, igdb.browse(category, page, limit)))
        }

        val query = call.request.queryParameters["q"]?.trim()
        if (query.isNullOrEmpty() || query.length > MAX_QUERY_LENGTH) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiError("q is required and must be 1 to $MAX_QUERY_LENGTH characters"),
            )
        }

        call.respond(withSteamArt(igdb, igdb.search(query, page, limit)))
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

private suspend fun withSteamArt(igdb: IgdbClient, games: List<IgdbGame>): List<GameDto> {
    val steamAppIds = igdb.steamAppIds(games.map { it.id })
    return games.map { it.toDto(artworkUrl = steamArtUrl(steamAppIds[it.id])) }
}
