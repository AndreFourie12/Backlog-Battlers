package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.profile.profileFor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.Uuid

/** The signed-in user's profile: level, stats, badges and showcases, all worked out from their data. */
fun Route.profileRoutes(currentUser: CurrentUser) {

    // GET /users/me/profile
    get("/users/me/profile") {
        val me = call.signedInUser(currentUser) ?: return@get
        val profile = profileFor(me)
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Account was not found"))
        call.respond(profile)
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to see your profile"))
    return user
}
//------------------------------EOF------------------------------\
