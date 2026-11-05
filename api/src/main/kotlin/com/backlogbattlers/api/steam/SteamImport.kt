package com.backlogbattlers.api.steam

import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.games.GameDto
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.OwnedGames
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.games.saveGame
import com.backlogbattlers.api.games.toDto
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

/** The answer to `POST /users/me/steam/import`. */
@Serializable
data class SteamImportDto(
    /** Games in the Steam library. */
    val ownedOnSteam: Int,
    /** Games added to the library, on PC, in the backlog. */
    val added: Int,
    /** PC entries whose hours played went up to Steam's figure. */
    val hoursUpdated: Int,
    /** Games already in the library that needed no change. */
    val unchanged: Int,
    /** Steam apps the game catalogue has no game for (tools, soundtracks, demos), so they were skipped. */
    val notInCatalogue: Int,
    /** The games just added, so the app can show them without asking for each one. */
    val games: List<GameDto>,
)

sealed interface SteamImportResult {
    data class Imported(val result: SteamImportDto) : SteamImportResult

    /** The user has not linked a Steam account. */
    data object NotLinked : SteamImportResult

    /** The Steam profile, or just its game details, is not public. */
    data object PrivateProfile : SteamImportResult

    /** The user imported moments ago; Steam is not asked again until [retryAfterSeconds] have passed. */
    data class TooSoon(val retryAfterSeconds: Long) : SteamImportResult
}

/**
 * Brings the games a user owns on Steam into their library, with Steam's hours played.
 *
 * Built to spend as few external calls as possible:
 *  - Steam: one call, whatever the size of the library (GetOwnedGames lists everything at once).
 *  - IGDB: which game each Steam app is comes from the shared SteamApps cache; only apps nobody has
 *    imported before are looked up, 250 per request. Details and time to beat are fetched only for
 *    games new to this library, 500 per request. Importing again with no new games costs no IGDB call.
 *  - A user can import at most once per [cooldown], so tapping the button repeatedly costs nothing.
 *
 * New games go in as PC entries in the backlog. A game already in the library on PC gets its hours
 * raised to Steam's figure (never lowered, so hours logged by hand are kept); one on another
 * platform is left alone.
 */
class SteamImporter(
    private val igdb: IgdbClient,
    private val steam: SteamClient,
    private val clock: () -> Instant = Instant::now,
    private val cooldown: Duration = DEFAULT_COOLDOWN,
) {
    // when each user last imported. per server process, which is enough to stop repeated taps
    private val lastImport = ConcurrentHashMap<Uuid, Instant>()

    suspend fun import(owner: Uuid): SteamImportResult {
        val now = clock()
        val last = lastImport[owner]
        if (last != null) {
            val wait = Duration.between(now, last.plus(cooldown))
            if (wait > Duration.ZERO) return SteamImportResult.TooSoon(wait.toSeconds() + 1)
        }

        val steamId = linkedSteamId(owner) ?: return SteamImportResult.NotLinked
        // claimed before calling out, so two taps at once cannot both import; released on any failure
        // so the user can retry straight away once they fix their privacy setting or Steam is back
        lastImport[owner] = now
        return try {
            when (val owned = steam.ownedGames(steamId)) {
                OwnedGames.PrivateProfile -> {
                    lastImport.remove(owner)
                    SteamImportResult.PrivateProfile
                }
                is OwnedGames.Owned -> SteamImportResult.Imported(addToLibrary(owner, owned, now))
            }
        } catch (e: Exception) {
            lastImport.remove(owner)
            throw e
        }
    }

    private suspend fun addToLibrary(owner: Uuid, owned: OwnedGames.Owned, now: Instant): SteamImportDto {
        val gameByApp = resolveSteamApps(owned.games.map { it.appId }, igdb, now)

        // when several Steam apps are the same game (editions, a remaster), the most played one counts
        val minutesByGame = owned.games
            .mapNotNull { game -> gameByApp[game.appId]?.let { it to game.minutesPlayed } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, minutes) -> minutes.max() }
        val notInCatalogue = owned.games.map { it.appId }.distinct().count { it !in gameByApp }

        val existing = transaction {
            if (minutesByGame.isEmpty()) emptyList()
            else LibraryEntries.selectAll()
                .where { (LibraryEntries.userId eq owner) and (LibraryEntries.gameId inList minutesByGame.keys) }
                .map { Triple(it[LibraryEntries.id], it[LibraryEntries.gameId], it[LibraryEntries.platform] to it[LibraryEntries.hoursPlayed]) }
        }
        val gamesInLibrary = existing.map { it.second }.toSet()
        val newGameIds = minutesByGame.keys.filter { it !in gamesInLibrary }

        // details only for the games new to this library: two batched IGDB requests per 500 games
        val details = igdb.games(newGameIds)
        val timeToBeat = igdb.timeToBeats(details.map { it.id })
        val appByGame = gameByApp.entries.groupBy({ it.value }, { it.key }).mapValues { (_, apps) -> apps.min() }
        val newGames = details.map { it.toDto(timeToBeat[it.id], steamAppId = appByGame[it.id]) }

        var hoursUpdated = 0
        transaction {
            newGames.forEach { game ->
                saveGame(game)
                LibraryEntries.insert {
                    it[userId] = owner
                    it[gameId] = game.gameId
                    it[platform] = Platform.PC
                    it[status] = LibraryStatus.BACKLOG
                    it[hoursPlayed] = hours(minutesByGame.getValue(game.gameId))
                }
            }
            existing.forEach { (entryId, gameId, platformAndHours) ->
                val (platform, storedHours) = platformAndHours
                val steamHours = hours(minutesByGame.getValue(gameId))
                if (platform == Platform.PC && steamHours > storedHours) {
                    LibraryEntries.update({ LibraryEntries.id eq entryId }) {
                        it[hoursPlayed] = steamHours
                        it[updatedAt] = now
                    }
                    hoursUpdated++
                }
            }
        }

        return SteamImportDto(
            ownedOnSteam = owned.games.size,
            added = newGames.size,
            hoursUpdated = hoursUpdated,
            unchanged = gamesInLibrary.size - hoursUpdated,
            notInCatalogue = notInCatalogue,
            games = newGames,
        )
    }

    companion object {
        val DEFAULT_COOLDOWN: Duration = Duration.ofMinutes(2)

        /** Steam counts minutes; the library keeps hours to one decimal place. */
        fun hours(minutes: Int): Double = (minutes / 6.0).roundToLong() / 10.0
    }
}
//------------------------------EOF------------------------------\\
