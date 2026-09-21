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
    val avgCompletionHours: Float?,
    val avg100PercentHours: Float?,
    val cachedAt: Long,
)

// Recommendation data class pairs a game with the line shown under its name, e.g. on the home screen
data class Recommendation(
    val game: Game,
    val reason: String,
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
//------------------------------EOF------------------------------\\