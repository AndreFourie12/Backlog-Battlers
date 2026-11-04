package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.R
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.ProfileCustomizationRepository
import com.backlogbattlers.app.domain.model.ProfileCustomization
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// longest display name the field accepts
const val MAX_DISPLAY_NAME_LENGTH = 20

// the edit profile screen: what is saved now, and the draft being edited on top of it
data class EditProfileUiState(
    val loaded: Boolean = false,
    val signedIn: Boolean = false,
    val savedName: String = "",
    val savedCustomization: ProfileCustomization = ProfileCustomization(),
    val name: String = "",
    val customization: ProfileCustomization = ProfileCustomization(),
    val saving: Boolean = false,
) {
    // true once anything differs from what is saved, so leaving asks before throwing it away
    val hasChanges: Boolean get() = name.trim() != savedName || customization != savedCustomization
}

// how a save attempt ended, for the screen to show once
sealed class EditProfileEvent {
    data object Saved : EditProfileEvent()
    data class Rejected(@StringRes val message: Int) : EditProfileEvent()
}

// viewmodel behind the edit profile screen. nothing is stored until save(): the picture, the
// name and the section switches are a draft, so backing out leaves the profile as it was
class EditProfileViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val customizationRepository: ProfileCustomizationRepository = ServiceLocator.profileCustomizationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditProfileEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditProfileEvent> = _events.asSharedFlow()

    init {
        load()
    }

    //------------------------------
    // reads the signed-in user's name and saved setup once, as the starting draft
    private fun load() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            val customization = customizationRepository.observeCustomization().first()
            val name = user?.displayName.orEmpty()
            _uiState.value = EditProfileUiState(
                loaded = true,
                signedIn = user != null,
                savedName = name,
                savedCustomization = customization,
                name = name,
                customization = customization,
            )
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun choosePicture(pictureId: String) {
        Log.d(TAG, "Picture $pictureId chosen")
        updateCustomization { it.copy(pictureId = pictureId) }
    }

    fun removePicture() {
        Log.d(TAG, "Picture removed")
        updateCustomization { it.copy(pictureId = null) }
    }

    fun setShowBadges(show: Boolean) = updateCustomization { it.copy(showBadges = show) }

    fun setShowShowcase(show: Boolean) = updateCustomization { it.copy(showShowcase = show) }

    //------------------------------
    // checks the name, then stores the setup and, when it changed, the name
    fun save() {
        val state = _uiState.value
        if (state.saving) return
        val name = state.name.trim()

        val problem = when {
            !state.signedIn -> R.string.edit_profile_signed_out
            name.isEmpty() -> R.string.edit_profile_name_empty
            name.length > MAX_DISPLAY_NAME_LENGTH -> R.string.edit_profile_name_too_long
            else -> null
        }
        if (problem != null) {
            Log.d(TAG, "Not saving the profile: ${if (state.signedIn) "the name is not valid" else "nobody is signed in"}")
            _events.tryEmit(EditProfileEvent.Rejected(problem))
            return
        }

        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val nameSaved = name == state.savedName || authRepository.updateDisplayName(name)
            if (!nameSaved) {
                Log.w(TAG, "The display name could not be saved: nobody is signed in")
                _uiState.update { it.copy(saving = false) }
                _events.emit(EditProfileEvent.Rejected(R.string.edit_profile_signed_out))
                return@launch
            }
            customizationRepository.save(state.customization)
            Log.i(TAG, "Profile saved")
            _uiState.update {
                it.copy(saving = false, savedName = name, name = name, savedCustomization = state.customization)
            }
            _events.emit(EditProfileEvent.Saved)
        }
    }

    private fun updateCustomization(change: (ProfileCustomization) -> ProfileCustomization) {
        _uiState.update { it.copy(customization = change(it.customization)) }
    }

    private companion object {
        const val TAG = "EditProfileViewModel"
    }
}
//------------------------------EOF------------------------------\\
