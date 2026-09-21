package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.ensureGameSaved
import com.backlogbattlers.api.library.CreateEntryRequest
import com.backlogbattlers.api.library.UpdateEntryRequest
import com.backlogbattlers.api.library.UpdateResult
import com.backlogbattlers.api.library.createEntry
import com.backlogbattlers.api.library.deleteEntry
import com.backlogbattlers.api.library.listEntries
import com.backlogbattlers.api.library.updateEntry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

private const val MAX_HOURS_PLAYED = 100_000.0
private const val MAX_UNLOCKED_PER_UPDATE = 1000
private const val MAX_ACHIEVEMENT_ID_LENGTH = 64

/** The signed-in user's game library. Every route needs [currentUser] to identify the caller. */
fun Route.libraryRoutes(igdb: IgdbClient, currentUser: CurrentUser) {
    route("/users/me/library") {

        // GET /users/me/library
        get {
            val owner = call.signedInUser(currentUser) ?: return@get
            call.respond(listEntries(owner))
        }

        // POST /users/me/library   {"gameId": 113112, "platform": "PC"}
        post {
            val owner = call.signedInUser(currentUser) ?: return@post
            val request = call.receive<CreateEntryRequest>()

            if (request.gameId <= 0) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("gameId must be a positive number"))
            }
            if (request.status == LibraryStatus.COMPLETED) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("A game can only be completed through the complete endpoint"))
            }
            // The library refers to our own copy of the game, so fetch it from IGDB the first time
            if (!ensureGameSaved(igdb, request.gameId)) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Game ${request.gameId} was not found"))
            }

            val entry = createEntry(owner, request)
                ?: return@post call.respond(HttpStatusCode.Conflict, ApiError("This game is already in your library on that platform"))
            call.respond(HttpStatusCode.Created, entry)
        }

        // PATCH /users/me/library/{id}   {"status": "PLAYING", "hoursPlayed": 12.5}
        patch("{id}") {
            val owner = call.signedInUser(currentUser) ?: return@patch
            val entryId = call.entryId() ?: return@patch
            val request = call.receive<UpdateEntryRequest>()

            val problem = problemWith(request)
            if (problem != null) return@patch call.respond(HttpStatusCode.BadRequest, ApiError(problem))

            when (val result = updateEntry(owner, entryId, request)) {
                is UpdateResult.Updated -> call.respond(result.entry)
                UpdateResult.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("Library entry was not found"))
                is UpdateResult.UnknownAchievements -> call.respond(
                    HttpStatusCode.BadRequest,
                    ApiError("These achievements do not belong to this game: ${result.ids.joinToString()}"),
                )
            }
        }

        // DELETE /users/me/library/{id}
        delete("{id}") {
            val owner = call.signedInUser(currentUser) ?: return@delete
            val entryId = call.entryId() ?: return@delete

            if (deleteEntry(owner, entryId)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiError("Library entry was not found"))
            }
        }
    }
}

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to use your library"))
    return user
}

/** The `{id}` from the path, or null after answering 400. */
private suspend fun ApplicationCall.entryId(): Uuid? {
    val id = runCatching { Uuid.parse(parameters["id"].orEmpty()) }.getOrNull()
    if (id == null) respond(HttpStatusCode.BadRequest, ApiError("Library entry id is not valid"))
    return id
}

/** What is wrong with an update, or null when it is fine. */
private fun problemWith(request: UpdateEntryRequest): String? {
    val hours = request.hoursPlayed
    val achievements = request.unlockedAchievementIds
    return when {
        request.status == LibraryStatus.COMPLETED -> "A game can only be completed through the complete endpoint"
        hours != null && (hours.isNaN() || hours < 0 || hours > MAX_HOURS_PLAYED) ->
            "hoursPlayed must be between 0 and ${MAX_HOURS_PLAYED.toInt()}"
        achievements != null && achievements.size > MAX_UNLOCKED_PER_UPDATE ->
            "At most $MAX_UNLOCKED_PER_UPDATE achievements can be sent at once"
        achievements != null && achievements.any { it.isBlank() || it.length > MAX_ACHIEVEMENT_ID_LENGTH } ->
            "Achievement ids must be 1 to $MAX_ACHIEVEMENT_ID_LENGTH characters"
        else -> null
    }
}
