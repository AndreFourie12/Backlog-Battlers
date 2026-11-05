package com.backlogbattlers.app.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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

/** The answer to `POST /users/me/steam/import`. [games] are the games just added, to cache straight away. */
@Serializable
data class SteamImportDto(
    val ownedOnSteam: Int,
    val added: Int,
    val hoursUpdated: Int,
    val unchanged: Int,
    val notInCatalogue: Int,
    // achievements unlocked on Steam that the library did not have yet; the sync brings them in
    val achievementsUnlocked: Int = 0,
    val games: List<GameDto> = emptyList(),
)

/** How `POST /users/me/steam/import` answered, apart from a lost session (thrown) or no connection. */
sealed interface SteamImportResponse {
    data class Imported(val result: SteamImportDto) : SteamImportResponse

    /** 404: no Steam account is linked. */
    data object NotLinked : SteamImportResponse

    /** 403: the Steam profile or its game details are private. */
    data object PrivateProfile : SteamImportResponse

    /** 429: imported moments ago. */
    data object TooSoon : SteamImportResponse

    /** 502: Steam or IGDB is down. */
    data object Unavailable : SteamImportResponse
}

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
    // asks the server to add the games owned on Steam to the library (one Steam call on the server)
    suspend fun importGames(accessToken: String): SteamImportResponse {
        val response = httpClient.post("$baseUrl/users/me/steam/import") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            accept(ContentType.Application.Json)
        }
        return when (response.status) {
            // a 404 only means "not linked" when the API says so. a bare 404 is a server that
            // doesn't have the import yet (an older deploy), which is reported as unavailable
            HttpStatusCode.NotFound ->
                if (runCatching { response.body<String>() }.getOrDefault("").contains("No Steam account is linked")) {
                    SteamImportResponse.NotLinked
                } else {
                    SteamImportResponse.Unavailable
                }
            HttpStatusCode.Forbidden -> SteamImportResponse.PrivateProfile
            HttpStatusCode.TooManyRequests -> SteamImportResponse.TooSoon
            HttpStatusCode.BadGateway -> SteamImportResponse.Unavailable
            else -> SteamImportResponse.Imported(response.bodyOrThrow())
        }
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
