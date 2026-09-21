package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.remote.FriendDto
import com.backlogbattlers.app.data.remote.FriendRelationshipStatusDto
import com.backlogbattlers.app.data.remote.FriendRequestDto
import com.backlogbattlers.app.data.remote.FriendSearchResultDto
import com.backlogbattlers.app.data.remote.FriendsApi
import com.backlogbattlers.app.domain.model.Friend
import com.backlogbattlers.app.domain.model.FriendRelationshipStatus
import com.backlogbattlers.app.domain.model.FriendRequest
import com.backlogbattlers.app.domain.model.FriendSearchResult

// operations for the signed in users friends, friend requests and friend search
interface FriendsRepository {
    suspend fun getFriends(): List<Friend>
    suspend fun getIncomingRequests(): List<FriendRequest>
    suspend fun searchUsers(query: String): List<FriendSearchResult>
    suspend fun sendRequest(targetUserId: String)
    suspend fun acceptRequest(requestId: String)
    suspend fun rejectRequest(requestId: String)
}

// network implementation for FriendsRepository, mapping dtos to domain models
class FriendsRepositoryImpl(
    private val api: FriendsApi,
) : FriendsRepository {

    //------------------------------
    // fetches the signed in user's friend list mapped to domain models
    override suspend fun getFriends(): List<Friend> = api.getFriends().map { it.toDomain() }

    //------------------------------
    // fetches incoming friend requests mapped to domain models
    override suspend fun getIncomingRequests(): List<FriendRequest> = api.getIncomingRequests().map { it.toDomain() }

    //------------------------------
    // searches for users by query mapped to domain models
    override suspend fun searchUsers(query: String): List<FriendSearchResult> = api.searchUsers(query).map { it.toDomain() }

    //------------------------------
    // sends a friend request to targetUserId via the remote api
    override suspend fun sendRequest(targetUserId: String) {
        api.sendRequest(targetUserId)
    }

    //------------------------------
    // accepts a pending friend request via the remote api
    override suspend fun acceptRequest(requestId: String) {
        api.acceptRequest(requestId)
    }

    //------------------------------
    // rejects a pending friend request via the remote api
    override suspend fun rejectRequest(requestId: String) {
        api.rejectRequest(requestId)
    }

    //------------------------------
    // converts a FriendDto to domain Friend
    private fun FriendDto.toDomain(): Friend = Friend(userId, displayName, avatarUrl, monthlyPoints, achievementCount, isOnline)

    //------------------------------
    // converts a FriendRequestDto to domain FriendRequest
    private fun FriendRequestDto.toDomain(): FriendRequest = FriendRequest(requestId, fromUserId, fromDisplayName, fromAvatarUrl, createdAt)

    //------------------------------
    // converts a FriendSearchResultDto to domain FriendSearchResult
    private fun FriendSearchResultDto.toDomain(): FriendSearchResult = FriendSearchResult(userId, displayName, avatarUrl, status.toDomain())

    //------------------------------
    // converts a FriendRelationshipStatusDto to domain FriendRelationshipStatus
    private fun FriendRelationshipStatusDto.toDomain(): FriendRelationshipStatus {
        // this when expression maps remote relationship status dtos to domain status enums
        return when (this) {
            FriendRelationshipStatusDto.NONE -> FriendRelationshipStatus.NONE
            FriendRelationshipStatusDto.PENDING_SENT -> FriendRelationshipStatus.PENDING_SENT
            FriendRelationshipStatusDto.PENDING_RECEIVED -> FriendRelationshipStatus.PENDING_RECEIVED
            FriendRelationshipStatusDto.FRIENDS -> FriendRelationshipStatus.FRIENDS
        }
    }
}
//------------------------------EOF------------------------------\\