package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.settings.SettingsDto
import com.backlogbattlers.api.settings.problemWith
import com.backlogbattlers.api.settings.saveSettings
import com.backlogbattlers.api.settings.settingsFor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

/** The signed-in user's app preferences, kept here so they follow them to another phone. */
fun Route.settingsRoutes(currentUser: CurrentUser) {
    route("/users/me/settings") {

        // GET /users/me/settings
        // 404 until the app has saved settings once, which tells it to upload its own
        get {
            val me = call.signedInUser(currentUser) ?: return@get
            val settings = settingsFor(me)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("No settings have been saved yet"))
            call.respond(settings)
        }

        // PUT /users/me/settings   {"language":"af","themeMode":"DARK","homeBackdrop":"HILLS","defaultPlatform":"PLAYSTATION", ...}
        // Replaces every setting at once; the newest save wins
        put {
            val me = call.signedInUser(currentUser) ?: return@put
            val request = call.receive<SettingsDto>()

            val problem = problemWith(request)
            if (problem != null) return@put call.respond(HttpStatusCode.BadRequest, ApiError(problem))

            call.respond(saveSettings(me, request))
        }
    }
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to use your settings"))
    return user
}
//------------------------------EOF------------------------------\\
