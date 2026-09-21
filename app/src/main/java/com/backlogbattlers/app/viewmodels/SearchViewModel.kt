package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.data.repository.SearchHistoryRepository
import com.backlogbattlers.app.domain.model.BrowseCategory
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.domain.model.SearchSort
import com.backlogbattlers.app.util.primaryPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TYPING_PAUSE_MS = 320L

private const val SUGGESTION_LIMIT = 10

private const val RESULT_LIMIT = 50

enum class SearchMode {
    RESTING,

    TYPING,

    RESULTS,
}

sealed class GameListState {

    data object Loading : GameListState()

    data class Loaded(val games: List<Game>) : GameListState()

    data object Error : GameListState()
}

data class SearchFilters(
    val platform: String? = null,
    val genre: String? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    val isNarrowing: Boolean get() = platform != null || genre != null
}

data class SearchUiState(
    val mode: SearchMode = SearchMode.RESTING,
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val trending: List<Game> = emptyList(),
    val suggestions: GameListState = GameListState.Loading,
    val results: GameListState = GameListState.Loading,
    val filters: SearchFilters = SearchFilters(),
    val ownedGameIds: Set<Int> = emptySet(),
    val browsedCategory: BrowseCategory? = null,
) {

    val visibleResults: List<Game>
        get() {
            val kept = loadedResults.filter { game ->
                (filters.platform == null || filters.platform in game.platforms) &&
                    (filters.genre == null || filters.genre in game.genres)
            }
            return when (filters.sort) {
                SearchSort.RELEVANCE -> kept
                SearchSort.NAME_A_Z -> kept.sortedBy { it.title.lowercase() }
                SearchSort.NAME_Z_A -> kept.sortedByDescending { it.title.lowercase() }
            }
        }

    val platformOptions: List<String>
        get() = loadedResults.flatMap { it.platforms }.distinct().sorted()

    val genreOptions: List<String>
        get() = loadedResults.flatMap { it.genres }.distinct().sorted()

    private val loadedResults: List<Game>
        get() = (results as? GameListState.Loaded)?.games.orEmpty()
}

class SearchViewModel(
    private val gameRepository: GameRepository = ServiceLocator.gameRepository,
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
    private val searchHistoryRepository: SearchHistoryRepository = ServiceLocator.searchHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _addedGames = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val addedGames: SharedFlow<String> = _addedGames.asSharedFlow()

    private var suggestJob: Job? = null

    private var resultsJob: Job? = null

    init {
        observeRecentSearches()
        observeLibrary()
        loadTrending()
    }

    fun onQueryChanged(text: String) {
        val query = text.trim()
        val current = _uiState.value
        if (query == current.query && current.mode == SearchMode.TYPING) return

        suggestJob?.cancel()

        if (query.isEmpty()) {
            resultsJob?.cancel()
            _uiState.update { it.copy(mode = SearchMode.RESTING, query = "", browsedCategory = null) }
            return
        }

        _uiState.update {
            it.copy(mode = SearchMode.TYPING, query = query, suggestions = GameListState.Loading)
        }

        suggestJob = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            val state = fetch { gameRepository.searchOnline(query, SUGGESTION_LIMIT) }
            _uiState.update { if (it.query == query) it.copy(suggestions = state) else it }
        }
    }

    fun runSearch(query: String = _uiState.value.query) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return

        suggestJob?.cancel()
        viewModelScope.launch { searchHistoryRepository.recordSearch(cleaned) }
        loadResults(query = cleaned, category = null)
    }

    fun browse(category: BrowseCategory) {
        suggestJob?.cancel()
        loadResults(query = "", category = category)
    }

    fun retry() {
        val state = _uiState.value
        loadResults(query = state.query, category = state.browsedCategory)
    }

    fun clearQuery() {
        suggestJob?.cancel()
        resultsJob?.cancel()
        _uiState.update {
            it.copy(
                mode = SearchMode.RESTING,
                query = "",
                browsedCategory = null,
                filters = SearchFilters(),
            )
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { searchHistoryRepository.removeSearch(query) }
    }

    fun setPlatformFilter(platform: String?) {
        _uiState.update { it.copy(filters = it.filters.copy(platform = platform)) }
    }

    fun setGenreFilter(genre: String?) {
        _uiState.update { it.copy(filters = it.filters.copy(genre = genre)) }
    }

    fun setSort(sort: SearchSort) {
        _uiState.update { it.copy(filters = it.filters.copy(sort = sort)) }
    }

    fun addToLibrary(game: Game) {
        if (game.gameId in _uiState.value.ownedGameIds) return

        viewModelScope.launch {
            try {
                libraryRepository.addToLibrary(
                    gameId = game.gameId,
                    platform = platformOf(game),
                    status = LibraryStatus.BACKLOG,
                )
                _addedGames.tryEmit(game.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Adding ${game.title} to the library failed", e)
            }
        }
    }

    private fun loadResults(query: String, category: BrowseCategory?) {
        if (category == null && query.isEmpty()) return

        resultsJob?.cancel()
        _uiState.update {
            it.copy(
                mode = SearchMode.RESULTS,
                query = query,
                results = GameListState.Loading,
                filters = SearchFilters(),
                browsedCategory = category,
            )
        }

        resultsJob = viewModelScope.launch {
            val state = fetch {
                if (category != null) {
                    gameRepository.browse(category.key, RESULT_LIMIT)
                } else {
                    gameRepository.searchOnline(query, RESULT_LIMIT)
                }
            }
            _uiState.update { it.copy(results = state) }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val games = gameRepository.getStarterGames().map { it.game }
                _uiState.update { it.copy(trending = games) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading the trending games failed", e)
            }
        }
    }

    private fun observeRecentSearches() {
        viewModelScope.launch {
            searchHistoryRepository.observeRecentSearches().collect { searches ->
                _uiState.update { it.copy(recentSearches = searches) }
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect { entries ->
                _uiState.update { state -> state.copy(ownedGameIds = entries.map { it.gameId }.toSet()) }
            }
        }
    }

    private suspend fun fetch(request: suspend () -> List<Game>): GameListState {
        return try {
            GameListState.Loaded(request())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "The game catalogue could not be reached", e)
            GameListState.Error
        }
    }

    private fun platformOf(game: Game): Platform = primaryPlatform(game.platforms) ?: Platform.OTHER

    private companion object {
        const val TAG = "SearchViewModel"
    }
}
