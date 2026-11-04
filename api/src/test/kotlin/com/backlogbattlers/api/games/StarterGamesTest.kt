package com.backlogbattlers.api.games

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The home screen suggestions: 3 random picks from the popular single player games on Steam's most
 * played chart, falling back to the hand picked games. Steam, Twitch and IGDB are all faked.
 */
class StarterGamesTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""

    // Steam's chart, most played first: two multiplayer-only games, four single player games, and an app IGDB doesn't know
    private val chart = listOf(730, 570, 1145360, 261570, 814380, 1245620, 999999)
    private val igdbIdByApp = mapOf(
        730 to 241, // Counter-Strike 2
        570 to 2963, // Dota 2
        1145360 to 113112, // Hades
        261570 to 7344, // Ori and the Blind Forest
        814380 to 76882, // Sekiro: Shadows Die Twice
        1245620 to 119133, // Elden Ring
    )
    private val names = mapOf(241 to "Counter-Strike 2", 2963 to "Dota 2", 113112 to "Hades", 7344 to "Ori and the Blind Forest", 76882 to "Sekiro: Shadows Die Twice", 119133 to "Elden Ring")
    // what IGDB's game_modes filter lets through
    private var singlePlayerIds = setOf(113112, 7344, 76882, 119133)

    private var steamStatus = HttpStatusCode.OK
    private val seen = mutableListOf<HttpRequestData>()
    private var now = Instant.parse("2026-11-03T08:00:00Z")

    private fun bodyOf(request: HttpRequestData): String = (request.body as? TextContent)?.text.orEmpty()

    private fun gameJson(id: Int) = """{"id":$id,"name":"${names.getValue(id)}","platforms":[{"id":6,"name":"PC (Microsoft Windows)"}]}"""

    private val services: MockRequestHandler = { request ->
        seen += request
        val body = bodyOf(request)
        when {
            request.url.host == "id.twitch.tv" -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)

            request.url.host == "api.steampowered.com" -> respond(
                """{"response":{"ranks":[${chart.withIndex().joinToString(",") { (i, app) -> """{"rank":${i + 1},"appid":$app}""" }}]}}""",
                steamStatus,
                jsonHeaders,
            )

            // Steam app ids to IGDB games (the popular pool)
            request.url.encodedPath == "/v4/external_games" && body.contains("where uid") -> respond(
                igdbIdByApp.entries.joinToString(",", "[", "]") { (app, game) -> """{"id":$game,"game":$game,"uid":"$app"}""" },
                HttpStatusCode.OK,
                jsonHeaders,
            )

            // IGDB games to Steam app ids (the hand picks' artwork)
            request.url.encodedPath == "/v4/external_games" -> {
                val gameId = Regex("""where game = \((\d+)\)""").find(body)!!.groupValues[1].toInt()
                val app = igdbIdByApp.entries.first { it.value == gameId }.key
                respond("""[{"id":1,"game":$gameId,"uid":"$app"}]""", HttpStatusCode.OK, jsonHeaders)
            }

            // the single player filter
            request.url.encodedPath == "/v4/games" && body.contains("game_modes") ->
                respond(singlePlayerIds.joinToString(",", "[", "]") { gameJson(it) }, HttpStatusCode.OK, jsonHeaders)

            // one hand picked game by id
            request.url.encodedPath == "/v4/games" -> {
                val id = Regex("""where id = (\d+);""").find(body)!!.groupValues[1].toInt()
                respond("[${gameJson(id)}]", HttpStatusCode.OK, jsonHeaders)
            }

            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private val igdb = IgdbClient(igdbHttpClient(MockEngine(services)), "id", "secret", clock = { now })
    private val steam = SteamClient(HttpClient(MockEngine(services)), apiKey = "")

    private fun starterGames(withSteam: Boolean = true) =
        StarterGames(igdb, if (withSteam) steam else null, Random(42), clock = { now })

    private fun steamRequests() = seen.count { it.url.host == "api.steampowered.com" }

    @Test
    fun `three popular single player games are suggested, best ranked first`() = runBlocking {
        val starter = starterGames()
        val picks = starter.load()

        assertEquals(3, picks.size)
        assertEquals(3, picks.map { it.game.gameId }.distinct().size)
        // only the single player games from the chart, never CS2, Dota 2 or the app IGDB doesn't know
        assertTrue(picks.all { it.game.gameId in singlePlayerIds })
        assertEquals(picks.sortedBy { it.steamRank }, picks)

        // Hades is 3rd on the chart (behind the two multiplayer games), and its art is its Steam capsule
        val hades = (1..20).flatMap { starter.load() }.first { it.game.gameId == 113112 }
        assertEquals(3, hades.steamRank)
        assertEquals("#3 most played on Steam right now", hades.reason)
        assertEquals(steamArtUrl(1145360), hades.game.artworkUrl)
    }

    @Test
    fun `the picks change between requests but the chart is fetched once`() = runBlocking {
        val starter = starterGames()

        val draws = (1..10).map { starter.load().map { it.game.gameId }.toSet() }

        assertTrue(draws.toSet().size > 1, "10 requests all got the same 3 games")
        assertTrue(draws.flatten().all { it in singlePlayerIds })
        assertEquals(1, steamRequests())
    }

    @Test
    fun `the chart is fetched again once the pool is 6 hours old`() = runBlocking {
        val starter = starterGames()
        starter.load()

        now = now.plus(Duration.ofHours(5))
        starter.load()
        assertEquals(1, steamRequests())

        now = now.plus(Duration.ofHours(2))
        starter.load()
        assertEquals(2, steamRequests())
    }

    @Test
    fun `when Steam is down the hand picked games are suggested, and Steam is asked again 10 minutes later`() = runBlocking {
        steamStatus = HttpStatusCode.ServiceUnavailable
        val starter = starterGames()

        val picks = starter.load()

        assertEquals(listOf(76882, 7344, 113112), picks.map { it.game.gameId })
        assertTrue(picks.all { it.steamRank == null })
        assertEquals("Highly rated by the community", picks.first().reason)

        now = now.plus(Duration.ofMinutes(5))
        starter.load()
        assertEquals(1, steamRequests())

        steamStatus = HttpStatusCode.OK
        now = now.plus(Duration.ofMinutes(6))
        assertTrue(starter.load().all { it.steamRank != null })
        assertEquals(2, steamRequests())
    }

    @Test
    fun `too few single player games on the chart falls back to the hand picks`() = runBlocking {
        singlePlayerIds = setOf(113112, 7344)

        val picks = starterGames().load()

        assertEquals(listOf(76882, 7344, 113112), picks.map { it.game.gameId })
    }

    @Test
    fun `without a Steam client the hand picks are used and Steam is never asked`() = runBlocking {
        val picks = starterGames(withSteam = false).load()

        assertEquals(listOf(76882, 7344, 113112), picks.map { it.game.gameId })
        assertEquals(0, steamRequests())
    }
}
//------------------------------EOF------------------------------\\
