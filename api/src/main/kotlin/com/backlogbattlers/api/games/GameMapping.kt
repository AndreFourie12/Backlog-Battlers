package com.backlogbattlers.api.games

import com.backlogbattlers.api.db.Platform
import kotlinx.serialization.Serializable

/**
 * IGDB time-to-beat figures are submitted by players and can be wrong: one game reported
 * 6,617 hours (276 days). Anything above this is treated as bad data and dropped, so a single
 * outlier can never make a game worth a huge number of points.
 */
const val MAX_PLAUSIBLE_HOURS = 1000.0

/**
 * A game as the app receives it. Field names match the Android app's `Game` model,
 * except `cachedAt`, which the app sets itself.
 */
@Serializable
data class GameDto(
    val gameId: Int,
    val title: String,
    val coverImageUrl: String?,
    val platforms: List<String>,
    val avgCompletionHours: Double?,
    val avg100PercentHours: Double?,
)

/** Builds the picture address from IGDB's image id, or null when the game has no cover. */
fun coverUrl(imageId: String?): String? =
    imageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" }

/**
 * Converts IGDB platform names ("PlayStation 5", "PC (Microsoft Windows)") into the app's
 * [Platform] enum names, without duplicates and in first-seen order.
 */
fun normalisePlatforms(names: List<String>): List<String> =
    names.map { platformFor(it).name }.distinct()

private fun platformFor(name: String): Platform = when {
    "PlayStation" in name -> Platform.PLAYSTATION
    "Xbox" in name -> Platform.XBOX
    "Switch" in name -> Platform.SWITCH
    // Starts with "PC (" so that "PC Engine" and "PC-98" are not mistaken for Windows PCs
    name.startsWith("PC (") -> Platform.PC
    else -> Platform.OTHER
}

/** Converts IGDB seconds to hours, or null when the value is missing or implausible. */
fun secondsToHours(seconds: Int?): Double? {
    if (seconds == null || seconds <= 0) return null
    val hours = seconds / 3600.0
    return if (hours > MAX_PLAUSIBLE_HOURS) null else hours
}

/** Builds the app-facing game. Pass [timeToBeat] when it is known; the search results skip it. */
fun IgdbGame.toDto(timeToBeat: IgdbTimeToBeat? = null): GameDto = GameDto(
    gameId = id,
    title = name,
    coverImageUrl = coverUrl(cover?.imageId),
    platforms = normalisePlatforms(platforms.orEmpty().map { it.name }),
    avgCompletionHours = secondsToHours(timeToBeat?.normally),
    avg100PercentHours = secondsToHours(timeToBeat?.completely),
)
