package com.backlogbattlers.app.library

import com.backlogbattlers.app.data.local.dao.LibraryEntryDao
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// an in-memory LibraryEntryDao that filters exactly like the real queries in Daos.kt
internal class FakeLibraryEntryDao : LibraryEntryDao {

    val rows = MutableStateFlow<List<LibraryEntryEntity>>(emptyList())

    override fun observeAll(userId: String): Flow<List<LibraryEntryEntity>> =
        rows.map { list -> list.filter { it.userId == userId && !it.pendingDelete }.sortedByDescending { it.updatedAt } }

    override fun observeByStatus(userId: String, status: String): Flow<List<LibraryEntryEntity>> =
        observeAll(userId).map { list -> list.filter { it.status == status } }

    override suspend fun getById(userId: String, id: String): LibraryEntryEntity? =
        rows.value.firstOrNull { it.userId == userId && !it.pendingDelete && it.libraryEntryId == id }

    override fun observeByGameId(userId: String, gameId: Int): Flow<LibraryEntryEntity?> =
        rows.map { list -> list.firstOrNull { it.userId == userId && !it.pendingDelete && it.gameId == gameId } }

    override suspend fun getPendingSync(userId: String): List<LibraryEntryEntity> =
        rows.value.filter { it.userId == userId && it.pendingSync }

    override suspend fun getAll(userId: String): List<LibraryEntryEntity> =
        rows.value.filter { it.userId == userId }

    override suspend fun markSynced(id: String, updatedAt: Long) {
        rows.value = rows.value.map {
            if (it.libraryEntryId == id && it.updatedAt == updatedAt) it.copy(pendingSync = false) else it
        }
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { it.libraryEntryId == id }
    }

    override suspend fun upsert(entry: LibraryEntryEntity) {
        rows.value = rows.value.filterNot { it.libraryEntryId == entry.libraryEntryId } + entry
    }

    override suspend fun delete(entry: LibraryEntryEntity) {
        rows.value = rows.value.filterNot { it.libraryEntryId == entry.libraryEntryId }
    }

    override suspend fun clear(userId: String) {
        rows.value = rows.value.filterNot { it.userId == userId }
    }
}
