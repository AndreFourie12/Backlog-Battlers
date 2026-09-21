package com.backlogbattlers.app.domain.model

// User data class represents domain model for user details
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

// Game data class represents domain model for game information
data class Game(
    val gameId: Int,
    val title: String,
    val coverImageUrl: String?,
    val platforms: List<String>,
    val avgCompletionHours: Float?,
    val avg100PercentHours: Float?,
    val cachedAt: Long,
)

// enum representing gaming platforms
enum class Platform {
    PC,
    PLAYSTATION,
    XBOX,
    SWITCH,
    OTHER,
}

// enum representing backlog library item status
enum class LibraryStatus {
    BACKLOG,
    PLAYING,
    COMPLETED,
    ABANDONED,
}

// enum representing game completion types
enum class CompletionType {
    MAIN_STORY,
    MAIN_EXTRA,
    COMPLETIONIST,
}

// enum representing supported application languages
enum class AppLanguage {
    ENGLISH,
    NODECIDEDYET,
    AFRIKAANS,
}

//------------------------------
// LibraryEntry data class represents domain model for user game library entries
data class LibraryEntry(
    val libraryEntryId: String,
    val gameId: Int,
    val platform: Platform,
    val status: LibraryStatus,
    val hoursPlayed: Float,
    val unlockedAchievementIds: List<String>,
    val completionType: CompletionType?,
    val addedAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean = false,
)

//------------------------------
// UserSettings data class represents domain model for application settings
data class UserSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val biometricLoginEnabled: Boolean = false,
    val achievementNotificationsEnabled: Boolean = true,
    val rankChangeNotificationsEnabled: Boolean = true,
    val seasonResetNotificationsEnabled: Boolean = true,
)

// how a signed in user relates to someone found by a friend search
enum class FriendRelationshipStatus {
    NONE,
    PENDING_SENT,
    PENDING_RECEIVED,
    FRIENDS,
}

// a person the signed in user is friends with, or the signed in user themself once
data class Friend(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val monthlyPoints: Int,
    val achievementCount: Int,
    val isOnline: Boolean,
)

// one incoming, not yet answered friend request
data class FriendRequest(
    val requestId: String,
    val fromUserId: String,
    val fromDisplayName: String,
    val fromAvatarUrl: String?,
    val createdAt: Long,
)

// one person found while searching for someone to add
data class FriendSearchResult(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val status: FriendRelationshipStatus,
)
//------------------------------EOF------------------------------\\