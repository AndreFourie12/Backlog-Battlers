package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.local.dao.LibraryEntryDao
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.LibraryEntry
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

//------------------------------
// interface providing operations for managing user library entries
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
}


//------------------------------
// Room backed implementation of LibraryRepository using LibraryEntryDao
// modify when RestAPI integrated
class LibraryRepositoryImpl(
    private val dao: LibraryEntryDao,
) : LibraryRepository {

    //------------------------------
    // observes library entries by delegating to LibraryEntryDao and mapping to domain list
    override fun observeLibrary(): Flow<List<LibraryEntry>> {
        return dao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    //------------------------------
    // observes library entries filtered by status by delegating to LibraryEntryDao and mapping to domain list
    override fun observeByStatus(status: LibraryStatus): Flow<List<LibraryEntry>> {
        return dao.observeByStatus(status.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    //------------------------------
    // observes the entry for one game by delegating to LibraryEntryDao and mapping to domain
    override fun observeByGameId(gameId: Int): Flow<LibraryEntry?> {
        return dao.observeByGameId(gameId).map { it?.toDomain() }
    }

    //------------------------------
    // creates and upserts a new LibraryEntryEntity with a random UUID
    override suspend fun addToLibrary(
        gameId: Int,
        platform: Platform,
        status: LibraryStatus,
    ) {
        val now = System.currentTimeMillis()
        val entity = LibraryEntryEntity(
            libraryEntryId = UUID.randomUUID().toString(),
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
    }

    //------------------------------
    // updates status field and pendingSync flag of an existing library entry
    override suspend fun updateStatus(
        libraryEntryId: String,
        status: LibraryStatus,
    ) {
        val existing = dao.getById(libraryEntryId) ?: return
        val updated = existing.copy(
            status = status.name,
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        dao.upsert(updated)
    }

    //------------------------------
    // updates hoursPlayed field and pendingSync flag of an existing library entry
    override suspend fun updateHoursPlayed(
        libraryEntryId: String,
        hours: Float,
    ) {
        val existing = dao.getById(libraryEntryId) ?: return
        val updated = existing.copy(
            hoursPlayed = hours,
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        dao.upsert(updated)
    }

    //------------------------------
    // adds or removes one achievement from the entry's unlocked set and marks it for sync
    override suspend fun setAchievementUnlocked(
        libraryEntryId: String,
        achievementId: String,
        unlocked: Boolean,
    ) {
        val existing = dao.getById(libraryEntryId) ?: return
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
    }

    //------------------------------
    // fetches entry by id and deletes it from database
    override suspend fun remove(libraryEntryId: String) {
        val existing = dao.getById(libraryEntryId) ?: return
        dao.delete(existing)
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
        )
    }
}
//------------------------------EOF------------------------------\\