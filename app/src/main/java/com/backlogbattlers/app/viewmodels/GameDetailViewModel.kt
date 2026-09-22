package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.data.repository.LibraryRepository
import com.backlogbattlers.app.domain.model.Achievement
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.util.achievementPoints
import com.backlogbattlers.app.util.completionPercent as computeCompletionPercent
import com.backlogbattlers.app.util.primaryPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// one of a game's achievements, paired with whether the signed in user has unlocked it
data class AchievementUiItem(
    val achievement: Achievement,
    val unlocked: Boolean,
) {
    val points: Int get() = achievementPoints(achievement.rarityPercent)
}

data class GameDetailUiState(
    val game: Game? = null,
    val achievements: List<AchievementUiItem> = emptyList(),
    // the game itself comes straight from the local cache; only the achievement list waits on
    // the network, so the header can show while this is still true
    val achievementsLoading: Boolean = true,
    val achievementsFailed: Boolean = false,
) {
    val unlockedCount: Int get() = achievements.count { it.unlocked }
    val totalCount: Int get() = achievements.size
    val earnedPoints: Int get() = achievements.filter { it.unlocked }.sumOf { it.points }
    val totalPoints: Int get() = achievements.sumOf { it.points }

    // whole achievements unlocked out of the whole list, not a points ratio
    val completionPercent: Int
        get() = computeCompletionPercent(unlockedCount, totalCount)
}

// the game detail screen: one game's header stats and its full achievement list, each one
// tappable to unlock, which earns its points and moves completion live. load() is idempotent
// per gameId so the fragment can call it from onViewCreated without refetching on rotation
class GameDetailViewModel(
    private val gameRepository: GameRepository = ServiceLocator.gameRepository,
    private val libraryRepository: LibraryRepository = ServiceLocator.libraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private var loadedGameId: Int? = null
    // the entry backing the currently unlocked achievements, once one exists for this game
    private var libraryEntryId: String? = null
    private var observeJob: Job? = null

    fun load(gameId: Int) {
        if (loadedGameId == gameId) return
        loadedGameId = gameId
        libraryEntryId = null
        _uiState.value = GameDetailUiState()

        viewModelScope.launch {
            // the cache lookup is local, so the header can show right away, ahead of the achievements
            _uiState.update { it.copy(game = gameRepository.getGame(gameId)) }

            // then refresh for the real IGDB time-to-beat, which search/starter/popular never carry
            try {
                val fresh = gameRepository.refreshGame(gameId)
                if (loadedGameId == gameId) _uiState.update { it.copy(game = fresh) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Refreshing game $gameId failed", e)
            }
        }
        fetchAchievements(gameId)
    }

    // retries just the achievement list after a failed load; the game header is already showing
    fun retryAchievements() {
        val gameId = loadedGameId ?: return
        if (!_uiState.value.achievementsFailed) return
        _uiState.update { it.copy(achievementsLoading = true, achievementsFailed = false) }
        fetchAchievements(gameId)
    }

    private fun fetchAchievements(gameId: Int) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val achievements = try {
                gameRepository.getAchievements(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading achievements for game $gameId failed", e)
                _uiState.update { it.copy(achievementsLoading = false, achievementsFailed = true) }
                return@launch
            }

            // stays subscribed for as long as this game is open, so an unlock (from here or
            // elsewhere) updates points and completion live
            libraryRepository.observeByGameId(gameId).collect { entry ->
                libraryEntryId = entry?.libraryEntryId
                val unlockedIds = entry?.unlockedAchievementIds.orEmpty().toSet()
                _uiState.update {
                    it.copy(
                        achievements = achievements.map { a ->
                            AchievementUiItem(a, unlocked = a.achievementId in unlockedIds)
                        }.sortedByDescending { item -> item.achievement.rarityPercent ?: 0.0 },
                        achievementsLoading = false,
                        achievementsFailed = false,
                    )
                }
            }
        }
    }

    // unlocks a locked achievement, or locks one back if tapped again. Tracking an achievement
    // implies tracking the game, so a game not yet in the library is added to it first
    fun toggleAchievement(achievementId: String) {
        val gameId = loadedGameId ?: return
        val item = _uiState.value.achievements.firstOrNull { it.achievement.achievementId == achievementId } ?: return
        val makeUnlocked = !item.unlocked

        viewModelScope.launch {
            try {
                val entryId = libraryEntryId ?: run {
                    val platform = _uiState.value.game?.let { primaryPlatform(it.platforms) } ?: Platform.OTHER
                    libraryRepository.addToLibrary(gameId, platform, LibraryStatus.BACKLOG)
                    libraryRepository.observeByGameId(gameId).first()?.libraryEntryId
                } ?: return@launch
                libraryEntryId = entryId
                libraryRepository.setAchievementUnlocked(entryId, achievementId, makeUnlocked)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Toggling $achievementId on game $gameId failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "GameDetailViewModel"
    }
}
//------------------------------EOF------------------------------\\
