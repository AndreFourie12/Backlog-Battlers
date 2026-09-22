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

@Serializable
data class AchievementDto(
    val achievementId: String,
    val name: String,
    val description: String? = null,
    val rarityPercent: Double? = null,
    val iconUrl: String? = null,
    val iconGrayUrl: String? = null,
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

    suspend fun searchGames(query: String, limit: Int): List<GameDto> {
        return await {
            httpClient.get("$baseUrl/games/search") {
                accept(ContentType.Application.Json)
                parameter("q", query)
                parameter("limit", limit)
            }
        }
    }

    suspend fun browseGames(category: String, limit: Int): List<GameDto> {
        return await {
            httpClient.get("$baseUrl/games/search") {
                accept(ContentType.Application.Json)
                parameter("category", category)
                parameter("limit", limit)
            }
        }
    }

    // GET /games/{id}: the one call that includes real IGDB time-to-beat, at the cost of one extra
    // IGDB request, so only the detail screen pays for it - search and browse skip it
    suspend fun getGame(gameId: Int): GameDto {
        return await {
            httpClient.get("$baseUrl/games/$gameId") {
                accept(ContentType.Application.Json)
            }
        }
    }

    suspend fun achievementsFor(gameId: Int): List<AchievementDto> {
        return await {
            httpClient.get("$baseUrl/games/$gameId/achievements") {
                accept(ContentType.Application.Json)
            }
        }
    }

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
//------------------------------EOF------------------------------\\
