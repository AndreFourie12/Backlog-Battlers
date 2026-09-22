package com.backlogbattlers.app.search

import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.data.repository.SearchHistoryRepository
import com.backlogbattlers.app.domain.model.Achievement
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.LibraryEntry
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.domain.model.Recommendation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

fun game(
    id: Int,
    title: String,
    platforms: List<String> = listOf("PC"),
    genres: List<String> = listOf("Adventure"),
    avgCompletionHours: Float? = null,
    totalAchievements: Int? = null,
) = Game(
    gameId = id,
    title = title,
    coverImageUrl = "https://images.igdb.com/cover/$id.jpg",
    artworkUrl = "https://shared.akamai.steamstatic.com/$id/capsule_616x353.jpg",
    platforms = platforms,
    genres = genres,
    avgCompletionHours = avgCompletionHours,
    avg100PercentHours = null,
    cachedAt = 0L,
    totalAchievements = totalAchievements,
)

fun achievement(
    id: String,
    name: String = id,
    rarityPercent: Double? = 50.0,
) = Achievement(
    achievementId = id,
    name = name,
    description = "$name description",
    rarityPercent = rarityPercent,
)

class FakeGameRepository : GameRepository {

    var searchResults: List<Game> = emptyList()
    var browseResults: List<Game> = emptyList()
    var popularGames: List<Game> = emptyList()
    // stands in for the cached_games table: what GameViewModel's library list looks games up by id from
    var cachedGames: Map<Int, Game> = emptyMap()
    // what refreshGame returns, when it differs from what is already cached (e.g. a real time-to-beat)
    var refreshedGames: Map<Int, Game> = emptyMap()
    // stands in for the API's achievements endpoint, keyed by game id
    var achievementsByGame: Map<Int, List<Achievement>> = emptyMap()

    var failing = false

    val searches = mutableListOf<Pair<String, Int>>()
    val browses = mutableListOf<Pair<String, Int>>()

    override fun search(query: String): Flow<List<Game>> = MutableStateFlow(emptyList())

    override suspend fun searchOnline(query: String, limit: Int): List<Game> {
        searches += query to limit
        if (failing) throw IOException("no connection")
        return searchResults
    }

    override suspend fun browse(category: String, limit: Int): List<Game> {
        browses += category to limit
        if (failing) throw IOException("no connection")
        return browseResults
    }

    override suspend fun getGame(gameId: Int): Game? = cachedGames[gameId]

    override suspend fun refreshGame(gameId: Int): Game {
        if (failing) throw IOException("no connection")
        val fresh = refreshedGames[gameId] ?: cachedGames[gameId] ?: error("no game $gameId configured on this fake")
        cachedGames = cachedGames + (gameId to fresh)
        return fresh
    }

    override suspend fun getStarterGames(): List<Recommendation> =
        browseResults.map { Recommendation(it, "Trending with new players") }

    override suspend fun getPopularGames(): List<Game> = popularGames

    override suspend fun getAchievements(gameId: Int): List<Achievement> {
        if (failing) throw IOException("no connection")
        val achievements = achievementsByGame[gameId].orEmpty()
        cachedGames[gameId]?.let { cachedGames = cachedGames + (gameId to it.copy(totalAchievements = achievements.size)) }
        return achievements
    }
}

class FakeLibraryRepository : LibraryRepository {

    private val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override fun observeLibrary(): Flow<List<LibraryEntry>> = entries

    override fun observeByStatus(status: LibraryStatus): Flow<List<LibraryEntry>> =
        entries.map { list -> list.filter { it.status == status } }

    override fun observeByGameId(gameId: Int): Flow<LibraryEntry?> =
        entries.map { list -> list.firstOrNull { it.gameId == gameId } }

    override suspend fun addToLibrary(gameId: Int, platform: Platform, status: LibraryStatus) {
        // a stand-in clock: each entry added is one "tick" later than the last, so tests can
        // check the library list's default newest-first order without a real timestamp
        val addedAt = entries.value.size.toLong()
        entries.value = entries.value + LibraryEntry(
            libraryEntryId = UUID.randomUUID().toString(),
            gameId = gameId,
            platform = platform,
            status = status,
            hoursPlayed = 0f,
            unlockedAchievementIds = emptyList(),
            completionType = null as CompletionType?,
            addedAt = addedAt,
            updatedAt = addedAt,
        )
    }

    override suspend fun updateStatus(libraryEntryId: String, status: LibraryStatus) = Unit

    override suspend fun updateHoursPlayed(libraryEntryId: String, hours: Float) = Unit

    override suspend fun setAchievementUnlocked(libraryEntryId: String, achievementId: String, unlocked: Boolean) {
        val entry = entries.value.firstOrNull { it.libraryEntryId == libraryEntryId } ?: return
        val ids = if (unlocked) entry.unlockedAchievementIds + achievementId else entry.unlockedAchievementIds - achievementId
        entries.value = entries.value.map {
            if (it.libraryEntryId == libraryEntryId) it.copy(unlockedAchievementIds = ids) else it
        }
    }

    override suspend fun remove(libraryEntryId: String) {
        entries.value = entries.value.filterNot { it.libraryEntryId == libraryEntryId }
    }
}

class FakeSearchHistoryRepository : SearchHistoryRepository {

    private val searches = MutableStateFlow<List<String>>(emptyList())

    override fun observeRecentSearches(): Flow<List<String>> = searches

    override suspend fun recordSearch(query: String) {
        searches.value = listOf(query) + searches.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    override suspend fun removeSearch(query: String) {
        searches.value = searches.value.filterNot { it.equals(query, ignoreCase = true) }
    }
}
