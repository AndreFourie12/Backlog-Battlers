package com.backlogbattlers.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// what the fingerprint lock screen is doing right now
sealed class BiometricLockUiState {

    // waiting to show prompt
    data object Idle : BiometricLockUiState()

    // biometric prompt is actively showing
    data object Authenticating : BiometricLockUiState()

    // biometric check succeeded
    data object Success : BiometricLockUiState()

    // scan failed or cancelled user was signed out
    data object SignedOut : BiometricLockUiState()
}

// viewmodel behind the fingerprint lock screen shown after a cold launch
class BiometricLockViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BiometricLockUiState>(BiometricLockUiState.Idle)
    val uiState: StateFlow<BiometricLockUiState> = _uiState.asStateFlow()

    //------------------------------
    // marks the prompt as showing called right before biometricPrompt
    fun onAuthenticationStarted() {
        _uiState.value = BiometricLockUiState.Authenticating
    }

    //------------------------------
    // the scan matched the caller can now navigate to home
    fun onAuthenticationSucceeded() {
        _uiState.value = BiometricLockUiState.Success
    }

    //------------------------------
    // the scan was cancelled or failed terminally sign the session out entirely and turn the biometric setting off
    fun onAuthenticationTerminated() {
        viewModelScope.launch {
            authRepository.signOut()
            settingsRepository.setBiometricLoginEnabled(false)
            _uiState.value = BiometricLockUiState.SignedOut
        }
    }
}
//------------------------------EOF------------------------------\\