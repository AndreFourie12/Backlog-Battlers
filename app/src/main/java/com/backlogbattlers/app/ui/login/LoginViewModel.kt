package com.backlogbattlers.app.ui.login

import android.app.Activity
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.AuthResult
import com.backlogbattlers.app.data.repository.SettingsRepository
import com.backlogbattlers.app.domain.model.User
import com.backlogbattlers.app.util.BiometricAvailabilityChecker
import com.backlogbattlers.app.util.shouldOfferBiometricUnlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()

    // loading state while sign in request is in progress
    data object Loading : LoginUiState()

    // success state containing authenticated User
    data class Success(val user: User) : LoginUiState()

    // error state, with a message from strings.xml to show
    data class Error(@StringRes val messageRes: Int) : LoginUiState()
}

// viewmodel managing google sso state and repository interactions
class LoginViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository,
    private val biometricChecker: BiometricAvailabilityChecker = ServiceLocator.biometricAvailabilityChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkExistingSession()
    }

    //------------------------------
    // checks if a valid user session already exists and auto logs in
    fun checkExistingSession() {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser()
            if (currentUser != null) {
                _uiState.value = LoginUiState.Success(currentUser)
            }
        }
    }

    //------------------------------
    // initiates google sign in flow via AuthRepository and updates ui state with result
    fun onSignInClicked(activity: Activity) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(activity)) {
                is AuthResult.Success -> {
                    _uiState.value = LoginUiState.Success(result.user)
                    // brings down the settings saved to this account, e.g. from another phone.
                    // runs past this screen, which closes as the user is taken home
                    ServiceLocator.applicationScope.launch { ServiceLocator.settingsSync.sync() }
                }
                is AuthResult.Failure -> {
                    Log.e("LoginViewModel", "Sign-In failure, showing message ${result.messageRes}")
                    _uiState.value = LoginUiState.Error(result.messageRes)
                }
            }
        }
    }

    //------------------------------
    // whether to ask "unlock with your fingerprint next time?" before going home: once per phone,
    // only when it isn't on yet and the phone has a fingerprint, face or screen lock to use
    suspend fun shouldAskAboutBiometricUnlock(): Boolean {
        return shouldOfferBiometricUnlock(
            biometricEnabled = settingsRepository.observeSettings().first().biometricLoginEnabled,
            alreadyOffered = settingsRepository.hasOfferedBiometricUnlock(),
            availability = biometricChecker.check(),
        )
    }

    //------------------------------
    // stores the answer (DataStore, this phone only) so the question is never asked again here
    suspend fun answerBiometricOffer(accepted: Boolean) {
        if (accepted) settingsRepository.setBiometricLoginEnabled(true)
        settingsRepository.setBiometricUnlockOffered()
    }
}
