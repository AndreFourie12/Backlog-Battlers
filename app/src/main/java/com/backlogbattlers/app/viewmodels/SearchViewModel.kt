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

// how long typing has to stop before the suggestions are fetched. without this every
// keystroke would be its own request, and IGDB only allows four a second
private const val TYPING_PAUSE_MS = 320L

// the suggestions are a shortcut, so only the first few are worth fetching
private const val SUGGESTION_LIMIT = 10

// a committed search fetches a full page, which is as many as the API returns at once
private const val RESULT_LIMIT = 50

//------------------------------
// which of the search screen's three faces is showing
enum class SearchMode {
    // nothing typed yet: recent searches, trending games and the genre chips
    RESTING,

    // still typing: the first few matches for what is in the box so far
    TYPING,

    // a search has been run: the filters, the count and the full results
    RESULTS,
}

//------------------------------
// where one of the screen's two game lists is up to
sealed class GameListState {

    data object Loading : GameListState()

    data class Loaded(val games: List<Game>) : GameListState()

    data object Error : GameListState()
}

//------------------------------
// what the three chips above the results are currently narrowing them to.
// null means the chip has not been used, so nothing is being ruled out
data class SearchFilters(
    val platform: String? = null,
    val genre: String? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    val isNarrowing: Boolean get() = platform != null || genre != null
}

//------------------------------
// everything the search screen draws itself from
data class SearchUiState(
    val mode: SearchMode = SearchMode.RESTING,
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val trending: List<Game> = emptyList(),
    val suggestions: GameListState = GameListState.Loading,
    val results: GameListState = GameListState.Loading,
    val filters: SearchFilters = SearchFilters(),
    // the games already in the backlog, so a result can show Owned instead of Add
    val ownedGameIds: Set<Int> = emptySet(),
    // set when a browse chip was tapped rather than a query typed, e.g. "Top RPG games"
    val browsedCategory: BrowseCategory? = null,
) {

    // the results after the platform and genre chips have had their say, in the chosen order
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

    // the platforms worth offering in the Platform chip: only the ones this search turned up
    val platformOptions: List<String>
        get() = loadedResults.flatMap { it.platforms }.distinct().sorted()

    // the genres worth offering in the Genre chip, likewise
    val genreOptions: List<String>
        get() = loadedResults.flatMap { it.genres }.distinct().sorted()

    private val loadedResults: List<Game>
        get() = (results as? GameListState.Loaded)?.games.orEmpty()
}

//------------------------------
// the search screen.
//
// searching goes to the API rather than to the games already stored on this device,
// so anything in the IGDB catalogue can be found. what comes back is cached on the
// way through, which is what lets the rest of the app open a result without asking again
class SearchViewModel(
    private val gameRepository: GameRepository = ServiceLocator.gameRepository,
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
    private val searchHistoryRepository: SearchHistoryRepository = ServiceLocator.searchHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // one-off messages, e.g. the confirmation after a game is added
    private val _addedGames = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val addedGames: SharedFlow<String> = _addedGames.asSharedFlow()

    // the pending suggestion fetch, cancelled whenever the query changes again
    private var suggestJob: Job? = null

    // the pending results fetch, cancelled when a different search is started
    private var resultsJob: Job? = null

    init {
        observeRecentSearches()
        observeLibrary()
        loadTrending()
    }

    //------------------------------
    // called on every keystroke. an empty box goes back to the resting screen, anything
    // else waits for typing to pause and then fetches the first few matches
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
            // a later keystroke may have moved the screen on while this was in flight
            _uiState.update { if (it.query == query) it.copy(suggestions = state) else it }
        }
    }

    //------------------------------
    // runs the full search for what is in the box, and remembers it under Recent
    fun runSearch(query: String = _uiState.value.query) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return

        suggestJob?.cancel()
        viewModelScope.launch { searchHistoryRepository.recordSearch(cleaned) }
        loadResults(query = cleaned, category = null)
    }

    //------------------------------
    // shows the games behind one of the "Browse by genre" chips
    fun browse(category: BrowseCategory) {
        suggestJob?.cancel()
        loadResults(query = "", category = category)
    }

    //------------------------------
    // runs the last search again, for the retry under an error
    fun retry() {
        val state = _uiState.value
        loadResults(query = state.query, category = state.browsedCategory)
    }

    //------------------------------
    // empties the box, which takes the screen back to the resting shortcuts
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

    //------------------------------
    // forgets one of the recent searches, for the x at the end of its row
    fun removeRecentSearch(query: String) {
        viewModelScope.launch { searchHistoryRepository.removeSearch(query) }
    }

    //------------------------------
    // narrows the results to one platform, or back to all of them when platform is null
    fun setPlatformFilter(platform: String?) {
        _uiState.update { it.copy(filters = it.filters.copy(platform = platform)) }
    }

    //------------------------------
    // narrows the results to one genre, or back to all of them when genre is null
    fun setGenreFilter(genre: String?) {
        _uiState.update { it.copy(filters = it.filters.copy(genre = genre)) }
    }

    //------------------------------
    // reorders the results
    fun setSort(sort: SearchSort) {
        _uiState.update { it.copy(filters = it.filters.copy(sort = sort)) }
    }

    //------------------------------
    // puts a game in the backlog. the Owned tick appears on its own, because the
    // library is being observed and the new entry comes straight back through it
    fun addToLibrary(game: Game) {
        if (game.gameId in _uiState.value.ownedGameIds) return

        viewModelScope.launch {
            try {
                libraryRepository.addToLibrary(
                    gameId = game.gameId,
                    platform = firstPlatformOf(game),
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

    //------------------------------
    // fetches a page of results, either for a typed query or for a browse category
    private fun loadResults(query: String, category: BrowseCategory?) {
        if (category == null && query.isEmpty()) return

        resultsJob?.cancel()
        _uiState.update {
            it.copy(
                mode = SearchMode.RESULTS,
                query = query,
                results = GameListState.Loading,
                // the chips are about the results they were opened over, so a new search drops them
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

    //------------------------------
    // the games the API puts forward for new players, shown under Trending
    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val games = gameRepository.getStarterGames().map { it.game }
                _uiState.update { it.copy(trending = games) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // trending is a shortcut, not the point of the screen, so a failure
                // leaves the section out rather than taking the whole screen over
                Log.e(TAG, "Loading the trending games failed", e)
            }
        }
    }

    //------------------------------
    // keeps the Recent list in step with what has been searched for
    private fun observeRecentSearches() {
        viewModelScope.launch {
            searchHistoryRepository.observeRecentSearches().collect { searches ->
                _uiState.update { it.copy(recentSearches = searches) }
            }
        }
    }

    //------------------------------
    // keeps the Owned ticks in step with the backlog
    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect { entries ->
                _uiState.update { state -> state.copy(ownedGameIds = entries.map { it.gameId }.toSet()) }
            }
        }
    }

    //------------------------------
    // runs one request and reports what came of it, so a dropped connection shows the
    // retry message instead of taking the screen down with it
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

    //------------------------------
    // a library entry is filed under one platform, so a game listed on several is
    // filed under the first the API gave, and one with none under Other
    private fun firstPlatformOf(game: Game): Platform {
        val name = game.platforms.firstOrNull() ?: return Platform.OTHER
        return runCatching { Platform.valueOf(name) }.getOrDefault(Platform.OTHER)
    }

    private companion object {
        const val TAG = "SearchViewModel"
    }
}
//------------------------------EOF------------------------------\
