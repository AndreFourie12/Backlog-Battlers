package com.backlogbattlers.api.games

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** Thrown when Steam cannot be reached or rejects a request. */
class SteamException(message: String) : RuntimeException(message)

/** What Steam's schema says about one achievement. */
data class SteamAchievementInfo(val displayName: String, val description: String?)

// The parts of Steam's JSON that we read. Steam sends `percent` as text ("81.9"), so it is
// read as a primitive and converted, which also works if Steam ever switches to a number.
@Serializable
private data class RarityResponse(val achievementpercentages: RarityList = RarityList())

@Serializable
private data class RarityList(val achievements: List<RarityRow> = emptyList())

@Serializable
private data class RarityRow(val name: String, val percent: JsonPrimitive)

@Serializable
private data class SchemaResponse(val game: SchemaGame = SchemaGame())

@Serializable
private data class SchemaGame(val availableGameStats: SchemaStats? = null)

@Serializable
private data class SchemaStats(val achievements: List<SchemaAchievement> = emptyList())

@Serializable
private data class SchemaAchievement(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
)

/**
 * Reads achievement data from Steam.
 *
 * Global rarity needs no key. Real names and descriptions need a free Steam Web API key;
 * without one, [displayNames] returns null and callers fall back to Steam's internal codes.
 *
 * Sources followed:
 *  - Steam Web API, ISteamUserStats/GetGlobalAchievementPercentagesForApp and GetSchemaForGame:
 *    https://partner.steamgames.com/doc/webapi/ISteamUserStats
 */
class SteamClient(
    private val http: HttpClient,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Achievement code to the percentage of players who have unlocked it.
     * Empty when the app has no achievements (Steam answers 403 for those).
     */
    suspend fun globalRarity(appId: Int): Map<String, Double> {
        val response = http.get("$BASE_URL/GetGlobalAchievementPercentagesForApp/v2/") {
            parameter("gameid", appId)
        }
        if (response.status == HttpStatusCode.Forbidden) return emptyMap()
        if (!response.status.isSuccess()) throw SteamException("Steam rarity request failed: ${response.status}")

        return json.decodeFromString<RarityResponse>(response.bodyAsText())
            .achievementpercentages.achievements
            .mapNotNull { row -> row.percent.content.toDoubleOrNull()?.let { row.name to it } }
            .toMap()
    }

    /** Achievement code to its real name and description, or null when no Steam key is configured. */
    suspend fun displayNames(appId: Int): Map<String, SteamAchievementInfo>? {
        if (apiKey.isBlank()) return null

        val response = http.get("$BASE_URL/GetSchemaForGame/v2/") {
            parameter("key", apiKey)
            parameter("appid", appId)
        }
        // The message never includes the request address, because that contains the key
        if (!response.status.isSuccess()) throw SteamException("Steam schema request failed: ${response.status}")

        return json.decodeFromString<SchemaResponse>(response.bodyAsText())
            .game.availableGameStats?.achievements.orEmpty()
            .associate { it.name to SteamAchievementInfo(it.displayName ?: it.name, it.description) }
    }

    companion object {
        private const val BASE_URL = "https://api.steampowered.com/ISteamUserStats"

        /** Builds the real client from STEAM_API_KEY. The key is optional. */
        fun fromEnvironment(): SteamClient = SteamClient(
            http = HttpClient(CIO),
            apiKey = System.getenv("STEAM_API_KEY").orEmpty(),
        )
    }
}
