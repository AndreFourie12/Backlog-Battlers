package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.games.GameDto
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.igdbHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** The routes are tested end to end against a fake IGDB and an in-memory database. */
class GameRoutesTest {

    // A fresh in-memory database for every test
    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "game-routes-${Uuid.random()}")

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""
    private val hadesJson = """[{"id":113112,"name":"Hades","cover":{"id":1,"image_id":"cob9kr"},
        "platforms":[{"id":48,"name":"PlayStation 4"},{"id":167,"name":"PlayStation 5"},{"id":6,"name":"PC (Microsoft Windows)"}]}]"""
    private val hadesTimeJson = """[{"id":2005,"game_id":113112,"normally":253600,"completely":542700,"count":13}]"""

    /** A fake IGDB: answers Twitch with a token and the two IGDB endpoints with the given JSON. */
    private fun fakeIgdb(
        games: String = hadesJson,
        timeToBeats: String = hadesTimeJson,
        igdbStatus: HttpStatusCode = HttpStatusCode.OK,
    ) = IgdbClient(
        igdbHttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/oauth2/token" -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                    "/v4/games" -> respond(games, igdbStatus, jsonHeaders)
                    "/v4/game_time_to_beats" -> respond(timeToBeats, igdbStatus, jsonHeaders)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ),
        clientId = "id",
        clientSecret = "secret",
    )

    private fun storedGames() = transaction(db) { Games.selectAll().toList() }

    @Test
    fun `search returns light games with normalised platforms and no hours`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { gameRoutes(fakeIgdb()) }
        }

        val response = client.get("/games/search?q=hades")

        assertEquals(HttpStatusCode.OK, response.status)
        val games = Json.decodeFromString<List<GameDto>>(response.bodyAsText())
        assertEquals(1, games.size)
        assertEquals("Hades", games[0].title)
        assertEquals(listOf("PLAYSTATION", "PC"), games[0].platforms)
        assertEquals(null, games[0].avgCompletionHours)
        assertEquals(0, storedGames().size) // searching does not save anything
    }

    @Test
    fun `search rejects a missing query, a too long query and a bad page`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { gameRoutes(fakeIgdb()) }
        }

        assertEquals(HttpStatusCode.BadRequest, client.get("/games/search").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/games/search?q=%20%20").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/games/search?q=${"a".repeat(101)}").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/games/search?q=hades&page=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/games/search?q=hades&page=abc").status)
    }

    @Test
    fun `game details include hours and the game is saved, then updated on repeat calls`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { gameRoutes(fakeIgdb()) }
        }

        val first = client.get("/games/113112")
        client.get("/games/113112") // a second call must update the row, not add another

        assertEquals(HttpStatusCode.OK, first.status)
        val game = Json.decodeFromString<GameDto>(first.bodyAsText())
        assertEquals(70.44, game.avgCompletionHours!!, 0.01)
        assertEquals(150.75, game.avg100PercentHours!!, 0.01)

        val rows = storedGames()
        assertEquals(1, rows.size)
        assertEquals("Hades", rows[0][Games.title])
        assertEquals(70.44, rows[0][Games.avgCompletionHours]!!, 0.01)
    }

    @Test
    fun `game details reject a non numeric id and report an unknown game`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { gameRoutes(fakeIgdb(games = "[]")) }
        }

        assertEquals(HttpStatusCode.BadRequest, client.get("/games/abc").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/games/1").status)
        assertEquals(0, storedGames().size)
    }

    @Test
    fun `an IGDB outage becomes a 502 that reveals no internal detail`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { gameRoutes(fakeIgdb(igdbStatus = HttpStatusCode.InternalServerError)) }
        }

        val response = client.get("/games/search?q=hades")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        val body = response.bodyAsText()
        assertEquals("""{"error":"The game catalogue is unavailable right now"}""", body)
        assertFalse(body.contains("igdb", ignoreCase = true))
    }
}
