package com.backlogbattlers.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.ProfileCustomizationRepository
import com.backlogbattlers.app.domain.model.ProfileCustomization
import com.backlogbattlers.app.domain.model.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// what the profile header shows: who is signed in and how they set up their profile
data class ProfileUiState(
    val user: User? = null,
    val customization: ProfileCustomization = ProfileCustomization(),
)

// viewmodel for the profile screen's header. both sources are flows, so a name or picture saved
// on the edit profile screen is already showing when the user comes back
class ProfileViewModel(
    authRepository: AuthRepository = ServiceLocator.authRepository,
    customizationRepository: ProfileCustomizationRepository = ServiceLocator.profileCustomizationRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(authRepository.observeCurrentUser(), customizationRepository.observeCustomization()) { user, customization ->
            ProfileUiState(user, customization)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ProfileUiState())
}
//------------------------------EOF------------------------------\\
