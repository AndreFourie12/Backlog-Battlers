package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.profile.UpdateMeRequest
import com.backlogbattlers.api.profile.UpdateMeResult
import com.backlogbattlers.api.profile.meFor
import com.backlogbattlers.api.profile.updateMe
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlin.uuid.Uuid
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.backlogbattlers.api.routes.MeRoutes")

/** The signed-in user's own name and picture: what friends see of them. */
fun Route.meRoutes(currentUser: CurrentUser) {
    route("/users/me") {

        // GET /users/me
        get {
            val me = call.signedInUser(currentUser) ?: return@get
            call.respond(meFor(me) ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Account was not found")))
        }

        // PATCH /users/me   {"displayName":"Dylan"} and/or {"pictureId":"pfp_03"} (an empty pictureId takes it away)
        patch {
            val me = call.signedInUser(currentUser) ?: return@patch
            val request = call.receive<UpdateMeRequest>()

            when (val result = updateMe(me, request)) {
                is UpdateMeResult.Updated -> {
                    log.info("User {} changed their profile: name changed={}, picture changed={}", me, request.displayName != null, request.pictureId != null)
                    call.respond(result.me)
                }
                UpdateMeResult.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("Account was not found"))
                is UpdateMeResult.Invalid -> call.respond(HttpStatusCode.BadRequest, ApiError(result.problem))
            }
        }
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to change your profile"))
    return user
}
//------------------------------EOF------------------------------\\
