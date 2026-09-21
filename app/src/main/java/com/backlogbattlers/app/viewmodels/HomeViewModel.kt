package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.domain.model.Recommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {

    data object Loading : HomeUiState()

    data class Loaded(val recommendations: List<Recommendation>) : HomeUiState()

    data object Error : HomeUiState()
}

class HomeViewModel(
    private val gameRepository: GameRepository = ServiceLocator.gameRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadStarterGames()
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
                Log.e("HomeViewModel", "Loading the starter games failed", e)
                _uiState.value = HomeUiState.Error
            }
        }
    }
}
//------------------------------EOF------------------------------\\