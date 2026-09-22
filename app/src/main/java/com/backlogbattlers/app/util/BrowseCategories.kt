package com.backlogbattlers.app.util

import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.BrowseCategory
import com.backlogbattlers.app.domain.model.LibrarySort
import com.backlogbattlers.app.domain.model.SearchSort

@StringRes
fun browseCategoryLabel(category: BrowseCategory): Int = when (category) {
    BrowseCategory.ACTION -> R.string.genre_action
    BrowseCategory.RPG -> R.string.genre_rpg
    BrowseCategory.PUZZLE -> R.string.genre_puzzle
    BrowseCategory.COOP -> R.string.genre_coop
    BrowseCategory.STRATEGY -> R.string.genre_strategy
}

@StringRes
fun searchSortLabel(sort: SearchSort): Int = when (sort) {
    SearchSort.RELEVANCE -> R.string.sort_relevance
    SearchSort.NAME_A_Z -> R.string.sort_name_a_z
    SearchSort.NAME_Z_A -> R.string.sort_name_z_a
}

@StringRes
fun librarySortLabel(sort: LibrarySort): Int = when (sort) {
    LibrarySort.RECENTLY_ADDED -> R.string.sort_recently_added
    LibrarySort.NAME_A_Z -> R.string.sort_name_a_z
    LibrarySort.NAME_Z_A -> R.string.sort_name_z_a
}
