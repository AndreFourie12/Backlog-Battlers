package com.backlogbattlers.api.games

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every test uses a fake IGDB and fake Twitch, so nothing here touches the network. */
class IgdbClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""
    private val hadesJson = """[{"id":113112,"name":"Hades","cover":{"id":1,"image_id":"cob9kr"}}]"""

    private fun bodyOf(request: HttpRequestData): String = (request.body as TextContent).text

    private fun clientWith(
        clientId: String = "my-id",
        clientSecret: String = "my-secret",
        clock: () -> Instant = { Instant.parse("2026-01-01T00:00:00Z") },
        handler: MockRequestHandler,
    ) = IgdbClient(igdbHttpClient(MockEngine(handler)), clientId, clientSecret, clock)

    /** A fake that answers Twitch with a token and IGDB with [igdbBody], recording every request. */
    private fun fakeServices(
        seen: MutableList<HttpRequestData>,
        igdbBody: String = hadesJson,
        igdbStatus: HttpStatusCode = HttpStatusCode.OK,
    ): MockRequestHandler = { request ->
        seen += request
        if (request.url.host == "id.twitch.tv") {
            respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
        } else {
            respond(igdbBody, igdbStatus, jsonHeaders)
        }
    }

    @Test
    fun `search sends the token, the right query and pages correctly`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val client = clientWith(handler = fakeServices(seen))

        val games = client.search("hades", page = 2, pageSize = 10)

        assertEquals("Hades", games.single().name)
        val igdbRequest = seen.last()
        assertEquals(HttpMethod.Post, igdbRequest.method)
        assertEquals("/v4/games", igdbRequest.url.encodedPath)
        assertEquals("my-id", igdbRequest.headers["Client-ID"])
        assertEquals("Bearer tok", igdbRequest.headers["Authorization"])
        val body = bodyOf(igdbRequest)
        assertTrue(body.contains("""search "hades";"""))
        assertTrue(body.contains("limit 10;"))
        assertTrue(body.contains("offset 10;")) // page 2 skips the first 10 results
    }

    @Test
    fun `the token is reused, then replaced once it has expired`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val client = clientWith(clock = { now }, handler = fakeServices(seen))
        fun tokenRequests() = seen.count { it.url.host == "id.twitch.tv" }

        client.search("hades")
        client.search("hades")
        assertEquals(1, tokenRequests()) // second search reused the cached token

        now = now.plusSeconds(3600) // past the token's lifetime
        client.search("hades")
        assertEquals(2, tokenRequests())
    }

    @Test
    fun `search text cannot break out of its quotes`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val client = clientWith(handler = fakeServices(seen))

        client.search("""x"; where id = 1; fields *; "y""")

        // Only the two quotes we add around the text remain
        assertEquals(2, bodyOf(seen.last()).count { it == '"' })
        // Text that is nothing but quotes is empty after cleaning, so IGDB is never called
        val before = seen.size
        assertEquals(emptyList(), client.search("\"\""))
        assertEquals(before, seen.size)
    }

    @Test
    fun `game and time to beat return null when IGDB has nothing`() = runBlocking {
        val client = clientWith(handler = fakeServices(mutableListOf(), igdbBody = "[]"))

        assertNull(client.game(1))
        assertNull(client.timeToBeat(1))
    }

    @Test
    fun `time to beat decodes IGDB's figures`() = runBlocking {
        val body = """[{"id":2005,"game_id":113112,"normally":253600,"completely":542700,"count":13}]"""
        val client = clientWith(handler = fakeServices(mutableListOf(), igdbBody = body))

        val timeToBeat = client.timeToBeat(113112)

        assertEquals(253_600, timeToBeat?.normally)
        assertEquals(542_700, timeToBeat?.completely)
        assertEquals(13, timeToBeat?.count)
    }

    @Test
    fun `an IGDB error becomes an IgdbException`() {
        val client = clientWith(
            handler = fakeServices(mutableListOf(), igdbBody = "{}", igdbStatus = HttpStatusCode.InternalServerError),
        )

        assertFailsWith<IgdbException> { runBlocking { client.search("hades") } }
    }

    @Test
    fun `missing credentials give a clear error and never call Twitch`() {
        val seen = mutableListOf<HttpRequestData>()
        val client = clientWith(clientId = "", clientSecret = "", handler = fakeServices(seen))

        assertFailsWith<IgdbException> { runBlocking { client.search("hades") } }
        assertTrue(seen.isEmpty())
    }
}
