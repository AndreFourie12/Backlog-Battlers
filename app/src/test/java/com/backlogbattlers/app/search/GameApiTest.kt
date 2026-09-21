package com.backlogbattlers.app.search

import com.backlogbattlers.app.data.remote.GameApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val warResults = """
        [
          {"gameId":26192,"title":"God of War Ragnarök","coverImageUrl":"https://images.igdb.com/cover/a.jpg",
           "artworkUrl":"https://shared.akamai.steamstatic.com/2322010/capsule_616x353.jpg",
           "platforms":["PLAYSTATION"],"genres":["Adventure"],"avgCompletionHours":25.5,"avg100PercentHours":50.0},
          {"gameId":50275,"title":"Total War: Warhammer III","coverImageUrl":null,
           "platforms":["PC"],"genres":["Strategy"]}
        ]
    """.trimIndent()

    private val seen = mutableListOf<HttpRequestData>()

    private fun api(body: String = warResults, status: HttpStatusCode = HttpStatusCode.OK): GameApi {
        val client = HttpClient(
            MockEngine { request ->
                seen += request
                respond(body, status, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true }, contentType = ContentType.Any)
            }
        }
        return GameApi(client, baseUrl = "http://10.0.2.2:8080")
    }

    @Test
    fun `searching asks the catalogue endpoint for the query and reads the games back`() = runBlocking {
        val games = api().searchGames("war", limit = 50)

        val url = seen.single().url
        assertEquals("/games/search", url.encodedPath)
        assertEquals("war", url.parameters["q"])
        assertEquals("50", url.parameters["limit"])

        assertEquals(2, games.size)
        assertEquals("God of War Ragnarök", games[0].title)
        assertEquals(listOf("PLAYSTATION"), games[0].platforms)
        assertEquals(listOf("Adventure"), games[0].genres)
        assertTrue(games[0].artworkUrl!!.endsWith("capsule_616x353.jpg"))

        assertEquals(null, games[1].coverImageUrl)
        assertEquals(null, games[1].artworkUrl)
        assertEquals(null, games[1].avgCompletionHours)
    }

    @Test
    fun `browsing sends the category instead of a query`() = runBlocking {
        api().browseGames("rpg", limit = 50)

        val url = seen.single().url
        assertEquals("/games/search", url.encodedPath)
        assertEquals("rpg", url.parameters["category"])
        assertEquals(null, url.parameters["q"])
    }

    @Test
    fun `a query with spaces and punctuation is sent as one parameter`() = runBlocking {
        api().searchGames("baldur's gate 3", limit = 10)

        assertEquals("baldur's gate 3", seen.single().url.parameters["q"])
    }

    @Test
    fun `a server error is reported rather than read as an empty result`() {
        val api = api(body = """{"error":"The game catalogue is unavailable right now"}""", status = HttpStatusCode.BadGateway)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { api.searchGames("war", limit = 50) }
        }
        assertTrue(error.message!!.contains("502"))
    }
}
