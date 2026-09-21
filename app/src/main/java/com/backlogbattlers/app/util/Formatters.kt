package com.backlogbattlers.app.util

//------------------------------
// the display formatting the screens share



// IGDB gives platforms as a family, so a game is on "PlayStation" rather than on
// a particular console. these are the names those families are written with
fun platformLabel(platform: String): String = when (platform.uppercase()) {
    "PC" -> "PC"
    "PLAYSTATION" -> "PlayStation"
    "XBOX" -> "Xbox"
    "SWITCH" -> "Switch"
    else -> "Other"
}
//------------------------------


// the grey line under a game name in the search results, e.g. "PlayStation · Adventure".
// a game missing either half is written with whichever half it has, and an empty
// string means there is nothing worth showing at all
fun gameSubtitle(platforms: List<String>, genres: List<String>): String {
    val platform = platforms.firstOrNull()?.let { platformLabel(it) }
    val genre = genres.firstOrNull()
    return listOfNotNull(platform, genre).joinToString(" · ")
}
//------------------------------


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