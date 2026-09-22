package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.scoring.CompleteRequest
import com.backlogbattlers.api.scoring.CompletionResult
import com.backlogbattlers.api.scoring.completeLibraryEntry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.uuid.Uuid

/** Marking a library entry complete and awarding its points. */
fun Route.completionRoutes(currentUser: CurrentUser) {

    // POST /users/me/library/{id}/complete   {"completionType": "MAIN_STORY"}
    post("/users/me/library/{id}/complete") {
        val owner = call.signedInUser(currentUser) ?: return@post
        val entryId = call.entryId() ?: return@post
        val request = call.receive<CompleteRequest>()

        when (val result = completeLibraryEntry(owner, entryId, request.completionType)) {
            is CompletionResult.Completed -> call.respond(HttpStatusCode.OK, result.response)
            CompletionResult.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("Library entry was not found"))
            CompletionResult.AlreadyCompleted -> call.respond(
                HttpStatusCode.Conflict,
                ApiError("This entry was already completed as ${request.completionType}"),
            )
        }
    }
}

// Small private duplicates of LibraryRoutes'/FriendRoutes' identically-shaped helpers. Each
// routes file in this package keeps its own file-private copy on purpose, so unrelated features
// never collide over a shared top-level name (see git history for why).

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to mark a game complete"))
    return user
}

/** The `{id}` from the path, or null after answering 400. */
private suspend fun ApplicationCall.entryId(): Uuid? {
    val id = runCatching { Uuid.parse(parameters["id"].orEmpty()) }.getOrNull()
    if (id == null) respond(HttpStatusCode.BadRequest, ApiError("Library entry id is not valid"))
    return id
}
