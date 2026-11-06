package com.backlogbattlers.app.data.remote

import android.util.Log
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * A library entry as `GET /users/me/library` sends it. Times are epoch milliseconds, hours a double.
 */
@Serializable
data class LibraryEntryDto(
    val libraryEntryId: String,
    val gameId: Int,
    val platform: Platform,
    val status: LibraryStatus,
    val hoursPlayed: Double,
    val unlockedAchievementIds: List<String>,
    // the unlocked ones Steam has confirmed. an older server sends none
    val verifiedAchievementIds: List<String> = emptyList(),
    val completionType: CompletionType? = null,
    val addedAt: Long,
    val updatedAt: Long,
    // Saved server details let a synced entry appear even on a fresh phone.
    val game: GameDto? = null,
)

/** Body of `POST /users/me/library`. The id is the one the entry already has on this phone. */
@Serializable
data class CreateLibraryEntryRequest(
    val libraryEntryId: String,
    val gameId: Int,
    val platform: Platform,
    val status: LibraryStatus,
)

/** Body of `PATCH /users/me/library/{id}`. The server rejects COMPLETED, so that is never sent here. */
@Serializable
data class UpdateLibraryEntryRequest(
    val status: LibraryStatus? = null,
    val hoursPlayed: Double,
    // the complete set of unlocked achievements, not just the new ones
    val unlockedAchievementIds: List<String>,
)

/**
 * API client for the signed-in user's game library.
 */
class LibraryApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    //------------------------------
    // GETs every entry in the account's library
    suspend fun list(accessToken: String): List<LibraryEntryDto> {
        val response = httpClient.get("$baseUrl/users/me/library") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            accept(ContentType.Application.Json)
        }
        return response.bodyOrThrow()
    }

    //------------------------------
    // POSTs a new entry under the id it has on this phone. sending it again is harmless (the server
    // answers 200 with the entry it already has). returns null when the game is already in the
    // library on that platform under another id (a 409)
    suspend fun create(accessToken: String, request: CreateLibraryEntryRequest): LibraryEntryDto? {
        val response = httpClient.post("$baseUrl/users/me/library") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status == HttpStatusCode.Conflict) return null
        return response.bodyOrThrow()
    }

    //------------------------------
    // PATCHes an entry. returns null when the account has no such entry (a 404)
    suspend fun update(accessToken: String, id: String, request: UpdateLibraryEntryRequest): LibraryEntryDto? {
        val response = httpClient.patch("$baseUrl/users/me/library/$id") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.bodyOrThrow()
    }

    //------------------------------
    // DELETEs an entry. an entry the account no longer has (a 404) counts as deleted
    suspend fun delete(accessToken: String, id: String) {
        val response = httpClient.delete("$baseUrl/users/me/library/$id") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status == HttpStatusCode.NotFound) return
        response.bodyOrThrow<Unit>()
    }

    //------------------------------
    // decodes a successful response; a 401 means the session is no longer valid
    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        if (status == HttpStatusCode.Unauthorized) throw SessionExpiredException()
        if (!status.isSuccess()) {
            val errorText = runCatching { body<String>() }.getOrDefault("HTTP ${status.value}")
            Log.e("LibraryApi", "Library request failed [${status.value}]: $errorText")
            throw IllegalStateException("Server returned HTTP ${status.value}: $errorText")
        }
        return body()
    }
}
//------------------------------EOF------------------------------\\
