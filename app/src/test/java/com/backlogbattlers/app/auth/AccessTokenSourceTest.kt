package com.backlogbattlers.app.auth

import com.backlogbattlers.app.data.remote.FriendsApi
import com.backlogbattlers.app.data.remote.LeaderboardApi
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.repository.AccessTokenSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// the access token expires after an hour, and the backend can refuse a token that looks fine.
// both must end in a renewed token and a retry, or in the user being signed out
class AccessTokenSourceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    // the fake session: what the stored token is, what a renewal gives, and what was called
    private var stored: String? = "old"
    private var renewal: String? = "new"
    private var renewals = 0
    private var sessionsEnded = 0

    private val tokens = AccessTokenSource(
        fresh = { stored },
        renew = { renewals++; renewal },
        endSession = { sessionsEnded++ },
    )

    //------------------------------
    // the helper itself

    @Test
    fun `a valid token is used as it is, with no renewal`() = runBlocking {
        val used = mutableListOf<String>()

        val result = tokens.call { token -> used += token; "ok" }

        assertEquals("ok", result)
        assertEquals(listOf("old"), used)
        assertEquals(0, renewals)
    }

    @Test
    fun `a token the backend refuses is renewed once and the request is sent again`() = runBlocking {
        val used = mutableListOf<String>()

        val result = tokens.call { token ->
            used += token
            if (token == "old") throw SessionExpiredException()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf("old", "new"), used)
        assertEquals(1, renewals)
        assertEquals("a working session is not ended", 0, sessionsEnded)
    }

    @Test
    fun `nobody signed in fails at once without sending anything`() {
        stored = null
        var sent = false

        assertThrows(SessionExpiredException::class.java) {
            runBlocking { tokens.call { sent = true } }
        }

        assertFalse(sent)
        assertEquals(0, renewals)
    }

    @Test
    fun `when the renewal fails the request is not sent a second time`() {
        renewal = null // the repository has already ended the session by the time it returns null
        var sent = 0

        assertThrows(SessionExpiredException::class.java) {
            runBlocking { tokens.call { sent++; throw SessionExpiredException() } }
        }

        assertEquals(1, sent)
        assertEquals(1, renewals)
    }

    @Test
    fun `a brand new token that is refused too ends the session`() {
        var sent = 0

        assertThrows(SessionExpiredException::class.java) {
            runBlocking { tokens.call { sent++; throw SessionExpiredException() } }
        }

        assertEquals(2, sent)
        assertEquals(1, renewals)
        assertEquals(1, sessionsEnded)
    }

    @Test
    fun `any other failure is passed on without a renewal or ending the session`() {
        assertThrows(IOException::class.java) {
            runBlocking { tokens.call { throw IOException("no connection") } }
        }

        assertEquals(0, renewals)
        assertEquals(0, sessionsEnded)
    }

    //------------------------------
    // the Friends and Standings clients, which used to send whatever token was stored

    private val seen = mutableListOf<HttpRequestData>()
    private var serverStatusForOld = HttpStatusCode.Unauthorized

    // the backend: only the token "new" is accepted; "old" gets serverStatusForOld
    private fun client() = HttpClient(
        MockEngine { request ->
            seen += request
            val bearer = request.headers[HttpHeaders.Authorization]
            when {
                bearer == "Bearer new" && request.url.encodedPath.endsWith("/leaderboard/monthly") ->
                    respond(
                        """{"monthPeriod":"2026-11","seasonEndsAt":1,"entries":[],"me":null}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                bearer == "Bearer new" && request.url.encodedPath.endsWith("/reject") -> respond("", HttpStatusCode.NoContent)
                bearer == "Bearer new" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> respondError(serverStatusForOld)
            }
        },
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun bearers() = seen.map { it.headers[HttpHeaders.Authorization] }

    @Test
    fun `friends load after the token expired, with one renewal and one retry`() = runBlocking {
        val friends = FriendsApi(client(), "https://api.example.test", tokens).getFriends()

        assertEquals(emptyList<Any>(), friends)
        assertEquals(listOf("Bearer old", "Bearer new"), bearers())
        assertEquals(1, renewals)
    }

    @Test
    fun `the standings load after the token expired, with one renewal and one retry`() = runBlocking {
        val board = LeaderboardApi(client(), "https://api.example.test", tokens).getMonthlyLeaderboard()

        assertEquals("2026-11", board.monthPeriod)
        assertEquals(listOf("Bearer old", "Bearer new"), bearers())
    }

    @Test
    fun `rejecting a request, which has no body to read, is retried too`() = runBlocking {
        FriendsApi(client(), "https://api.example.test", tokens).rejectRequest("r1")

        assertEquals(listOf("Bearer old", "Bearer new"), bearers())
    }

    @Test
    fun `a session the backend keeps refusing signs the user out instead of looping`() {
        renewal = "also-bad" // the backend refuses this one as well

        assertThrows(SessionExpiredException::class.java) {
            runBlocking { FriendsApi(client(), "https://api.example.test", tokens).getFriends() }
        }

        assertEquals(listOf("Bearer old", "Bearer also-bad"), bearers())
        assertEquals(1, sessionsEnded)
    }

    @Test
    fun `a server error is not mistaken for an expired session`() {
        serverStatusForOld = HttpStatusCode.InternalServerError

        assertThrows(IllegalStateException::class.java) {
            runBlocking { LeaderboardApi(client(), "https://api.example.test", tokens).getMonthlyLeaderboard() }
        }

        assertEquals(1, seen.size)
        assertEquals(0, renewals)
        assertTrue(sessionsEnded == 0)
    }
}
