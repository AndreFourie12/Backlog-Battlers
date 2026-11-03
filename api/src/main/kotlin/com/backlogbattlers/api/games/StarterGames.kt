package com.backlogbattlers.api.games

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** A game together with the line the app shows under its name. */
@Serializable
data class RecommendationDto(
    val game: GameDto,
    val reason: String,
    // the game's place in Steam's most played chart (1 = most played), so the app can word the line in
    // the user's own language rather than showing [reason]. null for the hand picked games
    val steamRank: Int? = null,
)

private data class StarterPick(val gameId: Int, val reason: String)

// shown when Steam's chart can't be used (Steam or IGDB is down, or no Steam client is configured)
private val STARTER_PICKS = listOf(
    StarterPick(76882, "Highly rated by the community"), // Sekiro: Shadows Die Twice
    StarterPick(7344, "A great first pick for your backlog"), // Ori and the Blind Forest
    StarterPick(113112, "Trending with new players"), // Hades
)

// how many games the home screen shows, and how many of Steam's best ranked games they are picked from
private const val PICK_COUNT = 3
private const val POOL_SIZE = 20

private val CACHE_LIFETIME: Duration = Duration.ofHours(6)
// after Steam's chart fails, how long to use the hand picks before asking Steam again
private val RETRY_AFTER_FAILURE: Duration = Duration.ofMinutes(10)

/**
 * The games suggested on the home screen: [PICK_COUNT] picked at random, on every request, from the
 * [POOL_SIZE] best ranked games on Steam's most played chart that can be played alone. Steam's top 100
 * is mostly free-to-play multiplayer games (Counter-Strike, Dota), which make poor backlog picks, so
 * only games IGDB lists with a single player mode are kept. The pool is rebuilt every [CACHE_LIFETIME].
 * When Steam or IGDB can't be reached, or too few games are left, the hand picked [STARTER_PICKS] are
 * returned instead, so the home screen is never empty.
 */
class StarterGames(
    private val igdb: IgdbClient,
    // null leaves out Steam's chart entirely and always returns the hand picks
    private val steam: SteamClient? = null,
    // injected so tests get the same "random" picks every run
    private val random: Random = Random.Default,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Mutex()

    private var popular: List<RecommendationDto> = emptyList()
    private var popularAt: Instant = Instant.MIN
    private var popularLifetime: Duration = CACHE_LIFETIME

    private var handPicked: List<RecommendationDto>? = null
    private var handPickedAt: Instant = Instant.MIN

    suspend fun load(): List<RecommendationDto> = lock.withLock {
        val pool = popularPool()
        if (pool.size >= PICK_COUNT) {
            // shown best ranked first
            return pool.shuffled(random).take(PICK_COUNT).sortedBy { it.steamRank }
        }
        handPicks()
    }

    /** The cached pool of popular games, rebuilt from Steam and IGDB when it is too old. Empty when it can't be built. */
    private suspend fun popularPool(): List<RecommendationDto> {
        val steam = steam ?: return emptyList()
        if (clock().isBefore(popularAt.plus(popularLifetime))) return popular

        popular = try {
            buildPopularPool(steam)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Steam's most played games could not be loaded, suggesting the hand picked games: ${e.message}")
            emptyList()
        }
        popularAt = clock()
        popularLifetime = if (popular.size >= PICK_COUNT) CACHE_LIFETIME else RETRY_AFTER_FAILURE
        log.info("Built the popular starter games pool: ${popular.size} games from Steam's most played chart")
        return popular
    }

    /** Steam's most played chart, matched to IGDB games, narrowed to games that can be played alone. */
    private suspend fun buildPopularPool(steam: SteamClient): List<RecommendationDto> {
        // Steam lists them most played first, so a game's position is its rank
        val appIds = steam.mostPlayedAppIds()
        val gameIdByApp = igdb.gameIdsForSteamApps(appIds)
        val playableById = igdb.singlePlayerGames(gameIdByApp.values.toList()).associateBy { it.id }

        return appIds.withIndex()
            .mapNotNull { (index, appId) ->
                val game = gameIdByApp[appId]?.let { playableById[it] } ?: return@mapNotNull null
                val rank = index + 1
                RecommendationDto(
                    game = game.toDto(artworkUrl = steamArtUrl(appId)),
                    reason = "#$rank most played on Steam right now",
                    steamRank = rank,
                )
            }
            .distinctBy { it.game.gameId }
            .take(POOL_SIZE)
    }

    /** The hand picked games, cached like the pool. */
    private suspend fun handPicks(): List<RecommendationDto> {
        val stored = handPicked
        if (stored != null && clock().isBefore(handPickedAt.plus(CACHE_LIFETIME))) return stored

        val fresh = STARTER_PICKS.mapNotNull { pick ->
            val game = igdb.game(pick.gameId) ?: return@mapNotNull null
            val artwork = steamArtUrl(igdb.steamAppId(pick.gameId))
            RecommendationDto(game.toDto(artworkUrl = artwork), pick.reason)
        }
        handPicked = fresh
        handPickedAt = clock()
        return fresh
    }

    private companion object {
        val log = LoggerFactory.getLogger(StarterGames::class.java)
    }
}
//------------------------------EOF------------------------------\\
