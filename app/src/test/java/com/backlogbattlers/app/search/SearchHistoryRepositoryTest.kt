package com.backlogbattlers.app.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.backlogbattlers.app.data.repository.SearchHistoryRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// the real DataStore, written to a temporary file, so the per account keys are tested as stored
class SearchHistoryRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = PreferenceDataStoreFactory.create(scope = scope) {
        folder.newFile("search_history.preferences_pb").also { it.delete() }
    }

    // whoever is "signed in" for the repository
    private var signedIn: String? = "alice"
    private val repository = SearchHistoryRepositoryImpl(store, currentUserId = { signedIn })

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun recent() = repository.observeRecentSearches().first()

    @Test
    fun `each account sees only its own searches`() = runBlocking {
        repository.recordSearch("hades")
        signedIn = "bob"
        repository.recordSearch("celeste")

        assertEquals(listOf("celeste"), recent())
        signedIn = "alice"
        assertEquals(listOf("hades"), recent())
    }

    @Test
    fun `nobody signed in sees no searches and records none`() = runBlocking {
        repository.recordSearch("hades")
        signedIn = null

        repository.recordSearch("celeste")

        assertEquals(emptyList<String>(), recent())
        signedIn = "alice"
        assertEquals(listOf("hades"), recent())
    }

    @Test
    fun `searches are newest first, without repeats, and capped at six`() = runBlocking {
        listOf("a", "b", "c", "d", "e", "f", "g", "B").forEach { repository.recordSearch(it) }

        assertEquals(listOf("B", "g", "f", "e", "d", "c"), recent())
    }

    @Test
    fun `removing a search only touches the signed in account`() = runBlocking {
        repository.recordSearch("hades")
        signedIn = "bob"
        repository.recordSearch("hades")

        repository.removeSearch("HADES")

        assertEquals(emptyList<String>(), recent())
        signedIn = "alice"
        assertEquals(listOf("hades"), recent())
    }

    @Test
    fun `deleting an account clears its searches and leaves the others`() = runBlocking {
        repository.recordSearch("hades")
        signedIn = "bob"
        repository.recordSearch("celeste")

        repository.clear("alice")

        assertEquals(listOf("celeste"), recent())
        signedIn = "alice"
        assertEquals(emptyList<String>(), recent())
    }

    @Test
    fun `the old shared list goes to the first account to open search, and nobody else`() = runBlocking {
        store.edit { it[stringPreferencesKey("recent_searches")] = "hades\nceleste" }

        assertEquals(listOf("hades", "celeste"), recent())
        signedIn = "bob"
        assertEquals(emptyList<String>(), recent())
    }
}
