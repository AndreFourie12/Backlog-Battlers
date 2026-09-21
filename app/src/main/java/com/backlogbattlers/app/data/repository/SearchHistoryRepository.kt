package com.backlogbattlers.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryStore by preferencesDataStore(name = "search_history")

private const val MAX_RECENT_SEARCHES = 6

private const val SEPARATOR = "\n"

interface SearchHistoryRepository {

    fun observeRecentSearches(): Flow<List<String>>

    suspend fun recordSearch(query: String)

    suspend fun removeSearch(query: String)
}

class SearchHistoryRepositoryImpl(
    private val context: Context,
) : SearchHistoryRepository {

    private object Keys {
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }

    override fun observeRecentSearches(): Flow<List<String>> {
        return context.searchHistoryStore.data.map { prefs ->
            prefs[Keys.RECENT_SEARCHES].toQueryList()
        }
    }

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

    override suspend fun removeSearch(query: String) {
        context.searchHistoryStore.edit { prefs ->
            val remaining = prefs[Keys.RECENT_SEARCHES].toQueryList()
                .filterNot { it.equals(query.trim(), ignoreCase = true) }
            prefs[Keys.RECENT_SEARCHES] = remaining.joinToString(SEPARATOR)
        }
    }

    private fun String?.toQueryList(): List<String> {
        if (this.isNullOrEmpty()) return emptyList()
        return split(SEPARATOR).filter { it.isNotBlank() }
    }
}
