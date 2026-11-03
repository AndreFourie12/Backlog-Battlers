package com.backlogbattlers.app.library

import com.backlogbattlers.app.data.local.dao.LibraryEntryDao
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.data.repository.LibraryRepositoryImpl
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// each account on a shared phone only ever sees and changes its own library
// (Part 2 feedback: a second account signing in saw the previous user's library)
class LibraryRepositoryTest {

    private val dao = FakeLibraryEntryDao()
    private var signedIn: String? = ALICE
    private val repository = LibraryRepositoryImpl(dao, currentUserId = { signedIn })

    @Test
    fun `a second account signing in on the same phone sees an empty library`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        repository.addToLibrary(CELESTE, Platform.SWITCH, LibraryStatus.BACKLOG)

        signedIn = BOB

        assertTrue(repository.observeLibrary().first().isEmpty())
        assertTrue(repository.observeByStatus(LibraryStatus.PLAYING).first().isEmpty())
        assertNull(repository.observeByGameId(HADES).first())
    }

    @Test
    fun `signing back in brings the account's own library back`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        signedIn = BOB
        repository.addToLibrary(CELESTE, Platform.SWITCH, LibraryStatus.BACKLOG)

        signedIn = ALICE

        assertEquals(listOf(HADES), repository.observeLibrary().first().map { it.gameId })
    }

    @Test
    fun `each account can add the same game without touching the other's entry`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        signedIn = BOB
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.BACKLOG)

        assertEquals(LibraryStatus.BACKLOG, repository.observeByGameId(HADES).first()?.status)
        signedIn = ALICE
        assertEquals(LibraryStatus.PLAYING, repository.observeByGameId(HADES).first()?.status)
    }

    @Test
    fun `another account cannot edit or remove an entry by its id`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        val aliceEntryId = repository.observeLibrary().first().single().libraryEntryId

        signedIn = BOB
        repository.updateStatus(aliceEntryId, LibraryStatus.COMPLETED)
        repository.updateHoursPlayed(aliceEntryId, 99f)
        repository.setAchievementUnlocked(aliceEntryId, "ESCAPE", unlocked = true)
        repository.remove(aliceEntryId)

        signedIn = ALICE
        val entry = repository.observeLibrary().first().single()
        assertEquals(LibraryStatus.PLAYING, entry.status)
        assertEquals(0f, entry.hoursPlayed)
        assertTrue(entry.unlockedAchievementIds.isEmpty())
    }

    @Test
    fun `the owner can still edit and remove their own entry`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        val entryId = repository.observeLibrary().first().single().libraryEntryId

        repository.updateStatus(entryId, LibraryStatus.COMPLETED)
        repository.setAchievementUnlocked(entryId, "ESCAPE", unlocked = true)
        val edited = repository.observeLibrary().first().single()
        assertEquals(LibraryStatus.COMPLETED, edited.status)
        assertEquals(listOf("ESCAPE"), edited.unlockedAchievementIds)

        repository.remove(entryId)
        assertTrue(repository.observeLibrary().first().isEmpty())
    }

    @Test
    fun `nobody signed in sees nothing and adds nothing`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)

        signedIn = null
        assertTrue(repository.observeLibrary().first().isEmpty())
        repository.addToLibrary(CELESTE, Platform.SWITCH, LibraryStatus.BACKLOG)

        assertEquals(listOf(HADES), dao.rows.value.map { it.gameId })
    }

    @Test
    fun `deleting an account clears only that account's library`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        signedIn = BOB
        repository.addToLibrary(CELESTE, Platform.SWITCH, LibraryStatus.BACKLOG)

        // the account is already signed out by the time its library is cleared
        signedIn = null
        repository.clear(ALICE)

        assertEquals(listOf(BOB), dao.rows.value.map { it.userId })
    }

    @Test
    fun `new entries are stamped with the signed in account`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)

        assertEquals(ALICE, dao.rows.value.single().userId)
    }

    private companion object {
        const val ALICE = "user-alice"
        const val BOB = "user-bob"
        const val HADES = 1113
        const val CELESTE = 26226
    }
}

// an in-memory LibraryEntryDao that filters exactly like the real queries in Daos.kt
private class FakeLibraryEntryDao : LibraryEntryDao {

    val rows = MutableStateFlow<List<LibraryEntryEntity>>(emptyList())

    override fun observeAll(userId: String): Flow<List<LibraryEntryEntity>> =
        rows.map { list -> list.filter { it.userId == userId }.sortedByDescending { it.updatedAt } }

    override fun observeByStatus(userId: String, status: String): Flow<List<LibraryEntryEntity>> =
        observeAll(userId).map { list -> list.filter { it.status == status } }

    override suspend fun getById(userId: String, id: String): LibraryEntryEntity? =
        rows.value.firstOrNull { it.userId == userId && it.libraryEntryId == id }

    override fun observeByGameId(userId: String, gameId: Int): Flow<LibraryEntryEntity?> =
        rows.map { list -> list.firstOrNull { it.userId == userId && it.gameId == gameId } }

    override suspend fun getPendingSync(userId: String): List<LibraryEntryEntity> =
        rows.value.filter { it.userId == userId && it.pendingSync }

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
//------------------------------EOF------------------------------\\
