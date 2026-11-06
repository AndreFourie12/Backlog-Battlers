package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.activity.DEFAULT_ACTIVITY_LIMIT
import com.backlogbattlers.api.activity.MAX_ACTIVITY_LIMIT
import com.backlogbattlers.api.activity.activityFeed
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.Instant
import kotlin.uuid.Uuid

/** The activity feed: what the signed-in user and their friends have been doing. */
fun Route.activityRoutes(currentUser: CurrentUser) {

    // GET /activity?limit=30&before=1761030000000
    get("/activity") {
        val owner = call.signedInUser(currentUser) ?: return@get

        val limitParam = call.request.queryParameters["limit"]
        val limit = limitParam?.toIntOrNull()
        if (limitParam != null && (limit == null || limit < 1)) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("limit must be a whole number of 1 or more"))
        }
        val beforeParam = call.request.queryParameters["before"]
        val before = beforeParam?.toLongOrNull()
        if (beforeParam != null && (before == null || before < 0)) {
            return@get call.respond(HttpStatusCode.BadRequest, ApiError("before must be a time in epoch milliseconds"))
        }

        call.respond(
            activityFeed(
                owner,
                limit = (limit ?: DEFAULT_ACTIVITY_LIMIT).coerceAtMost(MAX_ACTIVITY_LIMIT),
                before = before?.let(Instant::ofEpochMilli),
            ),
        )
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to see your activity"))
    return user
}
//------------------------------EOF------------------------------\
