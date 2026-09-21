package com.backlogbattlers.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backlogbattlers.app.core.ServiceLocator
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
    val errorMessage: String? = null,
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
                _uiState.update { it.copy(isLoading = false, results = results, hasSearched = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "search failed", hasSearched = true) }
            }
        }
    }

    //------------------------------
    // sends a friend request and marks it as sent locally so the button updates straight away
    fun sendRequest(targetUserId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.sendRequest(targetUserId)
                _uiState.update { it.copy(sentRequestUserIds = it.sentRequestUserIds + targetUserId) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "could not send request") }
            }
        }
    }
}
//------------------------------EOF------------------------------\\