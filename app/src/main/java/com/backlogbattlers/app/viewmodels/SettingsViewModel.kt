package com.backlogbattlers.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.domain.model.HomeBackdrop
import com.backlogbattlers.app.domain.model.ThemeMode
import com.backlogbattlers.app.domain.model.User
import com.backlogbattlers.app.domain.model.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// shared by settings and every screen under it, so a change made on one screen
// is already on the others when they open
class SettingsViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val authRepository = ServiceLocator.authRepository

    val settings: StateFlow<UserSettings> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = settingsRepository.getSettingsBlocking(),
        )

    val currentUser: StateFlow<User?> = authRepository.observeCurrentUser()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    //------------------------------
    // stores the light/dark choice. the caller applies it to the delegate,
    // which recreates the activity, so this write runs on the application
    // scope rather than one that the recreation could cut short
    fun setThemeMode(mode: ThemeMode) {
        ServiceLocator.applicationScope.launch { settingsRepository.setThemeMode(mode) }
    }

    //------------------------------
    // stores the artwork shown behind the home screen header
    fun setHomeBackdrop(backdrop: HomeBackdrop) {
        viewModelScope.launch { settingsRepository.setHomeBackdrop(backdrop) }
    }

    //------------------------------
    // notification preferences
    fun setAchievementNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAchievementNotificationsEnabled(enabled) }
    }

    fun setRankChangeNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRankChangeNotificationsEnabled(enabled) }
    }

    fun setSeasonResetNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSeasonResetNotificationsEnabled(enabled) }
    }

    fun setFriendActivityNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFriendActivityNotificationsEnabled(enabled) }
    }

    //------------------------------
    // biometric unlock preference
    fun setBiometricLogin(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLoginEnabled(enabled) }
    }

    //------------------------------
    // signs the user out and clears tokens
    fun logOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onDone()
        }
    }
}
//------------------------------EOF------------------------------\\
