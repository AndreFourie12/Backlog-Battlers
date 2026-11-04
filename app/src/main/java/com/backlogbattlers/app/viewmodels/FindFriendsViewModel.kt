package com.backlogbattlers.app.viewmodels

import android.util.Log
import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.repository.FriendsRepository
import com.backlogbattlers.app.domain.model.FriendSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// everything the find friends screen needs for rendering
data class FindFriendsUiState(
    val query: String = "",
    val results: List<FriendSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    // a message from strings.xml for the screen to show once, never the raw exception text
    @StringRes val errorMessage: Int? = null,
    val sentRequestUserIds: Set<String> = emptySet(),
    val hasSearched: Boolean = false,
)

// viewmodel for finding and requesting new friends
class FindFriendsViewModel(
    private val friendsRepository: FriendsRepository = ServiceLocator.friendsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindFriendsUiState())
    val uiState: StateFlow<FindFriendsUiState> = _uiState.asStateFlow()

    //------------------------------
    // updates the typed query without searching, search only runs on submit
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query, hasSearched = false) }
    }

    //------------------------------
    // runs a search for the currently typed query
    fun search() {
        val query = _uiState.value.query.trim()

        // this if statement skips calling the server for blank queryies
        if (query.isEmpty()) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val results = friendsRepository.searchUsers(query)
                Log.d(TAG, "Friend search found ${results.size} people")
                _uiState.update { it.copy(isLoading = false, results = results, hasSearched = true) }
            } catch (e: SessionExpiredException) {
                // the app is already on its way back to login (MainActivity), so no error message on top of that
                Log.w(TAG, "The session ended while searching for friends", e)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Searching for friends failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = R.string.friends_error_search, hasSearched = true) }
            }
        }
    }

    //------------------------------
    // sends a friend request and marks it as sent locally so the button updates straight away
    fun sendRequest(targetUserId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.sendRequest(targetUserId)
                Log.i(TAG, "Friend request sent")
                _uiState.update { it.copy(sentRequestUserIds = it.sentRequestUserIds + targetUserId) }
            } catch (e: SessionExpiredException) {
                Log.w(TAG, "The session ended while sending a friend request", e)
            } catch (e: Exception) {
                Log.e(TAG, "Sending a friend request failed", e)
                _uiState.update { it.copy(errorMessage = R.string.friends_error_send) }
            }
        }
    }

    private companion object {
        const val TAG = "FindFriendsViewModel"
    }
}
//------------------------------EOF------------------------------\\