package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.R
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.ProfileCustomizationRepository
import com.backlogbattlers.app.data.repository.ProfileRepository
import com.backlogbattlers.app.domain.model.Profile
import com.backlogbattlers.app.domain.model.ProfileCustomization
import com.backlogbattlers.app.domain.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// everything the profile screen shows. the header needs only the first two, which are flows and
// so are there at once; the stats, badges, showcase and recent completions need the profile,
// which the backend works out
data class ProfileUiState(
    val user: User? = null,
    val customization: ProfileCustomization = ProfileCustomization(),
    val profile: Profile? = null,
    // the first load, or a retry after it failed: there is no profile to show yet
    val isLoading: Boolean = true,
    // a pull to refresh is under way, so the profile stays on screen under the spinner
    val isRefreshing: Boolean = false,
    // a message from strings.xml shown in place of the profile's sections, when there is no profile to show
    @StringRes val errorMessage: Int? = null,
    // a message from strings.xml for the screen to show once (a refresh failed, with a profile already on screen)
    @StringRes val message: Int? = null,
)

// viewmodel for the profile screen: the signed in user's name and picture, and the profile the
// backend works out (level and XP, stats, badges, rarest achievements, recent completions)
class ProfileViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val customizationRepository: ProfileCustomizationRepository = ServiceLocator.profileCustomizationRepository,
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // the loading half of the state, kept apart from the two flows it is combined with
    private data class Load(
        val profile: Profile? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val errorMessage: Int? = null,
        val message: Int? = null,
    )

    private val load = MutableStateFlow(Load())
    private var loadedAt = 0L
    private var loadJob: Job? = null

    val uiState: StateFlow<ProfileUiState> =
        combine(authRepository.observeCurrentUser(), customizationRepository.observeCustomization(), load) { user, customization, load ->
            ProfileUiState(user, customization, load.profile, load.isLoading, load.isRefreshing, load.errorMessage, load.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ProfileUiState())

    init {
        refresh()
    }

    //------------------------------
    // loads the profile again. pulled is true when the user dragged the screen down, so the pull
    // spinner shows instead of the loading one. a failure keeps a profile that is already on screen
    fun refresh(pulled: Boolean = false) {
        loadJob?.cancel()
        load.update { it.copy(isLoading = !pulled && it.profile == null, isRefreshing = pulled, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile()
                Log.d(TAG, "Loaded the profile: level ${profile.level}, ${profile.badgesEarned} badges earned")
                loadedAt = clock()
                load.update { it.copy(profile = profile, isLoading = false, isRefreshing = false) }
                syncPicture(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                // the app is already on its way back to login (MainActivity), so no error message on top of that
                Log.w(TAG, "The session ended while loading the profile", e)
                load.update { it.copy(isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Loading the profile failed", e)
                load.update {
                    if (it.profile == null) it.copy(isLoading = false, isRefreshing = false, errorMessage = R.string.profile_error_load)
                    else it.copy(isLoading = false, isRefreshing = false, message = R.string.profile_error_load)
                }
            }
        }
    }

    //------------------------------
    // the picture is kept on the account too, so friends see it and a new phone has it. one that only the
    // account has is taken here; one that only this phone has (picked before pictures were saved to the
    // account) is sent up. best effort: a failure just leaves it for the next time
    private fun syncPicture(profile: Profile) {
        viewModelScope.launch {
            try {
                val mine = customizationRepository.observeCustomization().first()
                val onAccount = profile.pictureId
                when {
                    onAccount != null && mine.pictureId == null -> customizationRepository.save(mine.copy(pictureId = onAccount))
                    onAccount == null && mine.pictureId != null -> authRepository.updateProfile(pictureId = mine.pictureId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The profile picture could not be synced", e)
            }
        }
    }

    //------------------------------
    // called each time the screen comes back: reloads quietly when what is shown is older than
    // STALE_AFTER_MS, so a game completed elsewhere is in the stats when the user returns here
    fun refreshIfStale() {
        val current = load.value
        if (current.isLoading || current.isRefreshing || loadJob?.isActive == true) return
        if (current.profile == null || clock() - loadedAt > STALE_AFTER_MS) refresh()
    }

    //------------------------------
    // the screen has shown the one-off message
    fun messageShown() {
        load.update { it.copy(message = null) }
    }

    private companion object {
        const val TAG = "ProfileViewModel"
        const val STALE_AFTER_MS = 30_000L
    }
}
//------------------------------EOF------------------------------\\
