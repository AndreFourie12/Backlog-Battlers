package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.notifications.RegisterDeviceRequest
import com.backlogbattlers.api.notifications.problemWithToken
import com.backlogbattlers.api.notifications.registerDevice
import com.backlogbattlers.api.notifications.removeDevice
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

/** The phones the signed-in user gets push notifications on. */
fun Route.deviceRoutes(currentUser: CurrentUser) {
    route("/devices") {

        // POST /devices   {"token": "<FCM registration token>"}
        // Sent on sign-in, on launch and whenever Firebase gives the phone a new token. Saving the
        // same token again is fine; a token another account had moves to the caller
        post {
            val me = call.signedInUser(currentUser) ?: return@post
            val token = call.receive<RegisterDeviceRequest>().token.trim()

            val problem = problemWithToken(token)
            if (problem != null) return@post call.respond(HttpStatusCode.BadRequest, ApiError(problem))

            registerDevice(me, token)
            call.respond(HttpStatusCode.NoContent)
        }

        // DELETE /devices/{token}
        // Sent on sign-out, so the phone stops getting this account's pushes. 204 whether or not
        // the token was stored, so signing out twice is harmless
        delete("{token}") {
            val me = call.signedInUser(currentUser) ?: return@delete
            removeDevice(me, call.parameters["token"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to get notifications"))
    return user
}
//------------------------------EOF------------------------------\\
