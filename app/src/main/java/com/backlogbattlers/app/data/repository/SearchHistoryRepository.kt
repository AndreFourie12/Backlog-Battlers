package com.backlogbattlers.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryStore by preferencesDataStore(name = "search_history")

// the "Recent" list on the search screen only keeps the last few searches, so it stays
// a shortcut rather than a log the user has to scroll through
private const val MAX_RECENT_SEARCHES = 6

// queries are stored as one line each, so the separator must be something a query cannot contain
private const val SEPARATOR = "\n"

//------------------------------
// interface providing the recent searches shown under the search box
interface SearchHistoryRepository {

    // observes the recent searches, most recent first
    fun observeRecentSearches(): Flow<List<String>>

    // records a search, moving it to the front if it was already there
    suspend fun recordSearch(query: String)

    // removes one search from the list
    suspend fun removeSearch(query: String)
}


//------------------------------
// implementation of SearchHistoryRepository storing the list in DataStore Preferences
class SearchHistoryRepositoryImpl(
    private val context: Context,
) : SearchHistoryRepository {

    private object Keys {
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }

    //------------------------------
    // observes the stored searches, most recent first
    override fun observeRecentSearches(): Flow<List<String>> {
        return context.searchHistoryStore.data.map { prefs ->
            prefs[Keys.RECENT_SEARCHES].toQueryList()
        }
    }

    //------------------------------
    // puts the search at the front of the list, dropping any earlier copy of it so the same
    // query never appears twice, and trimming the oldest once the list is full
    override suspend fun recordSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return

        context.searchHistoryStore.edit { prefs ->
            val existing = prefs[Keys.RECENT_SEARCHES].toQueryList()
            val updated = (listOf(cleaned) + existing.filterNot { it.equals(cleaned, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            prefs[Keys.RECENT_SEARCHES] = updated.joinToString(SEPARATOR)
        }
    }

    //------------------------------
    // drops one search, for the x at the end of its row
    override suspend fun removeSearch(query: String) {
        context.searchHistoryStore.edit { prefs ->
            val remaining = prefs[Keys.RECENT_SEARCHES].toQueryList()
                .filterNot { it.equals(query.trim(), ignoreCase = true) }
            prefs[Keys.RECENT_SEARCHES] = remaining.joinToString(SEPARATOR)
        }
    }

    //------------------------------
    // splits the stored line back into queries, ignoring any blanks left by an earlier write
    private fun String?.toQueryList(): List<String> {
        if (this.isNullOrEmpty()) return emptyList()
        return split(SEPARATOR).filter { it.isNotBlank() }
    }
}
//------------------------------EOF------------------------------\
