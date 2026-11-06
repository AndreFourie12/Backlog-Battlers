package com.backlogbattlers.app.data.repository

import android.util.Log
import com.backlogbattlers.app.data.local.dao.LibraryEntryDao
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.LibraryEntry
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

//------------------------------
// interface providing operations for managing the signed-in user's library entries.
// everything here only ever sees the account that is signed in right now
interface LibraryRepository {

    // observes all library entries as a Flow
    fun observeLibrary(): Flow<List<LibraryEntry>>

    // observes library entries filtered by LibraryStatus as a Flow
    fun observeByStatus(status: LibraryStatus): Flow<List<LibraryEntry>>

    // observes the entry for one game, for the game detail screen. Null while the game has not been added
    fun observeByGameId(gameId: Int): Flow<LibraryEntry?>

    // adds a new game entry to the library with initial values
    suspend fun addToLibrary(gameId: Int, platform: Platform, status: LibraryStatus)

    // updates the LibraryStatus of an existing library entry
    suspend fun updateStatus(libraryEntryId: String, status: LibraryStatus)

    // updates the hours played for an existing library entry
    suspend fun updateHoursPlayed(libraryEntryId: String, hours: Float)

    // locks or unlocks one achievement on an existing library entry
    suspend fun setAchievementUnlocked(libraryEntryId: String, achievementId: String, unlocked: Boolean)

    // removes a library entry by id
    suspend fun remove(libraryEntryId: String)

    // true when the account has every change made to the entry on this phone (nothing waits to be uploaded)
    suspend fun isSynced(libraryEntryId: String): Boolean

    // removes every library entry belonging to userId on this device, when that account is deleted.
    // takes the id rather than reading the session, because the session is already gone by then
    suspend fun clear(userId: String)
}


