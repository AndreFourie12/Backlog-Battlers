package com.backlogbattlers.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.backlogbattlers.app.data.local.Converters


// this class contains the Room entities for local persistence.
// nothing outside data/local and data/repository imports these directly
// viewModels and ui use domain/model classes and mapping gets done in data/repository.



//------------------------------
// UserEntity class defines the local database table for user profile details
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val xp: Int,
    val level: Int,
    val currentStreak: Int,
    //iso date format yyyy mm dd
    val lastLoginDate: String?,
)

//------------------------------
// CachedGameEntity class defines the local database table for cached game details
@Entity(tableName = "cached_games")
@TypeConverters(Converters::class)
data class CachedGameEntity(
    @PrimaryKey val gameId: Int,
    val title: String,
    val coverImageUrl: String?,
    val platforms: List<String>,
    val avgCompletionHours: Float?,
    val avg100PercentHours: Float?,
    val cachedAt: Long,
)

//------------------------------
// LibraryEntryEntity class defines the local database table for user game library items
@Entity(tableName = "library_entries")
@TypeConverters(Converters::class)
data class LibraryEntryEntity(
    @PrimaryKey val libraryEntryId: String,
    val gameId: Int,
    val platform: String,
    val status: String,
    val hoursPlayed: Float,
    val unlockedAchievementIds: List<String>,
    //completionType enum name, nullable
    val completionType: String?,
    val addedAt: Long,
    val updatedAt: Long,
    //marks local only changes for our future sync call
    val pendingSync: Boolean = false,
)
//------------------------------EOF------------------------------\\