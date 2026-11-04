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
    // the first of [artworkUrls], kept for app builds that only read one picture
    val artworkUrl: String? = null,
    val genres: List<String> = emptyList(),
    // landscape pictures to try in order: the app shows the first one that loads, then its own
    // "no image found" picture when none do. empty when there is nothing to try
    val artworkUrls: List<String> = emptyList(),
)

private const val IGDB_IMAGES = "https://images.igdb.com/igdb/image/upload"
private const val STEAM_ASSETS = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"

// the landscape pictures Steam keeps at a fixed address for an app, best fit for a wide tile first:
// the 616x353 capsule, the 460x215 store header and the 3840x1240 library banner. newer games keep
// some or all of them at addresses with a hash in them instead, which can't be guessed, so these
// can be missing (404), and the app moves on to the next
private val STEAM_LANDSCAPE_FILES = listOf("capsule_616x353.jpg", "header.jpg", "library_hero.jpg")

/** Builds the picture address from IGDB's image id, or null when the game has no cover. */
fun coverUrl(imageId: String?): String? =
    imageId?.let { "$IGDB_IMAGES/t_cover_big/$it.jpg" }

fun steamArtUrl(steamAppId: Int?): String? =
    steamAppId?.let { "$STEAM_ASSETS/$it/${STEAM_LANDSCAPE_FILES.first()}" }

/**
 * Every landscape picture worth trying for a game, best first: Steam's own wide pictures (when the game
 * is on Steam), then IGDB's official artwork and a screenshot (889x500). Building the list makes no
 * requests, so it can be done for every search result; the app finds out which ones load.
 */
fun landscapeArtUrls(steamAppId: Int?, game: IgdbGame): List<String> = buildList {
    if (steamAppId != null) STEAM_LANDSCAPE_FILES.forEach { add("$STEAM_ASSETS/$steamAppId/$it") }
    game.artworks?.firstOrNull()?.let { add("$IGDB_IMAGES/t_screenshot_big/${it.imageId}.jpg") }
    game.screenshots?.firstOrNull()?.let { add("$IGDB_IMAGES/t_screenshot_big/${it.imageId}.jpg") }
}


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

/**
 * Builds the app-facing game. Pass [timeToBeat] when it is known; the search results skip it. Pass the
 * game's [steamAppId] when it is on Steam, so its Steam pictures lead the landscape artwork list.
 */
fun IgdbGame.toDto(timeToBeat: IgdbTimeToBeat? = null, steamAppId: Int? = null): GameDto {
    val artwork = landscapeArtUrls(steamAppId, this)
    return GameDto(
        gameId = id,
        title = name,
        coverImageUrl = coverUrl(cover?.imageId),
        platforms = normalisePlatforms(platforms.orEmpty().map { it.name }),
        avgCompletionHours = secondsToHours(timeToBeat?.normally),
        avg100PercentHours = secondsToHours(timeToBeat?.completely),
        artworkUrl = artwork.firstOrNull(),
        genres = genres.orEmpty().map { it.name },
        artworkUrls = artwork,
    )
}

