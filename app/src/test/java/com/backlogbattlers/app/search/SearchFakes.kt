package com.backlogbattlers.app.search

import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.data.repository.SearchHistoryRepository
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
) = Game(
    gameId = id,
    title = title,
    coverImageUrl = "https://images.igdb.com/cover/$id.jpg",
    artworkUrl = "https://shared.akamai.steamstatic.com/$id/capsule_616x353.jpg",
    platforms = platforms,
    genres = genres,
    avgCompletionHours = null,
    avg100PercentHours = null,
    cachedAt = 0L,
)

class FakeGameRepository : GameRepository {

    var searchResults: List<Game> = emptyList()
    var browseResults: List<Game> = emptyList()
    var popularGames: List<Game> = emptyList()

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

    override suspend fun getGame(gameId: Int): Game? = null

    override suspend fun getStarterGames(): List<Recommendation> =
        browseResults.map { Recommendation(it, "Trending with new players") }

    override suspend fun getPopularGames(): List<Game> = popularGames
}

class FakeLibraryRepository : LibraryRepository {

    private val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override fun observeLibrary(): Flow<List<LibraryEntry>> = entries

    override fun observeByStatus(status: LibraryStatus): Flow<List<LibraryEntry>> =
        entries.map { list -> list.filter { it.status == status } }

    override suspend fun addToLibrary(gameId: Int, platform: Platform, status: LibraryStatus) {
        entries.value = entries.value + LibraryEntry(
            libraryEntryId = UUID.randomUUID().toString(),
            gameId = gameId,
            platform = platform,
            status = status,
            hoursPlayed = 0f,
            unlockedAchievementIds = emptyList(),
            completionType = null as CompletionType?,
            addedAt = 0L,
            updatedAt = 0L,
        )
    }

    override suspend fun updateStatus(libraryEntryId: String, status: LibraryStatus) = Unit

    override suspend fun updateHoursPlayed(libraryEntryId: String, hours: Float) = Unit

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
