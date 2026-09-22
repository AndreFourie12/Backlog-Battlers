package com.backlogbattlers.app.util

import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform

//------------------------------
// the display formatting the screens share



fun platformLabel(platform: String): String = when (platform.uppercase()) {
    "PC" -> "PC"
    "PLAYSTATION" -> "PlayStation"
    "XBOX" -> "Xbox"
    "SWITCH" -> "Switch"
    else -> "Other"
}

// the API lists a game's platforms in no set order, so the one a game is shown and filed under is picked
// by rank instead, following the order of Platform: PC first, then the consoles, "other" last.
// Null when the game lists none
fun primaryPlatform(platforms: List<String>): Platform? {
    return platforms
        .map { name -> runCatching { Platform.valueOf(name.uppercase()) }.getOrDefault(Platform.OTHER) }
        .minOrNull()
}

fun gameSubtitle(platforms: List<String>, genres: List<String>): String {
    val platform = primaryPlatform(platforms)?.let { platformLabel(it.name) }
    val genre = genres.firstOrNull()
    return listOfNotNull(platform, genre).joinToString(" · ")
}

// how a library entry's status reads inline, e.g. under a game's progress bar
fun libraryStatusLabel(status: LibraryStatus): String = when (status) {
    LibraryStatus.BACKLOG -> "Not started"
    LibraryStatus.PLAYING -> "In progress"
    LibraryStatus.COMPLETED -> "Completed"
    LibraryStatus.ABANDONED -> "Abandoned"
}

// the line under a library game's progress bar: its platform, its status, and — once the game has
// a known average completion time — how long it usually takes to beat. No percentage yet: that
// needs the app to track time played against time to beat, which is a later piece of this feature
fun libraryStatusCaption(platform: Platform, status: LibraryStatus, avgCompletionHours: Float?): String {
    val parts = mutableListOf(platformLabel(platform.name), libraryStatusLabel(status))
    if (avgCompletionHours != null && avgCompletionHours > 0f) {
        parts += "${kotlin.math.round(avgCompletionHours).toInt()}h to beat"
    }
    return parts.joinToString(" · ")
}

// a game's average time to beat, for the detail screen's stat tile. Rounded to the nearest hour;
// a dash when IGDB has no usable figure for it, rather than showing "0h"
fun formatHours(hours: Float?): String {
    if (hours == null || hours <= 0f) return "—"
    return "${kotlin.math.round(hours).toInt()}h"
}

// points are always written with a thousands separator
fun formatPoints(points: Int): String {
    val text = points.toString()
    if (text.length <= 3) return text
    return text.reversed().chunked(3).joinToString(",").reversed()
}
//------------------------------


// community earn rates are always shown to one decimal place so the column stays aligned
fun formatRarity(percent: Double): String {
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    val whole = rounded.toInt()
    val tenth = kotlin.math.round((rounded - whole) * 10.0).toInt()
    return "$whole.$tenth%"
}
//------------------------------


// turns a rank into the ordinal the copy reads with
fun ordinal(rank: Int): String {
    if (rank <= 0) return "unranked"
    val suffix = when {
        rank % 100 in 11..13 -> "th"
        rank % 10 == 1 -> "st"
        rank % 10 == 2 -> "nd"
        rank % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$rank$suffix"
}
//------------------------------



// the move since last month, written the way the position card reads it
fun deltaLabel(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "$delta"
    else -> "0"
}
//------------------------------

//------------------------------End Of File------------------------------\\