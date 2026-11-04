package com.backlogbattlers.api.games

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameMappingTest {

    // The same setting the real IGDB client will use: skip fields we did not declare
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `platforms are normalised to the app enum with duplicates removed`() {
        // Hades' real IGDB platform list
        val igdbNames = listOf(
            "Xbox Series X|S", "PlayStation 4", "PC (Microsoft Windows)", "iOS",
            "PlayStation 5", "Mac", "Xbox One", "Nintendo Switch",
        )

        assertEquals(
            listOf("XBOX", "PLAYSTATION", "PC", "OTHER", "SWITCH"),
            normalisePlatforms(igdbNames),
        )
        // "PC Engine" is a Nintendo-era console, not a Windows PC
        assertEquals(listOf("OTHER"), normalisePlatforms(listOf("PC Engine")))
    }

    @Test
    fun `cover url is built from the image id and is null when there is no cover`() {
        assertNull(coverUrl(null))
        assertEquals(
            "https://images.igdb.com/igdb/image/upload/t_cover_big/cob9kr.jpg",
            coverUrl("cob9kr"),
        )
    }

    @Test
    fun `seconds convert to hours and implausible values are dropped`() {
        assertEquals(1.0, secondsToHours(3600))
        assertEquals(70.44, secondsToHours(253_600)!!, 0.01) // Hades, main story
        assertNull(secondsToHours(null))
        assertNull(secondsToHours(0))
        assertNull(secondsToHours(23_820_010)) // 6,617 hours: bad IGDB data
    }

    @Test
    fun `a real IGDB response decodes, including a game with no cover or platforms`() {
        val response = """
            [
              {"id":113112,"cover":{"id":525627,"image_id":"cob9kr"},"name":"Hades",
               "platforms":[{"id":169,"name":"Xbox Series X|S"},{"id":6,"name":"PC (Microsoft Windows)"}]},
              {"id":999,"name":"No Cover Game"}
            ]
        """.trimIndent()

        val games = json.decodeFromString<List<IgdbGame>>(response)

        assertEquals(2, games.size)
        assertEquals("Hades", games[0].name)
        assertEquals("cob9kr", games[0].cover?.imageId)
        assertEquals(2, games[0].platforms?.size)
        assertNull(games[1].cover)
        assertNull(games[1].platforms)
    }

    @Test
    fun `an IGDB game and its time to beat become the app's game`() {
        val hades = IgdbGame(
            id = 113112,
            name = "Hades",
            cover = IgdbCover("cob9kr"),
            platforms = listOf(IgdbPlatform("PlayStation 5"), IgdbPlatform("PC (Microsoft Windows)")),
        )
        val timeToBeat = IgdbTimeToBeat(gameId = 113112, normally = 253_600, completely = 542_700, count = 13)

        val dto = hades.toDto(timeToBeat)

        assertEquals(113112, dto.gameId)
        assertEquals("Hades", dto.title)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/cob9kr.jpg", dto.coverImageUrl)
        assertEquals(listOf("PLAYSTATION", "PC"), dto.platforms)
        assertEquals(70.44, dto.avgCompletionHours!!, 0.01)
        assertEquals(150.75, dto.avg100PercentHours!!, 0.01)

        // Search results have no time-to-beat yet, so the hours stay null
        assertNull(hades.toDto().avgCompletionHours)
    }

    private val steam = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360"
    private val igdbArt = "https://images.igdb.com/igdb/image/upload/t_screenshot_big"

    @Test
    fun `landscape pictures to try are Steam's three wide pictures, then IGDB's artwork and a screenshot`() {
        val hades = IgdbGame(
            id = 113112,
            name = "Hades",
            artworks = listOf(IgdbImage("ar1"), IgdbImage("ar2")),
            screenshots = listOf(IgdbImage("sc1"), IgdbImage("sc2")),
        )

        val dto = hades.toDto(steamAppId = 1145360)

        assertEquals(
            listOf(
                "$steam/capsule_616x353.jpg",
                "$steam/header.jpg",
                "$steam/library_hero.jpg",
                "$igdbArt/ar1.jpg",
                "$igdbArt/sc1.jpg",
            ),
            dto.artworkUrls,
        )
        // older app builds only read the first one
        assertEquals("$steam/capsule_616x353.jpg", dto.artworkUrl)
    }

    @Test
    fun `a game not on Steam starts with IGDB's pictures, and one with no pictures has none to try`() {
        val withArt = IgdbGame(id = 1, name = "Console only", screenshots = listOf(IgdbImage("sc1")))
        assertEquals(listOf("$igdbArt/sc1.jpg"), withArt.toDto().artworkUrls)
        assertEquals("$igdbArt/sc1.jpg", withArt.toDto().artworkUrl)

        val bare = IgdbGame(id = 2, name = "Unknown").toDto()
        assertEquals(emptyList(), bare.artworkUrls)
        assertNull(bare.artworkUrl)
    }

    @Test
    fun `IGDB artworks and screenshots are read from its JSON`() {
        val game = json.decodeFromString<IgdbGame>(
            """{"id":7344,"name":"Ori","artworks":[{"id":9,"image_id":"arx"}],"screenshots":[{"id":8,"image_id":"scx"}]}""",
        )

        assertEquals("arx", game.artworks!!.single().imageId)
        assertEquals("scx", game.screenshots!!.single().imageId)
    }
}
