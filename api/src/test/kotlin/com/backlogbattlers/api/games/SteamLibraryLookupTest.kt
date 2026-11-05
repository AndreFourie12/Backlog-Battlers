package com.backlogbattlers.api.games

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Reading a Steam library, and looking big ones up in IGDB in as few requests as it allows. */
class SteamLibraryLookupTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // ---- Steam's GetOwnedGames ----

    private val steamRequests = mutableListOf<HttpRequestData>()

    private fun steam(status: HttpStatusCode = HttpStatusCode.OK, body: String = "", apiKey: String = "steam-key") = SteamClient(
        HttpClient(
            MockEngine { request ->
                steamRequests += request
                if (status != HttpStatusCode.OK) respondError(status) else respond(body, status, jsonHeaders)
            },
        ),
        apiKey = apiKey,
    )

    @Test
    fun `owned games come back with minutes played, from one request that leaves names out`() = runBlocking {
        val body = """{"response":{"game_count":2,"games":[{"appid":620,"playtime_forever":300,"playtime_2weeks":12},{"appid":730}]}}"""

        val owned = steam(body = body).ownedGames("76561198000000001")

        assertEquals(OwnedGames.Owned(listOf(OwnedGame(620, 300), OwnedGame(730, 0))), owned)
        val request = steamRequests.single()
        assertEquals("/IPlayerService/GetOwnedGames/v1/", request.url.encodedPath)
        assertEquals("76561198000000001", request.url.parameters["steamid"])
        assertEquals("false", request.url.parameters["include_appinfo"])
        assertEquals("true", request.url.parameters["include_played_free_games"])
    }

    @Test
    fun `a library with no games is empty, not private`() = runBlocking {
        assertEquals(OwnedGames.Owned(emptyList()), steam(body = """{"response":{"game_count":0}}""").ownedGames("76561198000000001"))
    }

    @Test
    fun `steam leaving the list out entirely, or refusing, means the profile is private`() = runBlocking {
        assertEquals(OwnedGames.PrivateProfile, steam(body = """{"response":{}}""").ownedGames("76561198000000001"))
        assertEquals(OwnedGames.PrivateProfile, steam(status = HttpStatusCode.Forbidden).ownedGames("76561198000000001"))
        assertEquals(OwnedGames.PrivateProfile, steam(status = HttpStatusCode.Unauthorized).ownedGames("76561198000000001"))
    }

    @Test
    fun `an outage is an error, never an empty library, and the message never shows the key`() {
        val error = assertFailsWith<SteamException> {
            runBlocking { steam(status = HttpStatusCode.InternalServerError).ownedGames("76561198000000001") }
        }
        assertFalse(error.message.orEmpty().contains("steam-key"))
    }

    @Test
    fun `without a steam key nothing is sent`() {
        assertFailsWith<SteamException> { runBlocking { steam(apiKey = "").ownedGames("76561198000000001") } }
        assertEquals(0, steamRequests.size)
    }

    // ---- IGDB in batches ----

    private val igdbQueries = mutableListOf<Pair<String, String>>() // endpoint to query body

    private val igdb = IgdbClient(
        igdbHttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/oauth2/token") {
                    return@MockEngine respond("""{"access_token":"tok","expires_in":3600,"token_type":"bearer"}""", HttpStatusCode.OK, jsonHeaders)
                }
                val query = (request.body as TextContent).text
                igdbQueries += request.url.encodedPath.removePrefix("/v4/") to query
                val asked = Regex("\\((.*?)\\)").find(query)!!.groupValues[1].split(",").map { it.trim('"', ' ').toInt() }
                val body = when (request.url.encodedPath) {
                    "/v4/external_games" -> asked.map { """{"game":${it + 1_000_000},"uid":"$it"}""" }
                    "/v4/games" -> asked.map { """{"id":$it,"name":"Game $it"}""" }
                    else -> asked.map { """{"game_id":$it,"normally":3600}""" }
                }
                respond(body.joinToString(",", "[", "]"), HttpStatusCode.OK, jsonHeaders)
            },
        ),
        clientId = "id",
        clientSecret = "secret",
    )

    @Test
    fun `a library of 600 steam apps is matched in three requests of up to 250`() = runBlocking {
        val apps = (1..600).toList()

        val matched = igdb.gameIdsForSteamApps(apps + apps.take(10)) // repeats are asked about once

        assertEquals(600, matched.size)
        assertEquals(1_000_555, matched[555]) // an app from the last batch
        val appsPerRequest = igdbQueries.map { (_, query) -> Regex("\\((.*?)\\)").find(query)!!.groupValues[1].split(",").size }
        assertEquals(listOf(250, 250, 100), appsPerRequest)
    }

    @Test
    fun `details and time to beat for 600 games take two requests each`() = runBlocking {
        val ids = (1..600).toList()

        val games = igdb.games(ids)
        val timeToBeat = igdb.timeToBeats(ids)

        assertEquals(600, games.size)
        assertEquals(600, timeToBeat.size)
        assertEquals(listOf("games", "games", "game_time_to_beats", "game_time_to_beats"), igdbQueries.map { it.first })
    }

    @Test
    fun `nothing to look up sends nothing`() = runBlocking {
        assertEquals(emptyMap(), igdb.gameIdsForSteamApps(emptyList()))
        assertEquals(emptyList(), igdb.games(emptyList()))
        assertEquals(emptyMap(), igdb.timeToBeats(emptyList()))
        assertEquals(0, igdbQueries.size)
    }
}
//------------------------------EOF------------------------------\\
