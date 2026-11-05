package com.backlogbattlers.api.games

import com.backlogbattlers.api.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** Thrown when Steam cannot be reached or rejects a request. */
class SteamException(message: String) : RuntimeException(message)

/** What Steam's schema says about one achievement. Icon urls are absolute and need no key to load. */
data class SteamAchievementInfo(
    val displayName: String,
    val description: String?,
    val iconUrl: String?,
    val iconGrayUrl: String?,
)

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
    // absolute urls to the unlocked and locked (greyed out) icons, 64x64
    val icon: String? = null,
    val icongray: String? = null,
)

@Serializable
private data class PlayerStatsResponse(val playerstats: PlayerStats = PlayerStats())

@Serializable
private data class PlayerStats(
    val error: String? = null,
    val achievements: List<PlayerAchievementRow> = emptyList(),
)

// `achieved` is 1 once the player has unlocked it, 0 otherwise
@Serializable
private data class PlayerAchievementRow(val apiname: String, val achieved: Int = 0)

/** What Steam says one player has unlocked in one game. */
sealed interface PlayerAchievements {
    /** The codes of the achievements the player has unlocked. Empty when they have none, or do not own the game. */
    data class Unlocked(val codes: Set<String>) : PlayerAchievements

    /** The player's Steam profile (or its game details) is private, so Steam will not say. */
    data object PrivateProfile : PlayerAchievements
}

@Serializable
private data class MostPlayedResponse(val response: MostPlayedList = MostPlayedList())

@Serializable
private data class MostPlayedList(val ranks: List<MostPlayedRank> = emptyList())

@Serializable
private data class MostPlayedRank(val rank: Int, val appid: Int)


@Serializable
private data class OwnedGamesResponse(val response: OwnedGamesList = OwnedGamesList())

// Steam leaves game_count and games out entirely when the profile or its game details are private
@Serializable
private data class OwnedGamesList(
    @SerialName("game_count") val gameCount: Int? = null,
    val games: List<OwnedGameRow>? = null,
)

@Serializable
private data class OwnedGameRow(
    val appid: Int,
    /** Minutes played, ever. */
    @SerialName("playtime_forever") val playtimeForever: Int = 0,
)

/** One game a player owns on Steam, and how long they have played it. */
data class OwnedGame(val appId: Int, val minutesPlayed: Int)

/** The answer to [SteamClient.ownedGames]. */
sealed interface OwnedGames {
    data class Owned(val games: List<OwnedGame>) : OwnedGames

    /** The profile, or just its game details, is not public, so Steam will not list the games. */
    data object PrivateProfile : OwnedGames
}

/**
 * Reads achievement data and the most played games from Steam.
 *
 * Global rarity and the most played chart need no key. Real names and descriptions need a free
 * Steam Web API key; without one, [displayNames] returns null and callers fall back to Steam's
 * internal codes.
 *
 * Sources followed:
 *  - Steam Web API, ISteamUserStats/GetGlobalAchievementPercentagesForApp and GetSchemaForGame:
 *    https://partner.steamgames.com/doc/webapi/ISteamUserStats
 *  - Steam Web API, ISteamChartsService/GetMostPlayedGames (the daily top 100 by peak players):
 *    https://steamapi.xpaw.me/#ISteamChartsService/GetMostPlayedGames
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
            .associate {
                it.name to SteamAchievementInfo(
                    displayName = it.displayName ?: it.name,
                    description = it.description,
                    iconUrl = it.icon,
                    iconGrayUrl = it.icongray,
                )
            }
    }

    /**
     * The achievements [steamId] (a 17 digit Steam ID) has really unlocked in [appId]. Needs the Steam key.
     * Steam answers 403 for a private profile; any other refusal (not owned, no stats) means nothing
     * is confirmed. A Steam outage is a [SteamException], never an empty answer, so it is not mistaken
     * for "nothing unlocked".
     */
    suspend fun unlockedAchievements(steamId: String, appId: Int): PlayerAchievements {
        if (apiKey.isBlank()) throw SteamException("STEAM_API_KEY is not set, so achievements cannot be verified")

        val response = http.get("$BASE_URL/GetPlayerAchievements/v1/") {
            parameter("key", apiKey)
            parameter("steamid", steamId)
            parameter("appid", appId)
        }
        val body = response.bodyAsText()
        val stats = runCatching { json.decodeFromString<PlayerStatsResponse>(body).playerstats }.getOrNull()

        if (response.status == HttpStatusCode.Forbidden || stats?.error?.contains("not public", ignoreCase = true) == true) {
            return PlayerAchievements.PrivateProfile
        }
        if (response.status == HttpStatusCode.BadRequest) return PlayerAchievements.Unlocked(emptySet())
        // The message never includes the request address, because that contains the key
        if (!response.status.isSuccess() || stats == null) throw SteamException("Steam player achievements request failed: ${response.status}")

        return PlayerAchievements.Unlocked(stats.achievements.filter { it.achieved == 1 }.map { it.apiname }.toSet())
    }

    /** Steam app ids of the most played games on Steam right now, most played first (Steam's top 100). */
    suspend fun mostPlayedAppIds(): List<Int> {
        val response = http.get(MOST_PLAYED_URL)
        if (!response.status.isSuccess()) throw SteamException("Steam most played request failed: ${response.status}")

        return json.decodeFromString<MostPlayedResponse>(response.bodyAsText())
            .response.ranks
            .sortedBy { it.rank }
            .map { it.appid }
            .distinct()
    }


    /**
     * Every game [steamId] owns, with minutes played, in one call (IPlayerService/GetOwnedGames).
     * Names and icons are not asked for (include_appinfo off): IGDB supplies those, and leaving
     * them out keeps the answer small even for a library of thousands. Free games the player has
     * played are included. Needs the Steam key.
     * https://partner.steamgames.com/doc/webapi/IPlayerService#GetOwnedGames
     */
    suspend fun ownedGames(steamId: String): OwnedGames {
        if (apiKey.isBlank()) throw SteamException("STEAM_API_KEY is not set, so Steam games cannot be imported")

        val response = http.get(OWNED_GAMES_URL) {
            parameter("key", apiKey)
            parameter("steamid", steamId)
            parameter("include_appinfo", false)
            parameter("include_played_free_games", true)
        }
        if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.Unauthorized) {
            return OwnedGames.PrivateProfile
        }
        // The message never includes the request address, because that contains the key
        if (!response.status.isSuccess()) throw SteamException("Steam owned games request failed: ${response.status}")

        val list = json.decodeFromString<OwnedGamesResponse>(response.bodyAsText()).response
        if (list.gameCount == null && list.games == null) return OwnedGames.PrivateProfile
        return OwnedGames.Owned(list.games.orEmpty().map { OwnedGame(it.appid, it.playtimeForever.coerceAtLeast(0)) })
    }

    companion object {
        private const val BASE_URL = "https://api.steampowered.com/ISteamUserStats"
        private const val MOST_PLAYED_URL = "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/"
        private const val OWNED_GAMES_URL = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/"

        /** Builds the real client from STEAM_API_KEY (see [Config]). The key is optional. */
        fun fromConfig(): SteamClient = SteamClient(
            http = HttpClient(CIO),
            apiKey = Config.default.get("STEAM_API_KEY").orEmpty(),
        )
    }
}
