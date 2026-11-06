package com.backlogbattlers.app.data.remote

import android.util.Log
import com.backlogbattlers.app.data.repository.AccessTokenSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// dto for one feed event as the backend sends it. the type stays text so a kind added to the
// backend later does not break reading the whole page
@Serializable
data class ActivityEventDto(
    val eventId: String,
    val type: String,
    val actorId: String,
    val actorName: String,
    val actorAvatarUrl: String? = null,
    val gameId: Int? = null,
    val gameTitle: String? = null,
    val subjectId: String? = null,
    val subjectName: String? = null,
    val count: Int = 1,
    val points: Int? = null,
    val detail: String? = null,
    val createdAt: Long,
)

// dto for one page of the feed
@Serializable
data class ActivityPageDto(
    val events: List<ActivityEventDto> = emptyList(),
    val nextBefore: Long? = null,
)

// authenticated http client for the activity feed: GET /activity
class ActivityApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokens: AccessTokenSource,
) {
    //------------------------------
    // fetches one page of the feed, the newest events when before is null, otherwise the ones older than it
    suspend fun getActivity(before: Long? = null, limit: Int = PAGE_SIZE): ActivityPageDto = tokens.call { token ->
        val response = httpClient.get("$baseUrl/activity") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("limit", limit)
            if (before != null) parameter("before", before)
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

    companion object {
        // events asked for per page
        const val PAGE_SIZE = 30
        private const val TAG = "ActivityApi"
    }
}
//------------------------------EOF------------------------------\\
