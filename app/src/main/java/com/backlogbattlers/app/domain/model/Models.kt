package com.backlogbattlers.app.domain.model

// user data class represents domain model for user details
data class User(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val xp: Int,
    val level: Int,
    val currentStreak: Int,
    val lastLoginDate: String?,
)

// game data class represents domain model for game information
data class Game(
    val gameId: Int,
    val title: String,
    val coverImageUrl: String?,
    val platforms: List<String>,
    val avgCompletionHours: Float?,
    val avg100PercentHours: Float?,
    val cachedAt: Long,
)

// LibraryEntry data class represents domain model for user game library entries
data class LibraryEntry(
    val libraryEntryId: String,
    val gameId: Int,
    val platform: String,
    val status: String,
    val hoursPlayed: Float,
    val unlockedAchievementIds: List<String>,
    val completionType: String?,
    val addedAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean = false,
)
//------------------------------EOF------------------------------\\