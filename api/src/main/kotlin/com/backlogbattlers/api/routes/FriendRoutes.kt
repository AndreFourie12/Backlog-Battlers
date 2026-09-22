package com.backlogbattlers.api.routes

import com.backlogbattlers.api.ApiError
import com.backlogbattlers.api.CurrentUser
import com.backlogbattlers.api.friends.CreateFriendRequestBody
import com.backlogbattlers.api.friends.RespondToRequestResult
import com.backlogbattlers.api.friends.SendRequestResult
import com.backlogbattlers.api.friends.acceptRequest
import com.backlogbattlers.api.friends.listFriends
import com.backlogbattlers.api.friends.listIncomingRequests
import com.backlogbattlers.api.friends.rejectRequest
import com.backlogbattlers.api.friends.searchUsers
import com.backlogbattlers.api.friends.sendRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

//------------------------------
// defines ktor route handlers for friend management ops
fun Route.friendRoutes(currentUser: CurrentUser) {
    route("/users/me/friends") {

        // GET /users/me/friends
        get {
            val owner = call.signedInUser(currentUser) ?: return@get
            call.respond(listFriends(owner))
        }

        // GET /users/me/friends/search?q=...
        get("/search") {
            val owner = call.signedInUser(currentUser) ?: return@get
            val query = call.request.queryParameters["q"]?.trim()

            if (query.isNullOrEmpty() || query.length > MAX_QUERY_LENGTH) {
                return@get call.respond(HttpStatusCode.BadRequest, ApiError("q is required and must be 1 to $MAX_QUERY_LENGTH characters"))
            }
            call.respond(searchUsers(owner, query))
        }

        route("/requests") {

            // GET /users/me/friends/requests
            get {
                val owner = call.signedInUser(currentUser) ?: return@get
                call.respond(listIncomingRequests(owner))
            }

            // POST /users/me/friends/requests   {"targetUserId": "uuid"}
            post {
                val owner = call.signedInUser(currentUser) ?: return@post
                val request = call.receive<CreateFriendRequestBody>()
                val targetId = runCatching { Uuid.parse(request.targetUserId) }.getOrNull()

                if (targetId == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("targetUserId is not a valid id"))
                }

                when (val result = sendRequest(owner, targetId)) {
                    is SendRequestResult.Sent -> call.respond(HttpStatusCode.Created, result.request)
                    SendRequestResult.CannotAddSelf -> call.respond(HttpStatusCode.BadRequest, ApiError("you cannot send a friend request to yourself"))
                    SendRequestResult.UserNotFound -> call.respond(HttpStatusCode.NotFound, ApiError("user was not found"))
                    SendRequestResult.AlreadyFriends -> call.respond(HttpStatusCode.Conflict, ApiError("you are already friends with this person"))
                    SendRequestResult.RequestAlreadyExists -> call.respond(HttpStatusCode.Conflict, ApiError("a friend request already exists between you and this person"))
                }
            }

            // POST /users/me/friends/requests/{id}/accept
            post("{id}/accept") {
                val owner = call.signedInUser(currentUser) ?: return@post
                val requestId = call.requestId() ?: return@post
                when (val result = acceptRequest(owner, requestId)) {
                    is RespondToRequestResult.Accepted -> call.respond(result.friend)
                    RespondToRequestResult.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("friend request was not found"))
                    RespondToRequestResult.Rejected -> Unit // acceptRequest never returns Rejected, branch kept for exhaustiveness
                }
            }

            // POST /users/me/friends/requests/{id}/reject
            post("{id}/reject") {
                val owner = call.signedInUser(currentUser) ?: return@post
                val requestId = call.requestId() ?: return@post
                when (rejectRequest(owner, requestId)) {
                    RespondToRequestResult.Rejected -> call.respond(HttpStatusCode.NoContent)
                    RespondToRequestResult.NotFound -> call.respond(HttpStatusCode.NotFound, ApiError("friend request was not found"))
                    is RespondToRequestResult.Accepted -> Unit // rejectRequest never returns Accepted, branch kept for exhaustiveness
                }
            }
        }
    }
}

private const val MAX_QUERY_LENGTH = 100

//------------------------------
// extracts signed in user uuid or responds with a 401
private suspend fun ApplicationCall.signedInUser(currentUser: CurrentUser): Uuid? {
    val user = currentUser(this)
    if (user == null) respond(HttpStatusCode.Unauthorized, ApiError("sign in to use friends"))
    return user
}

//------------------------------
// extracts request id uuid from path params or responds 400
private suspend fun ApplicationCall.requestId(): Uuid? {
    val id = runCatching { Uuid.parse(parameters["id"].orEmpty()) }.getOrNull()
    if (id == null) respond(HttpStatusCode.BadRequest, ApiError("request id is not valid"))
    return id
}
//------------------------------EOF------------------------------\\