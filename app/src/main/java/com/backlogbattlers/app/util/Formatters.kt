package com.backlogbattlers.app.util

import android.content.Context
import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.Platform

//------------------------------
// the display formatting the screens share. anything a person reads comes from strings.xml, so
// it follows the app's language; the functions here only decide which string, and how to join them



// the name a platform is shown with. PC games are labelled by the store most PC players use
@StringRes
fun platformLabelRes(platform: String): Int = when (platform.uppercase()) {
    "PC" -> R.string.platform_steam
    "PLAYSTATION" -> R.string.platform_playstation
    "XBOX" -> R.string.platform_xbox
    "SWITCH" -> R.string.platform_switch
    else -> R.string.platform_other
}

fun Context.platformLabel(platform: String): String = getString(platformLabelRes(platform))

// the platform a game is shown and filed under: the user's default platform (Settings) when the
// game is on it. Otherwise, as the API lists a game's platforms in no set order, it is picked by
// rank, following the order of Platform: PC first, then the consoles, "other" last.
// Null when the game lists none
fun primaryPlatform(platforms: List<String>, preferred: Platform? = null): Platform? {
    val listed = platforms.map { name -> runCatching { Platform.valueOf(name.uppercase()) }.getOrDefault(Platform.OTHER) }
    return preferred?.takeIf { it in listed } ?: listed.minOrNull()
}

// the line under a game: its platform's name and its first genre, whichever of the two it has.
// genres come from the IGDB catalogue as they are
fun gameSubtitle(platformLabel: String?, genres: List<String>): String {
    return listOfNotNull(platformLabel, genres.firstOrNull()).joinToString(" · ")
}

// a game's average time to beat, rounded to the nearest hour, or null when IGDB has no usable
// figure for it (so the screen can show a dash rather than "0h")
fun hoursToBeat(hours: Float?): Int? {
    if (hours == null || hours <= 0f) return null
    return kotlin.math.round(hours).toInt()
}

// the same, written out for the detail screen's stat tile, e.g. "25h"
fun Context.formatHours(hours: Float?): String {
    val whole = hoursToBeat(hours) ?: return getString(R.string.hours_unknown)
    return getString(R.string.hours_short, whole)
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





// the move since last month, written the way the position card reads it
fun deltaLabel(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "$delta"
    else -> "0"
}
//------------------------------

//------------------------------End Of File------------------------------\\
