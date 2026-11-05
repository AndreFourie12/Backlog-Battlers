package com.backlogbattlers.app.steam

import com.backlogbattlers.app.R
import com.backlogbattlers.app.data.remote.GameDto
import com.backlogbattlers.app.data.remote.SteamApi
import com.backlogbattlers.app.data.repository.SteamAccountLoad
import com.backlogbattlers.app.data.repository.SteamAccountRepository
import com.backlogbattlers.app.data.repository.SteamAccountRepositoryImpl
import com.backlogbattlers.app.data.repository.SteamAccountResult
import com.backlogbattlers.app.data.repository.SteamImportFailure
import com.backlogbattlers.app.data.repository.SteamImportOutcome
import com.backlogbattlers.app.viewmodels.SteamAccountViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

// importing the games owned on Steam: the repository that calls the API, caches the games it sent
// and syncs the library, and the view model behind the Import games button
class SteamImportTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val steamId = "76561198000000001"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    //------------------------------
    // the repository, against a fake of the server

    private val seen = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var errorBody: String? = null // the body of an error answer, when the test needs one
    private var failure: Throwable? = null
    private var importBody = """
        {"ownedOnSteam":3,"added":2,"hoursUpdated":1,"unchanged":0,"notInCatalogue":0,
         "games":[{"gameId":1,"title":"Hades"},{"gameId":2,"title":"Celeste"}]}
    """.trimIndent()
    private val cached = mutableListOf<GameDto>()
    private var syncs = 0

    private val repository = SteamAccountRepositoryImpl(
        SteamApi(
            HttpClient(
                MockEngine { request ->
                    seen += request
                    failure?.let { throw it }
                    if (status != HttpStatusCode.OK) {
                        return@MockEngine errorBody?.let { respond(it, status, jsonHeaders) } ?: respondError(status)
                    }
                    respond(importBody, HttpStatusCode.OK, jsonHeaders)
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            baseUrl = "https://api.example.test",
        ),
        accessToken = { "token-123" },
        cacheGames = { cached += it },
        syncLibrary = { syncs++ },
    )

    @Test
    fun `an import posts once, caches the games it added, then syncs the library`() = runBlocking {
        val outcome = repository.importGames()

        assertEquals(SteamImportOutcome.Imported(added = 2, hoursUpdated = 1), outcome)
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/users/me/steam/import", request.url.encodedPath)
        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(1, 2), cached.map { it.gameId })
        assertEquals(1, syncs)
    }

    @Test
    fun `an import that changed nothing skips the library sync`() = runBlocking {
        importBody = """{"ownedOnSteam":3,"added":0,"hoursUpdated":0,"unchanged":3,"notInCatalogue":0,"games":[]}"""

        assertEquals(SteamImportOutcome.Imported(added = 0, hoursUpdated = 0), repository.importGames())
        assertEquals(0, syncs)
    }

    @Test
    fun `each refusal from the server becomes its own reason, and nothing is synced`() = runBlocking {
        mapOf(
            HttpStatusCode.NotFound to SteamImportFailure.UNAVAILABLE, // a bare 404: a server without the import yet
            HttpStatusCode.Forbidden to SteamImportFailure.PRIVATE_PROFILE,
            HttpStatusCode.TooManyRequests to SteamImportFailure.TOO_SOON,
            HttpStatusCode.BadGateway to SteamImportFailure.UNAVAILABLE,
            HttpStatusCode.Unauthorized to SteamImportFailure.SIGN_IN_AGAIN,
            HttpStatusCode.InternalServerError to SteamImportFailure.FAILED,
        ).forEach { (code, reason) ->
            status = code
            assertEquals("HTTP $code", SteamImportOutcome.Failed(reason), repository.importGames())
        }
        assertEquals(0, syncs)
        assertTrue(cached.isEmpty())
    }

    @Test
    fun `no connection is reported as such`() = runBlocking {
        failure = IOException("offline")

        assertEquals(SteamImportOutcome.Failed(SteamImportFailure.NO_CONNECTION), repository.importGames())
    }

    @Test
    fun `only the API's own not linked answer means not linked`() = runBlocking {
        status = HttpStatusCode.NotFound
        errorBody = """{"error":"No Steam account is linked"}"""

        assertEquals(SteamImportOutcome.Failed(SteamImportFailure.NOT_LINKED), repository.importGames())
    }

    //------------------------------
    // the view model, against a fake repository

    private class FakeRepository : SteamAccountRepository {
        var linked: String? = null
        var importOutcome: SteamImportOutcome? = null // null = never answers, like a slow network
        var imports = 0

        override suspend fun load(): SteamAccountLoad = SteamAccountLoad.Loaded(linked)

        override suspend fun link(steamId: String): SteamAccountResult {
            linked = steamId
            return SteamAccountResult.DONE
        }

        override suspend fun unlink(): SteamAccountResult {
            linked = null
            return SteamAccountResult.DONE
        }

        override suspend fun importGames(): SteamImportOutcome {
            imports++
            return importOutcome ?: awaitCancellation()
        }
    }

    private val fake = FakeRepository()

    @Test
    fun `linking a steam account imports its games straight away`() {
        fake.importOutcome = SteamImportOutcome.Imported(added = 12, hoursUpdated = 3)
        val vm = SteamAccountViewModel(fake)

        vm.link(steamId)

        assertEquals(1, fake.imports)
        assertEquals(R.string.steam_import_done, vm.uiState.value.message)
        assertEquals(listOf(12, 3), vm.uiState.value.messageArgs)
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test
    fun `an import that finds nothing new says the library is up to date`() {
        fake.linked = steamId
        fake.importOutcome = SteamImportOutcome.Imported(added = 0, hoursUpdated = 0)
        val vm = SteamAccountViewModel(fake)

        vm.importGames()

        assertEquals(R.string.steam_import_up_to_date, vm.uiState.value.message)
        assertEquals(emptyList<Int>(), vm.uiState.value.messageArgs)
    }

    @Test
    fun `each failed import shows its own message`() {
        fake.linked = steamId
        val vm = SteamAccountViewModel(fake)
        mapOf(
            SteamImportFailure.NOT_LINKED to R.string.steam_import_not_linked,
            SteamImportFailure.PRIVATE_PROFILE to R.string.steam_import_private,
            SteamImportFailure.TOO_SOON to R.string.steam_import_too_soon,
            SteamImportFailure.UNAVAILABLE to R.string.steam_import_unavailable,
            SteamImportFailure.SIGN_IN_AGAIN to R.string.steam_account_sign_in_again,
            SteamImportFailure.NO_CONNECTION to R.string.steam_account_no_connection,
            SteamImportFailure.FAILED to R.string.steam_account_failed,
        ).forEach { (reason, message) ->
            fake.importOutcome = SteamImportOutcome.Failed(reason)
            vm.importGames()
            assertEquals("$reason", message, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isImporting)
        }
    }

    @Test
    fun `a second tap while an import is running is ignored`() {
        fake.linked = steamId
        val vm = SteamAccountViewModel(fake)

        vm.importGames()
        vm.importGames()

        assertTrue(vm.uiState.value.isImporting)
        assertEquals(1, fake.imports)
    }

    @Test
    fun `the counts are cleared with the message once it has been shown`() {
        fake.linked = steamId
        fake.importOutcome = SteamImportOutcome.Imported(added = 4, hoursUpdated = 0)
        val vm = SteamAccountViewModel(fake)
        vm.importGames()

        vm.messageShown()

        assertEquals(null, vm.uiState.value.message)
        assertEquals(emptyList<Int>(), vm.uiState.value.messageArgs)
    }
}
//------------------------------EOF------------------------------\\
