package com.backlogbattlers.app.data.remote

import com.backlogbattlers.app.data.local.TokenStorage
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
)

// dto representing an incoming friend request returned by the backend
@Serializable
data class FriendRequestDto(
    val requestId: String,
    val fromUserId: String,
    val fromDisplayName: String,
    val fromAvatarUrl: String?,
    val createdAt: Long,
)

// dto representing a friend search result returned by the backend
@Serializable
data class FriendSearchResultDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: FriendRelationshipStatusDto,
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
    private val tokenStorage: TokenStorage,
) {
    //------------------------------
    // fetches the signed in user's friend list
    suspend fun getFriends(): List<FriendDto> = authedGet("$baseUrl/users/me/friends")

    //------------------------------
    // fetches incoming, not yet answered friend requests
    suspend fun getIncomingRequests(): List<FriendRequestDto> = authedGet("$baseUrl/users/me/friends/requests")

    //------------------------------
    // searches for people to add by display name
    suspend fun searchUsers(query: String): List<FriendSearchResultDto> {
        val response = httpClient.get("$baseUrl/users/me/friends/search") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, bearerHeader())
            parameter("q", query)
        }
        return response.checkedBody()
    }

    //------------------------------
    // sends a friend request to another user
    suspend fun sendRequest(targetUserId: String): SendRequestResponseDto {
        val response = httpClient.post("$baseUrl/users/me/friends/requests") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, bearerHeader())
            setBody(CreateFriendRequestBody(targetUserId))
        }
        return response.checkedBody()
    }

    //------------------------------
    // accepts a pending friend request, returning the new friend
    suspend fun acceptRequest(requestId: String): FriendDto {
        val response = httpClient.post("$baseUrl/users/me/friends/requests/$requestId/accept") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, bearerHeader())
        }
        return response.checkedBody()
    }

    //------------------------------
    // rejects a pending friend request
    suspend fun rejectRequest(requestId: String) {
        val response = httpClient.post("$baseUrl/users/me/friends/requests/$requestId/reject") {
            header(HttpHeaders.Authorization, bearerHeader())
        }
        // this if statement checks the reject actually succeeded, since this call has no body to decode
        if (!response.status.isSuccess()) {
            throw IllegalStateException("server returned HTTP ${response.status.value}")
        }
    }

    //------------------------------
    // performs a GET with the bearer header attached/ decodes json list response
    private suspend inline fun <reified T> authedGet(url: String): T {
        val response = httpClient.get(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, bearerHeader())
        }
        return response.checkedBody()
    }

    private fun bearerHeader(): String = "Bearer ${tokenStorage.getAccessToken().orEmpty()}"

    //------------------------------
    // decodes a successful response body, or throws with the servers error text
    private suspend inline fun <reified T> HttpResponse.checkedBody(): T {
        if (!status.isSuccess()) {
            val errorText = runCatching { body<String>() }.getOrDefault("HTTP ${status.value}")
            throw IllegalStateException("server returned HTTP ${status.value}: $errorText")
        }
        return body()
    }
}
//------------------------------EOF------------------------------\\