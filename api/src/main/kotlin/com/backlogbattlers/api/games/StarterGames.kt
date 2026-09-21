package com.backlogbattlers.api.games

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** A game together with the line the app shows under its name. */
@Serializable
data class RecommendationDto(
    val game: GameDto,
    val reason: String,
)

private data class StarterPick(val gameId: Int, val reason: String)

private val STARTER_PICKS = listOf(
    StarterPick(76882, "Highly rated by the community"), // Sekiro: Shadows Die Twice
    StarterPick(7344, "A great first pick for your backlog"), // Ori and the Blind Forest
    StarterPick(113112, "Trending with new players"), // Hades
)

private val CACHE_LIFETIME: Duration = Duration.ofHours(6)


class StarterGames(
    private val igdb: IgdbClient,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Mutex()
    private var cached: List<RecommendationDto>? = null
    private var cachedAt: Instant = Instant.MIN

    suspend fun load(): List<RecommendationDto> = lock.withLock {
        val stored = cached
        if (stored != null && clock().isBefore(cachedAt.plus(CACHE_LIFETIME))) return stored

        val fresh = STARTER_PICKS.mapNotNull { pick ->
            val game = igdb.game(pick.gameId) ?: return@mapNotNull null
            val artwork = steamArtUrl(igdb.steamAppId(pick.gameId))
            RecommendationDto(game.toDto(artworkUrl = artwork), pick.reason)
        }
        cached = fresh
        cachedAt = clock()
        fresh
    }
}
