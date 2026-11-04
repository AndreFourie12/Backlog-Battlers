package com.backlogbattlers.app.data.repository

import android.util.Log
import com.backlogbattlers.app.data.remote.LeaderboardApi
import com.backlogbattlers.app.data.remote.LeaderboardEntryDto
import com.backlogbattlers.app.domain.model.LeaderboardBoard
import com.backlogbattlers.app.domain.model.LeaderboardEntry

// operations for the monthly leaderboard
interface LeaderboardRepository {
    suspend fun getMonthlyLeaderboard(): LeaderboardBoard
}

// network implementation for LeaderboardRepository, mapping dtos to domain models
class LeaderboardRepositoryImpl(
    private val api: LeaderboardApi,
) : LeaderboardRepository {

    //------------------------------
    // fetches this month's leaderboard mapped to domain models
    override suspend fun getMonthlyLeaderboard(): LeaderboardBoard {
        val response = api.getMonthlyLeaderboard()
        Log.d(TAG, "Leaderboard for ${response.monthPeriod} has ${response.entries.size} entries")
        return LeaderboardBoard(
            entries = response.entries.map { it.toDomain() },
            me = response.me?.toDomain(),
        )
    }

    //------------------------------
    // converts a LeaderboardEntryDto to domain LeaderboardEntry
    private fun LeaderboardEntryDto.toDomain(): LeaderboardEntry =
        LeaderboardEntry(rank, userId, displayName, avatarUrl, monthlyPoints)

    private companion object {
        const val TAG = "LeaderboardRepository"
    }
}
//------------------------------EOF------------------------------\\
