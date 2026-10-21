package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.db.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

/** The signed-in user's own account. */
fun Route.accountRoutes(currentUser: CurrentUser) {

    // DELETE /users/me
    // Removes the account. Every table that points at Users does so with ON DELETE CASCADE, so
    // the user's library (and its unlocked achievements), completion records, monthly points,
    // friend requests and friendships all go with it. Their access token stops working at once,
    // because bearerCurrentUser rejects tokens for users that no longer exist.
    delete("/users/me") {
        val me = call.signedInUser(currentUser) ?: return@delete

        if (deleteAccount(me)) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound, ApiError("Account was not found"))
        }
    }
}

/** Deletes the user's row, and through the cascades everything they own. False if there was no such user. */
private fun deleteAccount(userId: Uuid): Boolean = transaction {
    Users.deleteWhere { Users.id eq userId } > 0
}

// Each routes file in this package keeps its own file-private copy of this helper on purpose,
// see CompletionRoutes.kt.

/** The caller's id, or null after answering 401 (so the route just returns). */
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("Sign in to manage your account"))
    return user
}
//------------------------------EOF------------------------------\\
