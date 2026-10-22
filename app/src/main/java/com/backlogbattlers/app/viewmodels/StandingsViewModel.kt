package com.backlogbattlers.app.viewmodels

import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.LeaderboardRepository
import com.backlogbattlers.app.domain.model.LeaderboardEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// everything the leaderboard screen needs to render
data class StandingsUiState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val selfUserId: String? = null,
    val isLoading: Boolean = false,
    // a message from strings.xml for the screen to show once, never the raw exception text
    @StringRes val errorMessage: Int? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && entries.isEmpty()
}

// viewmodel for the monthly leaderboard screen
class StandingsViewModel(
    private val leaderboardRepository: LeaderboardRepository = ServiceLocator.leaderboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StandingsUiState())
    val uiState: StateFlow<StandingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    //------------------------------
    // loads this month's leaderboard, merging in the signed in user's own row when it
    // falls outside the returned page, so it can still be shown pinned at the bottom
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val board = leaderboardRepository.getMonthlyLeaderboard()
                val me = board.me
                val entries = if (me != null && board.entries.none { it.userId == me.userId }) {
                    board.entries + me
                } else {
                    board.entries
                }

                _uiState.update {
                    it.copy(isLoading = false, entries = entries, selfUserId = board.me?.userId)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = R.string.standings_error_load) }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
