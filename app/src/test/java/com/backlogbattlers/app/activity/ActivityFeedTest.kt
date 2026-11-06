package com.backlogbattlers.app.activity

import com.backlogbattlers.app.R
import com.backlogbattlers.app.data.remote.ActivityApi
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.repository.AccessTokenSource
import com.backlogbattlers.app.data.repository.ActivityRepository
import com.backlogbattlers.app.data.repository.ActivityRepositoryImpl
import com.backlogbattlers.app.domain.model.ActivityEvent
import com.backlogbattlers.app.domain.model.ActivityPage
import com.backlogbattlers.app.domain.model.ActivityType
import com.backlogbattlers.app.viewmodels.ActivityRow
import com.backlogbattlers.app.viewmodels.ActivityViewModel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// the activity feed: the repository that reads it from the API, and the view model behind the screen
class ActivityFeedTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

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
    private var body = """
        {"events":[
          {"eventId":"e1","type":"GAME_COMPLETED","actorId":"u1","actorName":"Dylan","actorAvatarUrl":"pfp_03",
           "gameId":113112,"gameTitle":"Hades","subjectId":null,"subjectName":null,"count":1,"points":120,
           "detail":"COMPLETIONIST","createdAt":1762340000000},
          {"eventId":"e2","type":"SOMETHING_NEW","actorId":"u2","actorName":"Mihir","createdAt":1762330000000},
          {"eventId":"e3","type":"RANK_PASSED","actorId":"u1","actorName":"Dylan","subjectId":"u2","subjectName":"Mihir",
           "count":3,"createdAt":1762320000000}
        ],"nextBefore":1762320000000}
    """.trimIndent()

    private val api = ActivityApi(
        httpClient = HttpClient(
            MockEngine { request ->
                seen += request
                if (status != HttpStatusCode.OK) respondError(status) else respond(body, HttpStatusCode.OK, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
        baseUrl = "https://api.test",
        tokens = AccessTokenSource(fresh = { "token" }, renew = { "token" }, endSession = { }),
    )
    private val repository = ActivityRepositoryImpl(api)

    @Test
    fun `the newest page is asked for with the page size and the callers token, and no before`() = runBlocking {
        repository.getActivity()

        val url = seen.single().url
        assertEquals("/activity", url.encodedPath)
        assertEquals("30", url.parameters["limit"])
        assertNull(url.parameters["before"])
        assertEquals("Bearer token", seen.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `an older page is asked for with before`() = runBlocking {
        repository.getActivity(before = 1762320000000)

        assertEquals("1762320000000", seen.single().url.parameters["before"])
    }

    @Test
    fun `events are mapped, and a kind this version does not know is left out`() = runBlocking {
        val page = repository.getActivity()

        assertEquals(listOf("e1", "e3"), page.events.map { it.eventId })
        assertEquals(1762320000000, page.nextBefore)
        val completed = page.events.first()
        assertEquals(ActivityType.GAME_COMPLETED, completed.type)
        assertEquals("Hades", completed.gameTitle)
        assertEquals(120, completed.points)
        assertEquals("COMPLETIONIST", completed.detail)
        assertEquals("pfp_03", completed.actorAvatarUrl)
        val passed = page.events.last()
        assertEquals("Mihir", passed.subjectName)
        assertEquals(3, passed.count)
    }

    @Test
    fun `the last page has no nextBefore`() = runBlocking {
        body = """{"events":[],"nextBefore":null}"""

        val page = repository.getActivity()

        assertTrue(page.events.isEmpty())
        assertNull(page.nextBefore)
    }

    @Test
    fun `a server error is thrown, and a 401 is a session that ended`() {
        status = HttpStatusCode.InternalServerError
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.getActivity() } }

        status = HttpStatusCode.Unauthorized
        // the token source retries a 401 once with a renewed token, then gives up as an ended session
        assertThrows(SessionExpiredException::class.java) { runBlocking { repository.getActivity() } }
    }

    //------------------------------
    // the view model, against a fake repository

    private class FakeActivityRepository : ActivityRepository {
        // the page for each value of before; null is the newest
        val pages = mutableMapOf<Long?, ActivityPage>()
        var failure: Throwable? = null
        val asked = mutableListOf<Long?>()

        override suspend fun getActivity(before: Long?): ActivityPage {
            asked += before
            failure?.let { throw it }
            return pages[before] ?: ActivityPage(emptyList(), null)
        }
    }

    private val fake = FakeActivityRepository()
    private val utc = ZoneOffset.UTC

    private fun at(day: String, time: String): Long = Instant.parse("${day}T${time}Z").toEpochMilli()

    private fun event(id: String, createdAt: Long, actor: String = "u1", type: ActivityType = ActivityType.GAME_COMPLETED) = ActivityEvent(
        eventId = id,
        type = type,
        actorId = actor,
        actorName = "Dylan",
        actorAvatarUrl = null,
        gameId = 1,
        gameTitle = "Hades",
        subjectId = null,
        subjectName = null,
        count = 1,
        points = null,
        detail = null,
        createdAt = createdAt,
    )

    private fun viewModel(self: String? = "me") = ActivityViewModel(fake, selfUserId = { self }, zone = utc)

    @Test
    fun `the feed loads on opening, grouped under a header for each day`() {
        fake.pages[null] = ActivityPage(
            listOf(
                event("a", at("2026-11-05", "09:00:00")),
                event("b", at("2026-11-05", "08:00:00")),
                event("c", at("2026-11-04", "23:00:00")),
            ),
            nextBefore = null,
        )

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(
            listOf("day 2026-11-05", "a", "b", "day 2026-11-04", "c"),
            state.rows.map { if (it is ActivityRow.Day) "day ${it.date}" else (it as ActivityRow.Event).event.eventId },
        )
        assertEquals(LocalDate.of(2026, 11, 5), (state.rows.first() as ActivityRow.Day).date)
        assertEquals("me", state.selfUserId)
        assertFalse(state.hasMore)
        assertNull(state.errorMessage)
    }

    @Test
    fun `a feed with nothing in it is empty, not an error`() {
        val state = viewModel().uiState.value

        assertTrue(state.isEmpty)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a feed that cannot load shows the error, and trying again loads it`() {
        fake.failure = java.io.IOException("offline")
        val vm = viewModel()
        assertEquals(R.string.activity_error_load, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isEmpty) // an error is not "nothing here yet"

        fake.failure = null
        fake.pages[null] = ActivityPage(listOf(event("a", at("2026-11-05", "09:00:00"))), null)
        vm.refresh()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.rows.size)
    }

    @Test
    fun `a session that ended shows no error, the app is already going back to login`() {
        fake.failure = SessionExpiredException()

        val state = viewModel().uiState.value

        assertNull(state.errorMessage)
        assertNull(state.message)
        assertFalse(state.isLoading)
    }

    @Test
    fun `the next page is asked for from where the last one stopped, and added under it`() {
        val first = listOf(event("a", at("2026-11-05", "09:00:00")), event("b", at("2026-11-05", "08:00:00")))
        fake.pages[null] = ActivityPage(first, nextBefore = 100)
        fake.pages[100L] = ActivityPage(listOf(event("c", at("2026-11-04", "09:00:00"))), nextBefore = null)
        val vm = viewModel()
        assertTrue(vm.uiState.value.hasMore)

        vm.loadMore()

        assertEquals(listOf<Long?>(null, 100L), fake.asked)
        assertEquals(
            listOf("a", "b", "c"),
            vm.uiState.value.rows.filterIsInstance<ActivityRow.Event>().map { it.event.eventId },
        )
        assertEquals(2, vm.uiState.value.rows.count { it is ActivityRow.Day })
        assertFalse(vm.uiState.value.hasMore)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `an event that moves onto the next page is not shown twice`() {
        fake.pages[null] = ActivityPage(listOf(event("a", at("2026-11-05", "09:00:00"))), nextBefore = 100)
        fake.pages[100L] = ActivityPage(
            listOf(event("a", at("2026-11-05", "09:00:00")), event("b", at("2026-11-05", "08:00:00"))),
            nextBefore = null,
        )
        val vm = viewModel()

        vm.loadMore()

        assertEquals(listOf("a", "b"), vm.uiState.value.rows.filterIsInstance<ActivityRow.Event>().map { it.event.eventId })
    }

    @Test
    fun `nothing more is asked for once the last page is shown`() {
        fake.pages[null] = ActivityPage(listOf(event("a", at("2026-11-05", "09:00:00"))), nextBefore = null)
        val vm = viewModel()

        vm.loadMore()
        vm.loadMore()

        assertEquals(listOf<Long?>(null), fake.asked)
    }

    @Test
    fun `a next page that fails keeps the feed, says so once, and can be tried again`() {
        fake.pages[null] = ActivityPage(listOf(event("a", at("2026-11-05", "09:00:00"))), nextBefore = 100)
        fake.pages[100L] = ActivityPage(listOf(event("b", at("2026-11-04", "09:00:00"))), nextBefore = null)
        val vm = viewModel()

        fake.failure = java.io.IOException("offline")
        vm.loadMore()
        assertEquals(R.string.activity_error_load, vm.uiState.value.message)
        assertEquals(1, vm.uiState.value.rows.filterIsInstance<ActivityRow.Event>().size)
        assertTrue(vm.uiState.value.hasMore)
        assertFalse(vm.uiState.value.isLoadingMore)

        vm.messageShown()
        assertNull(vm.uiState.value.message)
        fake.failure = null
        vm.loadMore()
        assertEquals(2, vm.uiState.value.rows.filterIsInstance<ActivityRow.Event>().size)
    }

    @Test
    fun `pulling to refresh replaces the feed with the newest page`() {
        fake.pages[null] = ActivityPage(listOf(event("a", at("2026-11-05", "09:00:00"))), null)
        val vm = viewModel()

        fake.pages[null] = ActivityPage(
            listOf(event("new", at("2026-11-05", "10:00:00")), event("a", at("2026-11-05", "09:00:00"))),
            null,
        )
        vm.refresh(pulled = true)

        assertEquals(listOf("new", "a"), vm.uiState.value.rows.filterIsInstance<ActivityRow.Event>().map { it.event.eventId })
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `a refresh that fails keeps the feed on screen and says so once`() {
        fake.pages[null] = ActivityPage(listOf(event("a", at("2026-11-05", "09:00:00"))), null)
        val vm = viewModel()

        fake.failure = java.io.IOException("offline")
        vm.refresh(pulled = true)

        val state = vm.uiState.value
        assertEquals(2, state.rows.size)
        assertNull(state.errorMessage) // the feed is still there, so no error card over it
        assertEquals(R.string.activity_error_load, state.message)
        assertFalse(state.isRefreshing)
    }
}
//------------------------------EOF------------------------------\\
