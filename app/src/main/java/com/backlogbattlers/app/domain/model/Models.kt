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
    // portrait box art from IGDB
    val coverImageUrl: String?,
    // landscape art for the wide game tiles, null when the game has none
    val artworkUrl: String? = null,
    val platforms: List<String>,
    val genres: List<String> = emptyList(),
    val avgCompletionHours: Float?,
    val avg100PercentHours: Float?,
    val cachedAt: Long,
    // null until this game's achievements have been fetched at least once
    val totalAchievements: Int? = null,
)

// Recommendation data class pairs a game with the line shown under its name, e.g. on the home screen
data class Recommendation(
    val game: Game,
    val reason: String,
)

// Achievement data class represents domain model for one of a game's achievements.
// rarityPercent is the share of players who have unlocked it globally, null when unknown
data class Achievement(
    val achievementId: String,
    val name: String,
    val description: String?,
    val rarityPercent: Double?,
    // Steam's own icons, null when no STEAM_API_KEY is configured server side
    val iconUrl: String? = null,
    val iconGrayUrl: String? = null,
)

// enum representing gaming platforms
enum class Platform {
    PC,
    PLAYSTATION,
    XBOX,
    SWITCH,
    OTHER,
}

enum class BrowseCategory(val key: String) {
    ACTION("action"),
    RPG("rpg"),
    PUZZLE("puzzle"),
    COOP("coop"),
    STRATEGY("strategy"),
}

enum class SearchSort {
    RELEVANCE,
    NAME_A_Z,
    NAME_Z_A,
}

// enum representing how the games tab's library list can be sorted
enum class LibrarySort {
    RECENTLY_ADDED,
    NAME_A_Z,
    NAME_Z_A,
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

// enum representing the appearance the user picked in settings
enum class ThemeMode {
    DARK,
    LIGHT,
}

// enum representing the artwork shown behind the home screen header.
// the drawable each one maps to lives in util/Backdrops.kt
enum class HomeBackdrop {
    HILLS,
    MOUNTAIN,
    MEADOW,
    FOREST,
    COAST,
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
// LibraryGame data class pairs a library entry with its game, for the games tab's library list
data class LibraryGame(
    val entry: LibraryEntry,
    val game: Game,
)

//------------------------------
// UserSettings data class represents domain model for application settings
data class UserSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val homeBackdrop: HomeBackdrop = HomeBackdrop.HILLS,
    val biometricLoginEnabled: Boolean = false,
    val achievementNotificationsEnabled: Boolean = true,
    val rankChangeNotificationsEnabled: Boolean = true,
    val seasonResetNotificationsEnabled: Boolean = true,
    val friendActivityNotificationsEnabled: Boolean = true,
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