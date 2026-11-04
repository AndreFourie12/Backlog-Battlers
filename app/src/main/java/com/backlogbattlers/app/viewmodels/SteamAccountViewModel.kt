package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.R
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.SteamAccountLoad
import com.backlogbattlers.app.data.repository.SteamAccountRepository
import com.backlogbattlers.app.data.repository.SteamAccountResult
import com.backlogbattlers.app.data.repository.isValidSteamId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// everything the Steam account screen needs to render
data class SteamAccountUiState(
    // the linked Steam ID, null when nothing is linked
    val linkedSteamId: String? = null,
    val isLoading: Boolean = true,
    // a link or unlink request is in flight
    val isSaving: Boolean = false,
    // the linked account could not be read, so the screen offers a retry instead of the form
    val loadFailed: Boolean = false,
    // a message from strings.xml for the screen to show once, never the raw exception text
    @StringRes val message: Int? = null,
)

// viewmodel for linking the Steam account that achievements are checked against
class SteamAccountViewModel(
    private val repository: SteamAccountRepository = ServiceLocator.steamAccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SteamAccountUiState())
    val uiState: StateFlow<SteamAccountUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    //------------------------------
    // reads the linked account from the server
    fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            when (val loaded = repository.load()) {
                is SteamAccountLoad.Loaded ->
                    _uiState.update { it.copy(isLoading = false, linkedSteamId = loaded.steamId) }
                is SteamAccountLoad.Failed -> {
                    Log.w(TAG, "Loading the Steam account failed: ${loaded.reason}")
                    _uiState.update { it.copy(isLoading = false, loadFailed = true, message = messageFor(loaded.reason)) }
                }
            }
        }
    }

    //------------------------------
    // links the typed Steam ID. a malformed one is refused here without calling the server
    fun link(typed: String) {
        val steamId = typed.trim()
        if (!isValidSteamId(steamId)) {
            _uiState.update { it.copy(message = R.string.steam_account_invalid) }
            return
        }
        if (_uiState.value.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.link(steamId)
            Log.i(TAG, "Linking the Steam account: $result")
            _uiState.update {
                if (result == SteamAccountResult.DONE) {
                    it.copy(isSaving = false, linkedSteamId = steamId, message = R.string.steam_account_linked)
                } else {
                    it.copy(isSaving = false, message = messageFor(result))
                }
            }
        }
    }

    //------------------------------
    // removes the link
    fun unlink() {
        if (_uiState.value.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.unlink()
            Log.i(TAG, "Unlinking the Steam account: $result")
            _uiState.update {
                if (result == SteamAccountResult.DONE) {
                    it.copy(isSaving = false, linkedSteamId = null, message = R.string.steam_account_unlinked)
                } else {
                    it.copy(isSaving = false, message = messageFor(result))
                }
            }
        }
    }

    //------------------------------
    // called once the screen has shown the message, so it isn't shown again on rotation
    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    @StringRes
    private fun messageFor(result: SteamAccountResult): Int = when (result) {
        SteamAccountResult.TAKEN -> R.string.steam_account_taken
        SteamAccountResult.SIGN_IN_AGAIN -> R.string.steam_account_sign_in_again
        SteamAccountResult.NO_CONNECTION -> R.string.steam_account_no_connection
        SteamAccountResult.DONE, SteamAccountResult.FAILED -> R.string.steam_account_failed
    }

    private companion object {
        const val TAG = "SteamAccountViewModel"
    }
}
//------------------------------EOF------------------------------\\
