package com.backlogbattlers.app.data.remote

import android.util.Log
import com.backlogbattlers.app.data.repository.AccessTokenSource
import com.backlogbattlers.app.domain.model.CompletionType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// dtos for POST /users/me/library/{id}/complete. the status texts stay text, so one this version
// does not know cannot stop a completion from being read
@Serializable
data class CompleteRequestDto(val completionType: CompletionType)

@Serializable
data class CompleteResponseDto(
    val pointsAwarded: Int,
    val achievementPoints: Int = 0,
    val completionTimePoints: Int = 0,
    val speedBonusPoints: Int = 0,
    val monthlyPoints: Int = 0,
    val steamStatus: String = "NOT_NEEDED",
    val unverifiedAchievementIds: List<String> = emptyList(),
)

// the 422 body when 100% is not possible yet
@Serializable
data class HundredPercentRefusedDto(
    val error: String = "",
    val reason: String,
    val steamStatus: String = "NOT_NEEDED",
    val achievementIds: List<String> = emptyList(),
)

// what the backend answered
sealed interface CompleteAnswer {
    data class Done(val response: CompleteResponseDto) : CompleteAnswer
    data class Refused(val refusal: HundredPercentRefusedDto) : CompleteAnswer
    data object AlreadyDone : CompleteAnswer
    data object NotFound : CompleteAnswer

    // Steam (or IGDB) could not be reached, so the backend completed nothing
    data object Unavailable : CompleteAnswer
}

// authenticated http client for completing a game: POST /users/me/library/{id}/complete
class CompletionApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokens: AccessTokenSource,
) {
    //------------------------------
    // asks the backend to complete the entry. the backend checks the achievements with Steam, awards the
    // points, and refuses a 100% that is not earned yet. a 401 is SessionExpiredException, which is what
    // makes AccessTokenSource renew the token and retry
    suspend fun complete(libraryEntryId: String, type: CompletionType): CompleteAnswer = tokens.call { token ->
        val response = httpClient.post("$baseUrl/users/me/library/$libraryEntryId/complete") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(CompleteRequestDto(type))
        }
        when {
            response.status == HttpStatusCode.Unauthorized -> throw SessionExpiredException()
            response.status.isSuccess() -> CompleteAnswer.Done(response.body())
            response.status == HttpStatusCode.UnprocessableEntity -> CompleteAnswer.Refused(response.body())
            response.status == HttpStatusCode.Conflict -> CompleteAnswer.AlreadyDone
            response.status == HttpStatusCode.NotFound -> CompleteAnswer.NotFound
            response.status == HttpStatusCode.BadGateway -> CompleteAnswer.Unavailable
            else -> {
                val errorText = runCatching { response.body<String>() }.getOrDefault("HTTP ${response.status.value}")
                Log.e(TAG, "Completing a game failed [${response.status.value}]: $errorText")
                throw IllegalStateException("server returned HTTP ${response.status.value}: $errorText")
            }
        }
    }

    private companion object {
        const val TAG = "CompletionApi"
    }
}
//------------------------------EOF------------------------------\\
