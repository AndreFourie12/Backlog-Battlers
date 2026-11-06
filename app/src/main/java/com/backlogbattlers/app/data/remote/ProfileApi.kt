package com.backlogbattlers.app.data.remote

import android.util.Log
import com.backlogbattlers.app.data.repository.AccessTokenSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// dtos for GET /users/me/profile. badge ids and the completion type stay text, so one this
// version does not know cannot stop the whole profile from loading
@Serializable
data class BadgeDto(val id: String, val progress: Int, val target: Int, val earned: Boolean)

@Serializable
data class ShowcaseAchievementDto(
    val achievementId: String,
    val name: String,
    val iconUrl: String? = null,
    val rarityPercent: Double,
    val gameId: Int,
    val gameTitle: String? = null,
)

@Serializable
data class RecentCompletionDto(
    val gameId: Int,
    val gameTitle: String? = null,
    val completionType: String,
    val points: Int,
    val completedAt: Long,
)

@Serializable
data class ProfileDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val xp: Int = 0,
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 100,
    val monthlyPoints: Int = 0,
    val monthRank: Int? = null,
    val gamesInLibrary: Int = 0,
    val gamesCompleted: Int = 0,
    val achievementsUnlocked: Int = 0,
    val badges: List<BadgeDto> = emptyList(),
    val rarestAchievements: List<ShowcaseAchievementDto> = emptyList(),
    val recentCompletions: List<RecentCompletionDto> = emptyList(),
)

// authenticated http client for the signed in user's profile: GET /users/me/profile
class ProfileApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokens: AccessTokenSource,
) {
    //------------------------------
    // fetches the profile: level, stats, badges and showcases, all worked out by the backend
    suspend fun getProfile(): ProfileDto = tokens.call { token ->
        val response = httpClient.get("$baseUrl/users/me/profile") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        response.checkedBody()
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
        const val TAG = "ProfileApi"
    }
}
//------------------------------EOF------------------------------\\
