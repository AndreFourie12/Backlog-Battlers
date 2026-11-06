package com.backlogbattlers.app.data.remote

import android.util.Log
import com.backlogbattlers.app.data.repository.AccessTokenSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// dto representing relationship status returned by the backend
@Serializable
enum class FriendRelationshipStatusDto { NONE, PENDING_SENT, PENDING_RECEIVED, FRIENDS }

// dto representing a friend row returned by the backend
@Serializable
data class FriendDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val monthlyPoints: Int,
    val achievementCount: Int,
    val isOnline: Boolean,
    val pictureId: String? = null,
)

// dto representing an incoming friend request returned by the backend
@Serializable
data class FriendRequestDto(
    val requestId: String,
    val fromUserId: String,
    val fromDisplayName: String,
    val fromAvatarUrl: String?,
    val createdAt: Long,
    val fromPictureId: String? = null,
)

// dto representing a friend search result returned by the backend
@Serializable
data class FriendSearchResultDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: FriendRelationshipStatusDto,
    val pictureId: String? = null,
)

// dto body for sending a friend request
@Serializable
data class CreateFriendRequestBody(val targetUserId: String)

// dto response body returned after sending a friend request
@Serializable
data class SendRequestResponseDto(val requestId: String, val targetUserId: String, val createdAt: Long)

// authenticated http client wrapping backend friend management api calls
class FriendsApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokens: AccessTokenSource,
) {
    //------------------------------
    // fetches the signed in user's friend list
    suspend fun getFriends(): List<FriendDto> = authedGet("$baseUrl/users/me/friends")

    //------------------------------
    // fetches incoming, not yet answered friend requests
    suspend fun getIncomingRequests(): List<FriendRequestDto> = authedGet("$baseUrl/users/me/friends/requests")

    //------------------------------
    // searches for people to add by display name
    suspend fun searchUsers(query: String): List<FriendSearchResultDto> = tokens.call { token ->
        val response = httpClient.get("$baseUrl/users/me/friends/search") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("q", query)
        }
        response.checkedBody()
    }

    //------------------------------
    // sends a friend request to another user
    suspend fun sendRequest(targetUserId: String): SendRequestResponseDto = tokens.call { token ->
        val response = httpClient.post("$baseUrl/users/me/friends/requests") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateFriendRequestBody(targetUserId))
        }
        response.checkedBody()
    }

    //------------------------------
    // accepts a pending friend request, returning the new friend
    suspend fun acceptRequest(requestId: String): FriendDto = tokens.call { token ->
        val response = httpClient.post("$baseUrl/users/me/friends/requests/$requestId/accept") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        response.checkedBody()
    }

    //------------------------------
    // rejects a pending friend request
    suspend fun rejectRequest(requestId: String) {
        tokens.call { token ->
            val response = httpClient.post("$baseUrl/users/me/friends/requests/$requestId/reject") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            // a 401 is thrown for a retry, and this checks the reject actually succeeded, since this call has no body to decode
            if (response.status == HttpStatusCode.Unauthorized) throw SessionExpiredException()
            if (!response.status.isSuccess()) {
                Log.e(TAG, "Rejecting a friend request failed [${response.status.value}]")
                throw IllegalStateException("server returned HTTP ${response.status.value}")
            }
        }
    }

    //------------------------------
    // performs a GET with a valid bearer token attached/ decodes json list response
    private suspend inline fun <reified T> authedGet(url: String): T = tokens.call { token ->
        val response = httpClient.get(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        response.checkedBody<T>()
    }

    //------------------------------
    // decodes a successful response body, or throws with the servers error text.
    // a 401 is SessionExpiredException, which is what makes AccessTokenSource renew the token and retry
    private suspend inline fun <reified T> HttpResponse.checkedBody(): T {
        if (status == HttpStatusCode.Unauthorized) throw SessionExpiredException()
        if (!status.isSuccess()) {
            val errorText = runCatching { body<String>() }.getOrDefault("HTTP ${status.value}")
            Log.e(TAG, "${call.request.url.encodedPath} failed [${status.value}]: $errorText")
            throw IllegalStateException("server returned HTTP ${status.value}: $errorText")
        }
        return body()
    }

    private companion object {
        const val TAG = "FriendsApi"
    }
}
//------------------------------EOF------------------------------\\