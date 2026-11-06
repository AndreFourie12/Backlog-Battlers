package com.backlogbattlers.app.library

import com.backlogbattlers.app.data.repository.LibraryRepositoryImpl
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import kotlinx.coroutines.flow.first
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
    private var changes = 0
    private val repository = LibraryRepositoryImpl(dao, currentUserId = { signedIn }, onChanged = { changes++ })

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
    fun `an achievement Steam confirmed cannot be locked again, while an ordinary one can`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        val entryId = repository.observeLibrary().first().single().libraryEntryId
        repository.setAchievementUnlocked(entryId, "ESCAPE", unlocked = true)
        repository.setAchievementUnlocked(entryId, "BLOODLESS", unlocked = true)
        dao.upsert(dao.rows.value.single().copy(verifiedAchievementIds = listOf("ESCAPE"))) // what the account says
        changes = 0

        repository.setAchievementUnlocked(entryId, "ESCAPE", unlocked = false)
        assertEquals(listOf("BLOODLESS", "ESCAPE").sorted(), repository.observeLibrary().first().single().unlockedAchievementIds.sorted())
        assertEquals(0, changes) // nothing changed, so nothing to upload

        repository.setAchievementUnlocked(entryId, "BLOODLESS", unlocked = false)
        assertEquals(listOf("ESCAPE"), repository.observeLibrary().first().single().unlockedAchievementIds)
        assertEquals(1, changes)
    }

    @Test
    fun `an entry is synced once nothing waits to be uploaded`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        val entry = dao.rows.value.single()
        assertEquals(false, repository.isSynced(entry.libraryEntryId))

        dao.markSynced(entry.libraryEntryId, entry.updatedAt)

        assertEquals(true, repository.isSynced(entry.libraryEntryId))
        assertEquals(false, repository.isSynced("not-an-entry"))
    }

    @Test
    fun `removing hides the entry but keeps a pending delete until the account is told`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        val entryId = repository.observeLibrary().first().single().libraryEntryId

        repository.remove(entryId)

        assertTrue(repository.observeLibrary().first().isEmpty())
        assertNull(repository.observeByGameId(HADES).first())
        val tombstone = dao.rows.value.single()
        assertTrue(tombstone.pendingDelete)
        assertTrue(tombstone.pendingSync)
    }

    @Test
    fun `every change tells the sync, a change that did nothing does not`() = runBlocking {
        repository.addToLibrary(HADES, Platform.PC, LibraryStatus.PLAYING)
        val entryId = repository.observeLibrary().first().single().libraryEntryId
        repository.updateStatus(entryId, LibraryStatus.ABANDONED)
        repository.updateHoursPlayed(entryId, 3f)
        repository.setAchievementUnlocked(entryId, "ESCAPE", unlocked = true)
        repository.remove(entryId)
        assertEquals(5, changes)

        repository.remove(entryId) // already hidden
        repository.updateStatus("not-an-entry", LibraryStatus.PLAYING)
        assertEquals(5, changes)
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
//------------------------------EOF------------------------------\\
