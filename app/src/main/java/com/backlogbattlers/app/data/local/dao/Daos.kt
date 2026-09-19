package com.backlogbattlers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.backlogbattlers.app.data.local.entity.CachedGameEntity
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

//------------------------------
// this interface provides database access ops for UserEntity
@Dao
interface UserDao {

    // observes user data as a Flow
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    // fetches user data once
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUser(userId: String): UserEntity?

    // inserts or replaces user data
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    // clears user table on logout
    @Query("DELETE FROM users")
    suspend fun clear()
}

//------------------------------
// this interface provides database access ops for CachedGameEntity
@Dao
interface CachedGameDao {

    // fetches a cached game by game id
    @Query("SELECT * FROM cached_games WHERE gameId = :gameId LIMIT 1")
    suspend fun getGame(gameId: Int): CachedGameEntity?

    // searches cached games by title query as a Flow
    @Query("SELECT * FROM cached_games WHERE title LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<CachedGameEntity>>

    // inserts or replaces a cached game
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: CachedGameEntity)

    // inserts or replaces a list of cached games
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(games: List<CachedGameEntity>)
}

//------------------------------
// this interface provides database access ops for LibraryEntryEntity
@Dao
interface LibraryEntryDao {

    // observes all library entries ordered by update time as a Flow
    @Query("SELECT * FROM library_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LibraryEntryEntity>>

    // observes library entries filtered by status as a Flow
    @Query("SELECT * FROM library_entries WHERE status = :status ORDER BY updatedAt DESC")
    fun observeByStatus(status: String): Flow<List<LibraryEntryEntity>>

    // fetches a library entry by id
    @Query("SELECT * FROM library_entries WHERE libraryEntryId = :id LIMIT 1")
    suspend fun getById(id: String): LibraryEntryEntity?

    // fetches all library entries marked as pending sync
    @Query("SELECT * FROM library_entries WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<LibraryEntryEntity>

    // inserts/ replaces a library entry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LibraryEntryEntity)

    // deletes library entry
    @Delete
    suspend fun delete(entry: LibraryEntryEntity)
}
//------------------------------EOF------------------------------\\