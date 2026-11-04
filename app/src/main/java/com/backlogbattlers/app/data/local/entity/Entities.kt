package com.backlogbattlers.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
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
    val artworkUrl: String? = null,
    val platforms: List<String>,
    val genres: List<String> = emptyList(),
    val avgCompletionHours: Float?,
    val avg100PercentHours: Float?,
    val cachedAt: Long,
    val totalAchievements: Int? = null,
    // landscape pictures to try in order, stored "|" separated by Converters. the default matches MIGRATION_5_6
    @ColumnInfo(defaultValue = "") val artworkUrls: List<String> = emptyList(),
)

//------------------------------
// LibraryEntryEntity class defines the local database table for user game library items.
// every row belongs to one account, so a second account signed in on the same phone
// never sees (or edits) the first account's library
@Entity(tableName = "library_entries", indices = [Index("userId")])
@TypeConverters(Converters::class)
data class LibraryEntryEntity(
    @PrimaryKey val libraryEntryId: String,
    //the account this entry belongs to. the default matches MIGRATION_4_5 for rows that existed before it
    @ColumnInfo(defaultValue = "") val userId: String,
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