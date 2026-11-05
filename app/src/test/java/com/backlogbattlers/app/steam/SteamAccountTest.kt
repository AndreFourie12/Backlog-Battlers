package com.backlogbattlers.app.steam

import com.backlogbattlers.app.R
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.remote.SteamApi
import com.backlogbattlers.app.data.repository.SteamAccountLoad
import com.backlogbattlers.app.data.repository.SteamAccountRepository
import com.backlogbattlers.app.data.repository.SteamImportOutcome
import com.backlogbattlers.app.data.repository.SteamAccountRepositoryImpl
import com.backlogbattlers.app.data.repository.SteamAccountResult
import com.backlogbattlers.app.data.repository.isValidSteamId
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
import io.ktor.http.content.TextContent
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

// linking the Steam account that achievements are checked against: the rule for a valid ID, the
// repository that talks to the API, and the view model the screen reads
class SteamAccountTest {

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
    // the ID rule

    @Test
    fun `a steam id is 17 digits starting with 7656`() {
        assertTrue(isValidSteamId(steamId))
        assertTrue(isValidSteamId("76561202255233023")) // the highest player account

        listOf(
            "", "123", "7656119800000000", "765611980000000012", "76561198000000A01",
            "12345678901234567", " 76561198000000001", "76561198000000001 ", "https://steamcommunity.com/id/gaben",
        ).forEach { assertFalse("'$it' must be refused", isValidSteamId(it)) }
    }

    //------------------------------
    // the repository, against a fake of the server

    private val seen = mutableListOf<HttpRequestData>()
    private var linkedOnServer: String? = null
    private var failure: Throwable? = null
    private var failStatus: HttpStatusCode? = null
    private var takenByAnotherUser = false
    private var signedIn = true

