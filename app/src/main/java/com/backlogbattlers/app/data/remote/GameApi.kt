package com.backlogbattlers.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.IOException

// how long the server gets to answer before we give up and show the retry state, instead of waiting out the engine default
private const val REQUEST_TIMEOUT_MS = 25_000L


@Serializable
data class GameDto(
    val gameId: Int,
    val title: String,
    val coverImageUrl: String? = null,
    val artworkUrl: String? = null,
    val platforms: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val avgCompletionHours: Double? = null,
    val avg100PercentHours: Double? = null,
)

@Serializable
data class RecommendationDto(
    val game: GameDto,
    val reason: String,
)


class GameApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {


    suspend fun starterGames(): List<RecommendationDto> {
        return await {
            httpClient.get("$baseUrl/games/starter") {
                accept(ContentType.Application.Json)
            }
        }
    }

    //------------------------------
    // searches the whole IGDB catalogue by title. the server answers with the games themselves,
    // so anything IGDB knows about can be found, not only the games already cached on this device
    suspend fun searchGames(query: String, limit: Int): List<GameDto> {
        return await {
            httpClient.get("$baseUrl/games/search") {
                accept(ContentType.Application.Json)
                parameter("q", query)
                parameter("limit", limit)
            }
        }
    }

    //------------------------------
    // the games behind one of the "Browse by genre" chips, most talked about first.
    // category is one of the keys the server knows: action, rpg, puzzle, coop, strategy
    suspend fun browseGames(category: String, limit: Int): List<GameDto> {
        return await {
            httpClient.get("$baseUrl/games/search") {
                accept(ContentType.Application.Json)
                parameter("category", category)
                parameter("limit", limit)
            }
        }
    }

    //------------------------------
    // runs one request under the shared timeout and turns a non 2xx answer into an error the
    // repositories above can report, rather than letting the screen wait out the engine default
    private suspend inline fun <reified T> await(crossinline request: suspend () -> HttpResponse): T {
        val body = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            val response = request()
            if (!response.status.isSuccess()) {
                val errorText = runCatching { response.bodyAsText() }.getOrDefault("")
                throw IllegalStateException("Server returned HTTP ${response.status.value}: ${errorText.take(200)}")
            }
            response.body<T>()
        }
        return body ?: throw IOException("The server did not answer within ${REQUEST_TIMEOUT_MS / 1000} seconds")
    }
}
//------------------------------EOF------------------------------\
