package com.backlogbattlers.app.ui.login

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.AuthResult
import com.backlogbattlers.app.domain.model.User
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

    // error state containing error message
    data class Error(val message: String) : LoginUiState()
}

// viewmodel managing google sso state and repository interactions
class LoginViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
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
                }
                is AuthResult.Failure -> {
                    Log.e("LoginViewModel", "Sign-In failure: ${result.message}")
                    _uiState.value = LoginUiState.Error(result.message)
                }
            }
        }
    }
}
