package com.backlogbattlers.app.games

import com.backlogbattlers.app.data.remote.CompletionApi
import com.backlogbattlers.app.data.repository.AccessTokenSource
import com.backlogbattlers.app.data.repository.CompletionRepositoryImpl
import com.backlogbattlers.app.domain.model.CompleteOutcome
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.HundredPercentBlock
import com.backlogbattlers.app.domain.model.SteamStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// completing a game: what is sent, how each answer of the backend is understood, and when the library
// is uploaded and downloaded around it
class CompletionRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val seen = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var body = """{"pointsAwarded":95,"achievementPoints":25,"completionTimePoints":70,"speedBonusPoints":0,"monthlyPoints":95,"steamStatus":"VERIFIED","unverifiedAchievementIds":["A_LOCKED"]}"""
    private var offline = false
    private var syncs = 0
    private var synced = true

    private val repository = CompletionRepositoryImpl(
        CompletionApi(
            httpClient = HttpClient(
                MockEngine { request ->
                    seen += request
                    if (offline) throw java.io.IOException("offline")
                    respond(body, status, jsonHeaders)
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            baseUrl = "https://api.test",
            tokens = AccessTokenSource(fresh = { "token" }, renew = { "token" }, endSession = { }),
        ),
        syncLibrary = { syncs++ },
        isSynced = { synced },
    )

    @Test
    fun `the entry is completed with the callers token, and the type that was asked for`() = runBlocking {
        repository.complete("entry-1", CompletionType.COMPLETIONIST)

        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/users/me/library/entry-1/complete", request.url.encodedPath)
        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        assertEquals("""{"completionType":"COMPLETIONIST"}""", (request.body as TextContent).text)
    }

    @Test
    fun `a completion reports its points and the achievements Steam did not confirm, then downloads the library`() = runBlocking {
        val outcome = repository.complete("entry-1", CompletionType.MAIN_STORY)

        assertEquals(CompleteOutcome.Completed(CompletionType.MAIN_STORY, 95, 1, SteamStatus.VERIFIED), outcome)
        // once before, so the account knows what this phone ticked, and once after, for the new status
        assertEquals(2, syncs)
    }

    @Test
    fun `a 100 percent the backend refuses says why, and nothing is downloaded`() = runBlocking {
        status = HttpStatusCode.UnprocessableEntity
        body = """{"error":"Every achievement has to be unlocked","reason":"NOT_ALL_UNLOCKED","steamStatus":"VERIFIED","achievementIds":["A","B"]}"""

        val outcome = repository.complete("entry-1", CompletionType.COMPLETIONIST)

        assertEquals(CompleteOutcome.NotReady(HundredPercentBlock.NOT_ALL_UNLOCKED, SteamStatus.VERIFIED, 2), outcome)
        assertEquals(1, syncs)
    }

    @Test
    fun `a reason or Steam status this version does not know is still understood`() = runBlocking {
        status = HttpStatusCode.UnprocessableEntity
        body = """{"reason":"SOMETHING_NEW","steamStatus":"ALSO_NEW","achievementIds":[]}"""

        val outcome = repository.complete("entry-1", CompletionType.COMPLETIONIST)

        assertEquals(CompleteOutcome.NotReady(HundredPercentBlock.UNKNOWN, SteamStatus.UNKNOWN, 0), outcome)
    }

    @Test
    fun `a game already completed that way is reported, and the library is downloaded to catch up`() = runBlocking {
        status = HttpStatusCode.Conflict
        body = """{"error":"This entry was already completed as MAIN_STORY"}"""

        assertEquals(CompleteOutcome.AlreadyCompleted, repository.complete("entry-1", CompletionType.MAIN_STORY))
        assertEquals(2, syncs)
    }

    @Test
    fun `Steam being down is its own outcome, and any other failure or no connection is a failure`() = runBlocking {
        status = HttpStatusCode.BadGateway
        body = """{"error":"Steam is not reachable"}"""
        assertEquals(CompleteOutcome.SteamUnavailable, repository.complete("entry-1", CompletionType.COMPLETIONIST))

        status = HttpStatusCode.InternalServerError
        assertEquals(CompleteOutcome.Failed, repository.complete("entry-1", CompletionType.COMPLETIONIST))

        status = HttpStatusCode.NotFound
        assertEquals(CompleteOutcome.Failed, repository.complete("entry-1", CompletionType.COMPLETIONIST))

        offline = true
        assertEquals(CompleteOutcome.Failed, repository.complete("entry-1", CompletionType.COMPLETIONIST))
    }

    @Test
    fun `changes that could not be uploaded stop the completion before the backend is asked`() = runBlocking {
        synced = false

        val outcome = repository.complete("entry-1", CompletionType.COMPLETIONIST)

        assertEquals(CompleteOutcome.NotSynced, outcome)
        assertTrue(seen.isEmpty())
    }
}
//------------------------------EOF------------------------------\\