    private val repository = SteamAccountRepositoryImpl(
        SteamApi(
            HttpClient(
                MockEngine { request ->
                    seen += request
                    failure?.let { throw it }
                    failStatus?.let { return@MockEngine respondError(it) }
                    when (request.method) {
                        HttpMethod.Get ->
                            linkedOnServer?.let { respond("""{"steamId":"$it"}""", HttpStatusCode.OK, jsonHeaders) }
                                ?: respondError(HttpStatusCode.NotFound)
                        HttpMethod.Put -> if (takenByAnotherUser) {
                            respondError(HttpStatusCode.Conflict)
                        } else {
                            val sent = Json.parseToJsonElement((request.body as TextContent).text).toString()
                            linkedOnServer = Regex("\"steamId\":\"(\\d+)\"").find(sent)!!.groupValues[1]
                            respond(sent, HttpStatusCode.OK, jsonHeaders)
                        }
                        HttpMethod.Delete -> {
                            val had = linkedOnServer != null
                            linkedOnServer = null
                            if (had) respond("", HttpStatusCode.NoContent) else respondError(HttpStatusCode.NotFound)
                        }
                        else -> respondError(HttpStatusCode.MethodNotAllowed)
                    }
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            baseUrl = "https://api.example.test",
        ),
        accessToken = { if (signedIn) "token-123" else null },
    )

    @Test
    fun `load reads the linked id, or null when nothing is linked`() = runBlocking {
        assertEquals(SteamAccountLoad.Loaded(null), repository.load())

        linkedOnServer = steamId
        assertEquals(SteamAccountLoad.Loaded(steamId), repository.load())
        assertEquals("Bearer token-123", seen.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `link sends the id and unlink removes it, and unlinking nothing still counts as done`() = runBlocking {
        assertEquals(SteamAccountResult.DONE, repository.link(steamId))
        assertEquals(steamId, linkedOnServer)

        assertEquals(SteamAccountResult.DONE, repository.unlink())
        assertNull(linkedOnServer)
        assertEquals(SteamAccountResult.DONE, repository.unlink())
    }

    @Test
    fun `a steam account another user has linked is reported as taken`() = runBlocking {
        takenByAnotherUser = true

        assertEquals(SteamAccountResult.TAKEN, repository.link(steamId))
    }

    @Test
    fun `every way a request can fail becomes its own result`() = runBlocking {
        signedIn = false
        assertEquals(SteamAccountResult.SIGN_IN_AGAIN, repository.link(steamId))
        assertTrue("nobody signed in means nothing is sent", seen.isEmpty())
        signedIn = true

        failure = SessionExpiredException()
        assertEquals(SteamAccountResult.SIGN_IN_AGAIN, repository.unlink())

        failure = IOException("no route")
        assertEquals(SteamAccountResult.NO_CONNECTION, repository.link(steamId))
        assertEquals(SteamAccountLoad.Failed(SteamAccountResult.NO_CONNECTION), repository.load())

        failure = null
        failStatus = HttpStatusCode.InternalServerError
        assertEquals(SteamAccountResult.FAILED, repository.link(steamId))
        assertEquals(SteamAccountLoad.Failed(SteamAccountResult.FAILED), repository.load())
    }

    @Test
    fun `a 401 from the server means sign in again`() = runBlocking {
        failStatus = HttpStatusCode.Unauthorized

        assertEquals(SteamAccountResult.SIGN_IN_AGAIN, repository.link(steamId))
    }

    //------------------------------
    // the view model, against a fake repository

    private class FakeRepository : SteamAccountRepository {
        var linked: String? = null
        var loadResult: SteamAccountLoad? = null // null = read `linked`
        var actionResult = SteamAccountResult.DONE
        var calls = 0

        override suspend fun load(): SteamAccountLoad = loadResult ?: SteamAccountLoad.Loaded(linked)

        override suspend fun link(steamId: String): SteamAccountResult {
            calls++
            if (actionResult == SteamAccountResult.DONE) linked = steamId
            return actionResult
        }

        override suspend fun unlink(): SteamAccountResult {
            calls++
            if (actionResult == SteamAccountResult.DONE) linked = null
            return actionResult
        }

        // null = never answers, like a slow network, so tests about linking see the screen mid-import
        var importOutcome: SteamImportOutcome? = null
        var imports = 0

        override suspend fun importGames(): SteamImportOutcome {
            imports++
            return importOutcome ?: awaitCancellation()
        }
    }

    private val fake = FakeRepository()

    @Test
    fun `the screen starts by loading the linked account`() {
        fake.linked = steamId

        val state = SteamAccountViewModel(fake).uiState.value

        assertFalse(state.isLoading)
        assertEquals(steamId, state.linkedSteamId)
    }

    @Test
    fun `a failed load offers a retry, and a retry that works clears it`() {
        fake.loadResult = SteamAccountLoad.Failed(SteamAccountResult.NO_CONNECTION)
        val vm = SteamAccountViewModel(fake)
        assertTrue(vm.uiState.value.loadFailed)
        assertEquals(R.string.steam_account_no_connection, vm.uiState.value.message)

        fake.loadResult = null
        vm.load()

        assertFalse(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `a malformed id is refused without calling the server`() {
        val vm = SteamAccountViewModel(fake)

        vm.link("12345")

        assertEquals(R.string.steam_account_invalid, vm.uiState.value.message)
        assertEquals(0, fake.calls)
        assertNull(vm.uiState.value.linkedSteamId)
    }

    @Test
    fun `linking trims the typed id, shows it as linked, and unlinking clears it`() {
        val vm = SteamAccountViewModel(fake)

        vm.link("  $steamId ")
        assertEquals(steamId, vm.uiState.value.linkedSteamId)
        assertEquals(R.string.steam_account_linked, vm.uiState.value.message)

        vm.unlink()
        assertNull(vm.uiState.value.linkedSteamId)
        assertEquals(R.string.steam_account_unlinked, vm.uiState.value.message)
    }

    @Test
    fun `a refused link leaves the screen as it was and says why`() {
        val vm = SteamAccountViewModel(fake)
        fake.actionResult = SteamAccountResult.TAKEN

        vm.link(steamId)

        assertNull(vm.uiState.value.linkedSteamId)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(R.string.steam_account_taken, vm.uiState.value.message)
    }

    @Test
    fun `a failed unlink keeps the account linked`() {
        fake.linked = steamId
        val vm = SteamAccountViewModel(fake)
        fake.actionResult = SteamAccountResult.NO_CONNECTION

        vm.unlink()

        assertEquals(steamId, vm.uiState.value.linkedSteamId)
        assertEquals(R.string.steam_account_no_connection, vm.uiState.value.message)
    }

    @Test
    fun `a message is shown once`() {
        val vm = SteamAccountViewModel(fake)
        vm.link("nope")

        vm.messageShown()

        assertNull(vm.uiState.value.message)
    }
}
