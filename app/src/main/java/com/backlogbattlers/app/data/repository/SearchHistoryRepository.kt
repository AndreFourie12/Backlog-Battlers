package com.backlogbattlers.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryStore by preferencesDataStore(name = "search_history")

private const val MAX_RECENT_SEARCHES = 6

private const val SEPARATOR = "\n"

// the one list every account shared before searches were kept per account
private val SHARED_RECENT_SEARCHES = stringPreferencesKey("recent_searches")

interface SearchHistoryRepository {

    fun observeRecentSearches(): Flow<List<String>>

    suspend fun recordSearch(query: String)

    suspend fun removeSearch(query: String)

    // forgets every recent search of userId, when that account is deleted
    suspend fun clear(userId: String)
}

// DataStore backed implementation of SearchHistoryRepository. currentUserId gives the signed-in
// account's id (null when signed out); each account keeps its own list, so whoever signs in next
// on the same phone never sees another person's searches
class SearchHistoryRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val currentUserId: () -> String?,
) : SearchHistoryRepository {

    constructor(context: Context, currentUserId: () -> String?) :
        this(context.searchHistoryStore, currentUserId)

    //------------------------------
    // the signed-in account's searches, newest first. read for whoever is signed in when the flow
    // starts being collected, and empty when nobody is
    override fun observeRecentSearches(): Flow<List<String>> = flow {
        val userId = currentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        adoptSharedSearches(userId)
        emitAll(store.data.map { prefs -> prefs[keyFor(userId)].toQueryList() })
    }

    //------------------------------
    // puts query at the top of the signed-in account's list, once, keeping the newest few
    override suspend fun recordSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        val userId = currentUserId() ?: return

        store.edit { prefs ->
            val existing = prefs[keyFor(userId)].toQueryList()
            val updated = (listOf(cleaned) + existing.filterNot { it.equals(cleaned, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            prefs[keyFor(userId)] = updated.joinToString(SEPARATOR)
        }
    }

    //------------------------------
    // takes query off the signed-in account's list
    override suspend fun removeSearch(query: String) {
        val userId = currentUserId() ?: return
        store.edit { prefs ->
            val remaining = prefs[keyFor(userId)].toQueryList()
                .filterNot { it.equals(query.trim(), ignoreCase = true) }
            prefs[keyFor(userId)] = remaining.joinToString(SEPARATOR)
        }
    }

    override suspend fun clear(userId: String) {
        store.edit { prefs -> prefs.remove(keyFor(userId)) }
        Log.d(TAG, "Cleared the recent searches of a deleted account")
    }

    //------------------------------
    // the shared list from before this change goes to the first account to open search after
    // updating (the phone's owner, as with the library) and is then removed, so nobody else gets it
    private suspend fun adoptSharedSearches(userId: String) {
        store.edit { prefs ->
            val shared = prefs[SHARED_RECENT_SEARCHES] ?: return@edit
            if (prefs[keyFor(userId)] == null) prefs[keyFor(userId)] = shared
            prefs.remove(SHARED_RECENT_SEARCHES)
            Log.i(TAG, "Moved the shared recent searches to the signed-in account")
        }
    }

    private fun keyFor(userId: String) = stringPreferencesKey("recent_searches_$userId")

    private fun String?.toQueryList(): List<String> {
        if (this.isNullOrEmpty()) return emptyList()
        return split(SEPARATOR).filter { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "SearchHistoryRepository"
    }
}
