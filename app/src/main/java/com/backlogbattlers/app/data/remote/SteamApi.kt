package com.backlogbattlers.app.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** The Steam account linked to the user, as `GET`/`PUT /users/me/steam` send it: the 17 digit SteamID64. */
@Serializable
data class SteamLinkDto(val steamId: String)

/**
 * API client for the Steam account a user links, which the server uses to check their
 * achievements against Steam.
 */
class SteamApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    //------------------------------
    // GETs the linked Steam ID, or null when the account has none (a 404)
    suspend fun getLink(accessToken: String): String? {
        val response = httpClient.get("$baseUrl/users/me/steam") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.bodyOrThrow<SteamLinkDto>().steamId
    }

    //------------------------------
    // PUTs the Steam ID, replacing any linked before. returns false when another user already
    // has that Steam account (a 409)
    suspend fun putLink(accessToken: String, steamId: String): Boolean {
        val response = httpClient.put("$baseUrl/users/me/steam") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SteamLinkDto(steamId))
        }
        if (response.status == HttpStatusCode.Conflict) return false
        response.bodyOrThrow<SteamLinkDto>()
        return true
    }

    //------------------------------
    // DELETEs the link. having none already (a 404) counts as unlinked
    suspend fun deleteLink(accessToken: String) {
        val response = httpClient.delete("$baseUrl/users/me/steam") {
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
            Log.e("SteamApi", "Steam link request failed [${status.value}]: $errorText")
            throw IllegalStateException("Server returned HTTP ${status.value}: $errorText")
        }
        return body()
    }
}
//------------------------------EOF------------------------------\\
