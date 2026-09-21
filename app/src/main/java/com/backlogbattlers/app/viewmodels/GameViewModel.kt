package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.domain.model.Game
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
)

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

    init {
        observeLibrary()
        loadPopular()
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
    }

    // keeps the count and the owned games in step with the library, whichever screen changes it
    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect { entries ->
                val owned = entries.map { it.gameId }.toSet()
                _uiState.update { it.copy(libraryCount = owned.size, ownedGameIds = owned) }
            }
        }
    }

    private companion object {
        const val TAG = "GameViewModel"
    }
}
