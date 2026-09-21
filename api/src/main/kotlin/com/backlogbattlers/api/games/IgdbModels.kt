package com.backlogbattlers.api.games

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These classes mirror the JSON that IGDB returns for the fields we ask for.
// IGDB leaves a field out entirely when it has no value (e.g. a game with no cover has no
// "cover" key), so every optional field needs a default of null or decoding would crash.

/** A game's cover picture. IGDB only gives an id; see [coverUrl] for the full address. */
@Serializable
data class IgdbCover(
    @SerialName("image_id") val imageId: String,
)

@Serializable
data class IgdbPlatform(
    val name: String,
)

@Serializable
data class IgdbGenre(
    val name: String,
)

/** One game from `POST /v4/games`. */
@Serializable
data class IgdbGame(
    val id: Int,
    val name: String,
    val cover: IgdbCover? = null,
    val platforms: List<IgdbPlatform>? = null,
    val genres: List<IgdbGenre>? = null,
)

/** One row from `POST /v4/game_time_to_beats`. Times are in seconds. */
@Serializable
data class IgdbTimeToBeat(
    @SerialName("game_id") val gameId: Int,
    /** Average time to finish the main story. */
    val normally: Int? = null,
    /** Average time to reach 100% completion. */
    val completely: Int? = null,
    /** How many players submitted a time; a low count means the average is unreliable. */
    val count: Int? = null,
)

/** One row from `POST /v4/external_games`: the id a game has in another store. */
@Serializable
data class IgdbExternalGame(
    val game: Int,
    /** For the Steam source this is the Steam app id, as text. */
    val uid: String,
)