//------------------------------
// Room backed implementation of LibraryRepository using LibraryEntryDao.
// currentUserId gives the signed-in account's id (null when signed out); every query and write is
// scoped to it, so a second account on the same phone starts with its own, empty library
class LibraryRepositoryImpl(
    private val dao: LibraryEntryDao,
    private val currentUserId: () -> String?,
    // called after every change made here, so the app can upload it. must not block
    private val onChanged: () -> Unit = {},
) : LibraryRepository {

    //------------------------------
    // observes library entries by delegating to LibraryEntryDao and mapping to domain list
    override fun observeLibrary(): Flow<List<LibraryEntry>> {
        return forCurrentUser(signedOut = emptyList()) { userId ->
            dao.observeAll(userId).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    //------------------------------
    // observes library entries filtered by status by delegating to LibraryEntryDao and mapping to domain list
    override fun observeByStatus(status: LibraryStatus): Flow<List<LibraryEntry>> {
        return forCurrentUser(signedOut = emptyList()) { userId ->
            dao.observeByStatus(userId, status.name).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    //------------------------------
    // observes the entry for one game by delegating to LibraryEntryDao and mapping to domain
    override fun observeByGameId(gameId: Int): Flow<LibraryEntry?> {
        return forCurrentUser(signedOut = null) { userId ->
            dao.observeByGameId(userId, gameId).map { it?.toDomain() }
        }
    }

    //------------------------------
    // creates and upserts a new LibraryEntryEntity with a random UUID, owned by the signed-in user.
    // does nothing when nobody is signed in
    override suspend fun addToLibrary(
        gameId: Int,
        platform: Platform,
        status: LibraryStatus,
    ) {
        val userId = currentUserId()
        if (userId == null) {
            Log.w(TAG, "Not adding game $gameId: nobody is signed in")
            return
        }
        val now = System.currentTimeMillis()
        val entity = LibraryEntryEntity(
            libraryEntryId = UUID.randomUUID().toString(),
            userId = userId,
            gameId = gameId,
            platform = platform.name,
            status = status.name,
            hoursPlayed = 0f,
            unlockedAchievementIds = emptyList(),
            completionType = null,
            addedAt = now,
            updatedAt = now,
            pendingSync = true,
        )
        dao.upsert(entity)
        Log.d(TAG, "Added game $gameId to the library as $status on $platform")
        onChanged()
    }

    //------------------------------
    // updates status field and pendingSync flag of an existing library entry
    override suspend fun updateStatus(
        libraryEntryId: String,
        status: LibraryStatus,
    ) {
        val existing = ownedEntry(libraryEntryId) ?: return
        val updated = existing.copy(
            status = status.name,
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        dao.upsert(updated)
        Log.d(TAG, "Library entry moved to $status")
        onChanged()
    }

    //------------------------------
    // updates hoursPlayed field and pendingSync flag of an existing library entry
    override suspend fun updateHoursPlayed(
        libraryEntryId: String,
        hours: Float,
    ) {
        val existing = ownedEntry(libraryEntryId) ?: return
        val updated = existing.copy(
            hoursPlayed = hours,
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        dao.upsert(updated)
        Log.d(TAG, "Hours played set to $hours")
        onChanged()
    }

    //------------------------------
    // adds or removes one achievement from the entry's unlocked set and marks it for sync.
    // an achievement Steam confirmed can never be locked again
    override suspend fun setAchievementUnlocked(
        libraryEntryId: String,
        achievementId: String,
        unlocked: Boolean,
    ) {
        val existing = ownedEntry(libraryEntryId) ?: return
        if (!unlocked && achievementId in existing.verifiedAchievementIds) {
            Log.d(TAG, "Achievement $achievementId is confirmed by Steam, so it stays unlocked")
            return
        }
        val ids = if (unlocked) {
            if (achievementId in existing.unlockedAchievementIds) return
            existing.unlockedAchievementIds + achievementId
        } else {
            if (achievementId !in existing.unlockedAchievementIds) return
            existing.unlockedAchievementIds - achievementId
        }
        val updated = existing.copy(
            unlockedAchievementIds = ids,
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        dao.upsert(updated)
        Log.d(TAG, "Achievement $achievementId ${if (unlocked) "unlocked" else "locked again"}")
        onChanged()
    }

    //------------------------------
    // whether the account already has every change made to this entry here
    override suspend fun isSynced(libraryEntryId: String): Boolean = ownedEntry(libraryEntryId)?.pendingSync == false

    //------------------------------
    // hides the entry and marks the removal for upload. the row is deleted for good once the account
    // has been told (see LibrarySync), so a removal made offline survives until the phone is back online
    override suspend fun remove(libraryEntryId: String) {
        val existing = ownedEntry(libraryEntryId) ?: return
        dao.upsert(existing.copy(pendingDelete = true, pendingSync = true, updatedAt = System.currentTimeMillis()))
        Log.d(TAG, "Removed a game from the library")
        onChanged()
    }

    //------------------------------
    // removes every library entry of one account by delegating to LibraryEntryDao
    override suspend fun clear(userId: String) {
        dao.clear(userId)
        Log.d(TAG, "Cleared the library of a deleted account")
    }

    //------------------------------
    // the signed-in user's entry with this id. null when signed out, or when the id belongs to another account
    private suspend fun ownedEntry(libraryEntryId: String): LibraryEntryEntity? {
        val userId = currentUserId() ?: return null
        return dao.getById(userId, libraryEntryId)
    }

    //------------------------------
    // runs query for whoever is signed in when the flow starts being collected (not when it was
    // created), so a screen opened after switching accounts reads the new account's rows.
    // emits signedOut once when nobody is signed in
    private fun <T> forCurrentUser(signedOut: T, query: (String) -> Flow<T>): Flow<T> = flow {
        val userId = currentUserId()
        if (userId == null) emit(signedOut) else emitAll(query(userId))
    }

    //------------------------------
    // converts LibraryEntryEntity to domain LibraryEntry model
    private fun LibraryEntryEntity.toDomain(): LibraryEntry {
        return LibraryEntry(
            libraryEntryId = libraryEntryId,
            gameId = gameId,
            platform = runCatching { Platform.valueOf(platform) }.getOrDefault(Platform.OTHER),
            status = runCatching { LibraryStatus.valueOf(status) }.getOrDefault(LibraryStatus.BACKLOG),
            hoursPlayed = hoursPlayed,
            unlockedAchievementIds = unlockedAchievementIds,
            completionType = completionType?.let {
                runCatching { CompletionType.valueOf(it) }.getOrNull()
            },
            addedAt = addedAt,
            updatedAt = updatedAt,
            pendingSync = pendingSync,
            verifiedAchievementIds = verifiedAchievementIds,
        )
    }

    private companion object {
        const val TAG = "LibraryRepository"
    }
}
//------------------------------EOF------------------------------\\
