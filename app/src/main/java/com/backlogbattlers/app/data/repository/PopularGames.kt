package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.domain.model.Game

// the hand picked games offered under an empty library, in the order they are shown.
// like the API's starter games on the home screen, only built in, so the suggestions are there at once
// and need no request. The ids are IGDB's, the same ones the API returns for these games (search lists
// other editions and spin offs too, so they are pinned), and the rest is what the API sends for them,
// the art being the Steam capsule it links
internal val POPULAR_GAMES: List<Game> = listOf(
    Game(
        gameId = 1942,
        title = "The Witcher 3: Wild Hunt",
        coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/coaarl.jpg",
        artworkUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/292030/capsule_616x353.jpg",
        platforms = listOf("XBOX", "PLAYSTATION", "PC", "SWITCH"),
        genres = listOf("Role-playing (RPG)", "Adventure"),
        avgCompletionHours = null,
        avg100PercentHours = null,
        cachedAt = 0L,
    ),
    Game(
        gameId = 9061,
        title = "Cuphead",
        coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co62ao.jpg",
        artworkUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/268910/capsule_616x353.jpg",
        platforms = listOf("PLAYSTATION", "PC", "OTHER", "XBOX", "SWITCH"),
        genres = listOf("Shooter", "Platform", "Adventure", "Indie", "Arcade"),
        avgCompletionHours = null,
        avg100PercentHours = null,
        cachedAt = 0L,
    ),
    Game(
        gameId = 119133,
        title = "Elden Ring",
        coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg",
        artworkUrl = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1245620/capsule_616x353.jpg",
        platforms = listOf("XBOX", "PLAYSTATION", "SWITCH", "PC"),
        genres = listOf("Role-playing (RPG)", "Adventure"),
        avgCompletionHours = null,
        avg100PercentHours = null,
        cachedAt = 0L,
    ),
)
