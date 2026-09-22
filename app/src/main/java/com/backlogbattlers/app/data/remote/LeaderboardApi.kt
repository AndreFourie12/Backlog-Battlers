package com.backlogbattlers.app.data.remote

import com.backlogbattlers.app.data.local.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// dto representing one ranked row returned by the backend
@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val monthlyPoints: Int,
)

// dto representing the full GET /leaderboard/monthly response
@Serializable
data class LeaderboardResponseDto(
    val monthPeriod: String,
    val seasonEndsAt: Long,
    val entries: List<LeaderboardEntryDto>,
    val me: LeaderboardEntryDto?,
)

// http client wrapping the backend's monthly leaderboard endpoint. the endpoint itself needs no
// sign-in, but the bearer header is attached anyway so the backend can fill in `me` when possible
class LeaderboardApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenStorage: TokenStorage,
) {
    //------------------------------
    // fetches this month's leaderboard, with the signed in user's own row pinned in `me`
    suspend fun getMonthlyLeaderboard(): LeaderboardResponseDto {
        val response = httpClient.get("$baseUrl/leaderboard/monthly") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${tokenStorage.getAccessToken().orEmpty()}")
        }
        return response.checkedBody()
    }

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
