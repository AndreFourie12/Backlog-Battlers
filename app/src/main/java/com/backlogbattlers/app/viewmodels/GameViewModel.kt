package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.LibraryEntry
import com.backlogbattlers.app.domain.model.LibraryGame
import com.backlogbattlers.app.domain.model.LibrarySort
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.util.primaryPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GamesUiState(
    // how many games are in the library, null until the library has been read so the screen
    // never flashes the empty state at someone whose library has games
    val libraryCount: Int? = null,
    val ownedGameIds: Set<Int> = emptySet(),
    // the popular games offered to someone who has none yet
    val popular: GameListState = GameListState.Loading,
    // every game in the library, unfiltered; visibleLibraryGames is what the list actually shows
    val libraryGames: List<LibraryGame> = emptyList(),
    val libraryQuery: String = "",
    // null means the "All" chip: every status is shown
    val statusFilter: LibraryStatus? = null,
    val librarySort: LibrarySort = LibrarySort.RECENTLY_ADDED,
) {

    val visibleLibraryGames: List<LibraryGame>
        get() {
            val query = libraryQuery.trim()
            val filtered = libraryGames.filter { libraryGame ->
                (statusFilter == null || libraryGame.entry.status == statusFilter) &&
                    (query.isEmpty() || libraryGame.game.title.contains(query, ignoreCase = true))
            }
            return when (librarySort) {
                LibrarySort.RECENTLY_ADDED -> filtered.sortedByDescending { it.entry.addedAt }
                LibrarySort.NAME_A_Z -> filtered.sortedBy { it.game.title.lowercase() }
                LibrarySort.NAME_Z_A -> filtered.sortedByDescending { it.game.title.lowercase() }
            }
        }
}

// the games tab, the user's library
class GameViewModel(
    private val gameRepository: GameRepository = ServiceLocator.gameRepository,
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    // the title of each game added from this screen, for the confirmation message
    private val _addedGames = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val addedGames: SharedFlow<String> = _addedGames.asSharedFlow()

    private var popularJob: Job? = null
    private var libraryGamesJob: Job? = null

    init {
        observeLibrary()
        loadPopular()
    }

    fun onLibraryQueryChanged(query: String) {
        _uiState.update { it.copy(libraryQuery = query) }
    }

    fun setStatusFilter(status: LibraryStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
    }

    fun setLibrarySort(sort: LibrarySort) {
        _uiState.update { it.copy(librarySort = sort) }
    }

    // loads the popular games offered under an empty library, not the ones the home screen starts people off with
    fun loadPopular() {
        if (popularJob?.isActive == true) return

        _uiState.update { it.copy(popular = GameListState.Loading) }
        popularJob = viewModelScope.launch {
            val state = try {
                GameListState.Loaded(gameRepository.getPopularGames())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading the popular games failed", e)
                GameListState.Error
            }
            _uiState.update { it.copy(popular = state) }
        }
    }

    // puts the game in the library as something still to play. A game already in it is left alone
    fun addToLibrary(game: Game) {
        if (game.gameId in _uiState.value.ownedGameIds) return

        viewModelScope.launch {
            try {
                libraryRepository.addToLibrary(
                    gameId = game.gameId,
                    platform = primaryPlatform(game.platforms) ?: Platform.OTHER,
                    status = LibraryStatus.BACKLOG,
                )
                _addedGames.tryEmit(game.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Adding ${game.title} to the library failed", e)
            }
        }
        // so its achievements (and a real completion percent) are ready as soon as it shows up
        ensureAchievementsCached(game.gameId)
    }

    // fetches a game's achievements if they have never been fetched before, so the library grid's
    // completion pill has a real total to divide by instead of sitting at 0%. Fetching also
    // updates the cached game's total achievement count as a side effect (see GameRepository)
    private fun ensureAchievementsCached(gameId: Int) {
        viewModelScope.launch {
            try {
                gameRepository.getAchievements(gameId)
                val refreshed = gameRepository.getGame(gameId) ?: return@launch
                _uiState.update { state ->
                    state.copy(
                        libraryGames = state.libraryGames.map { libraryGame ->
                            if (libraryGame.game.gameId == gameId) libraryGame.copy(game = refreshed) else libraryGame
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Caching achievements for game $gameId failed", e)
            }
        }
    }

    // keeps the count and the owned games in step with the library, whichever screen changes it
    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect { entries ->
                val owned = entries.map { it.gameId }.toSet()
                _uiState.update { it.copy(libraryCount = owned.size, ownedGameIds = owned) }
                loadLibraryGames(entries)
            }
        }
    }

    // pairs each entry with its game from the cache, for the list on this screen. Nothing reaches
    // the library without having been shown, and cached, somewhere first, so this should never miss
    private fun loadLibraryGames(entries: List<LibraryEntry>) {
        libraryGamesJob?.cancel()
        if (entries.isEmpty()) {
            _uiState.update { it.copy(libraryGames = emptyList()) }
            return
        }
        libraryGamesJob = viewModelScope.launch {
            val games = entries.mapNotNull { entry ->
                gameRepository.getGame(entry.gameId)?.let { game -> LibraryGame(entry, game) }
            }
            _uiState.update { it.copy(libraryGames = games) }

            // games added before achievement totals were tracked, or whose first fetch never
            // finished, catch up here rather than sitting at 0% forever
            games.filter { it.game.totalAchievements == null }.forEach { ensureAchievementsCached(it.game.gameId) }
        }
    }

    private companion object {
        const val TAG = "GameViewModel"
    }
}
