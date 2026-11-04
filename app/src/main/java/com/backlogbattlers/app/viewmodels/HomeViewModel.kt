package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.data.repository.SettingsRepository
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.domain.model.Recommendation
import com.backlogbattlers.app.util.primaryPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class HomeUiState {

    data object Loading : HomeUiState()

    data class Loaded(val recommendations: List<Recommendation>) : HomeUiState()

    data object Error : HomeUiState()
}

class HomeViewModel(
    private val gameRepository: GameRepository = ServiceLocator.gameRepository,
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // the ids of the games already in the signed-in user's library, so a suggestion they own
    // shows a tick instead of the add button
    private val _ownedGameIds = MutableStateFlow<Set<Int>>(emptySet())
    val ownedGameIds: StateFlow<Set<Int>> = _ownedGameIds.asStateFlow()

    // the title of each game added from a suggestion, for the "added to your backlog" message
    private val _addedGames = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val addedGames: SharedFlow<String> = _addedGames.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadStarterGames()
        observeLibrary()
    }

    fun loadStarterGames() {
        if (loadJob?.isActive == true) return

        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                _uiState.value = HomeUiState.Loaded(gameRepository.getStarterGames())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading the starter games failed", e)
                _uiState.value = HomeUiState.Error
            }
        }
    }

    //------------------------------
    // adds a suggested game to the backlog on the user's default platform (or the game's main one
    // when it isn't on that platform), the same way search does. a game already owned is left alone
    fun addToLibrary(game: Game) {
        if (game.gameId in _ownedGameIds.value) return

        viewModelScope.launch {
            try {
                libraryRepository.addToLibrary(
                    gameId = game.gameId,
                    platform = platformOf(game),
                    status = LibraryStatus.BACKLOG,
                )
                Log.d(TAG, "Added ${game.title} to the library from the home suggestions")
                _addedGames.tryEmit(game.title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Adding ${game.title} to the library failed", e)
            }
        }
        // so its achievements are already cached, with a real total, by the time the games tab shows it
        viewModelScope.launch {
            try {
                gameRepository.getAchievements(game.gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Caching achievements for ${game.title} failed", e)
            }
        }
    }

    //------------------------------
    // keeps ownedGameIds in step with the library, so a tick appears the moment a game is added
    private fun observeLibrary() {
        viewModelScope.launch {
            libraryRepository.observeLibrary().collect { entries ->
                _ownedGameIds.value = entries.map { it.gameId }.toSet()
            }
        }
    }

    private suspend fun platformOf(game: Game): Platform {
        val preferred = settingsRepository.observeSettings().first().defaultPlatform
        return primaryPlatform(game.platforms, preferred) ?: Platform.OTHER
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
//------------------------------EOF------------------------------\\
