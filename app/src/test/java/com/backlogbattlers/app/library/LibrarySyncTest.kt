package com.backlogbattlers.app.library

import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.data.remote.CreateLibraryEntryRequest
import com.backlogbattlers.app.data.remote.LibraryApi
import com.backlogbattlers.app.data.remote.LibraryEntryDto
import com.backlogbattlers.app.data.remote.UpdateLibraryEntryRequest
import com.backlogbattlers.app.data.repository.LibrarySync
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// keeping this phone's library and the account's in step. the "server" below is a small in-memory
// copy of how the real API answers (create is idempotent on the id, a duplicate game is a 409)
class LibrarySyncTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val dao = FakeLibraryEntryDao()
    private val server = linkedMapOf<String, LibraryEntryDto>()
    private val seen = mutableListOf<HttpRequestData>()
    private var signedInAs: String? = ALICE
    private var offline = false

    // the account lets the phone download but answers every upload or delete with a 500
    private var failWrites = false

    // the answer to the next create is lost on the way back, after the server has stored the entry
    private var loseNextCreateAnswer = false

    // the user edits the entry on the phone while its upload is in flight
    private var editDuringUpload: (() -> Unit)? = null

    private val sync = LibrarySync(
        dao = dao,
        libraryApi = LibraryApi(
            HttpClient(
                MockEngine { request ->
                    seen += request
                    if (offline) throw IOException("no connection")
                    if (failWrites && request.method != HttpMethod.Get) respondError(HttpStatusCode.InternalServerError) else answer(request)
                },
            ) {
                install(ContentNegotiation) { json(json) }
            },
            baseUrl = "https://api.example.test",
        ),
        accessToken = { if (signedInAs != null) "token-123" else null },
        currentUserId = { signedInAs },
    )

    private fun MockRequestHandleScope.answer(request: HttpRequestData) = when (request.method) {
        HttpMethod.Get -> respond(json.encodeToString(server.values.toList()), HttpStatusCode.OK, jsonHeaders)
        HttpMethod.Post -> {
            val body = json.decodeFromString<CreateLibraryEntryRequest>(bodyOf(request))
            val sameGame = server.values.firstOrNull { it.gameId == body.gameId && it.platform == body.platform }
            when {
                server[body.libraryEntryId] != null ->
                    respond(json.encodeToString(server.getValue(body.libraryEntryId)), HttpStatusCode.OK, jsonHeaders)
                sameGame != null -> respondError(HttpStatusCode.Conflict)
                else -> {
                    val created = remote(body.libraryEntryId, body.gameId, body.platform, body.status)
                    server[created.libraryEntryId] = created
                    if (loseNextCreateAnswer) {
                        loseNextCreateAnswer = false
                        throw IOException("connection dropped")
                    }
                    respond(json.encodeToString(created), HttpStatusCode.Created, jsonHeaders)
                }
            }
        }
        HttpMethod.Patch -> {
            val id = request.url.encodedPath.substringAfterLast('/')
            val body = json.decodeFromString<UpdateLibraryEntryRequest>(bodyOf(request))
            val existing = server[id]
            if (existing == null) {
                respondError(HttpStatusCode.NotFound)
            } else {
                editDuringUpload?.invoke()
                editDuringUpload = null
                val updated = existing.copy(
                    status = body.status ?: existing.status,
                    hoursPlayed = body.hoursPlayed,
                    unlockedAchievementIds = body.unlockedAchievementIds,
                )
                server[id] = updated
                respond(json.encodeToString(updated), HttpStatusCode.OK, jsonHeaders)
            }
        }
        HttpMethod.Delete -> {
            val removed = server.remove(request.url.encodedPath.substringAfterLast('/'))
            respond("", if (removed != null) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
        else -> respondError(HttpStatusCode.MethodNotAllowed)
    }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    private fun methodsSeen() = seen.map { it.method.value }

    private fun local(
        id: String,
        gameId: Int = HADES,
        platform: Platform = Platform.PC,
        status: LibraryStatus = LibraryStatus.BACKLOG,
        hours: Float = 0f,
        unlocked: List<String> = emptyList(),
        pending: Boolean = true,
        userId: String = ALICE,
        updatedAt: Long = 1_000L,
    ) = LibraryEntryEntity(
        libraryEntryId = id,
        userId = userId,
        gameId = gameId,
        platform = platform.name,
        status = status.name,
        hoursPlayed = hours,
        unlockedAchievementIds = unlocked,
        completionType = null,
        addedAt = 500L,
        updatedAt = updatedAt,
        pendingSync = pending,
    )

    private fun remote(
        id: String,
        gameId: Int = HADES,
        platform: Platform = Platform.PC,
        status: LibraryStatus = LibraryStatus.BACKLOG,
        hours: Double = 0.0,
    ) = LibraryEntryDto(id, gameId, platform, status, hours, emptyList(), null, addedAt = 500L, updatedAt = 2_000L)

    private fun rowsOf(id: String) = dao.rows.value.single { it.libraryEntryId == id }

    @Test
    fun `a game added on the phone is created on the account under the same id and stops being pending`() = runBlocking {
        dao.upsert(local("entry-1", status = LibraryStatus.PLAYING))

        sync.sync()

        assertEquals(LibraryStatus.PLAYING, server.getValue("entry-1").status)
        assertEquals(HADES, server.getValue("entry-1").gameId)
        assertFalse(rowsOf("entry-1").pendingSync)
    }

    @Test
    fun `hours and unlocked achievements of a new entry are uploaded too`() = runBlocking {
        dao.upsert(local("entry-1", hours = 12.5f, unlocked = listOf("A1", "A2")))

        sync.sync()

        assertEquals(12.5, server.getValue("entry-1").hoursPlayed, 0.0)
        assertEquals(listOf("A1", "A2"), server.getValue("entry-1").unlockedAchievementIds)
    }

    @Test
    fun `an edit to an entry the account already has is a single patch, not a second create`() = runBlocking {
        server["entry-1"] = remote("entry-1")
        dao.upsert(local("entry-1", status = LibraryStatus.ABANDONED, hours = 3f))

        sync.sync()

        assertEquals(listOf("PATCH", "GET"), methodsSeen())
        assertEquals(LibraryStatus.ABANDONED, server.getValue("entry-1").status)
    }

    @Test
    fun `an upload whose answer was lost is retried without making a duplicate`() = runBlocking {
        dao.upsert(local("entry-1"))
        loseNextCreateAnswer = true

        sync.sync()
        // the account has the entry but the phone never heard, so it is still pending
        assertEquals(1, server.size)
        assertTrue(rowsOf("entry-1").pendingSync)

        sync.sync()

        assertEquals(1, server.size)
        assertFalse(rowsOf("entry-1").pendingSync)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `offline changes stay on the phone and pending`() = runBlocking {
        dao.upsert(local("entry-1", hours = 4f))
        offline = true

        sync.sync()

        assertTrue(rowsOf("entry-1").pendingSync)
        assertEquals(4f, rowsOf("entry-1").hoursPlayed)
        assertTrue(server.isEmpty())
    }

    @Test
    fun `an edit made while the upload is in flight is not marked as uploaded`() = runBlocking {
        server["entry-1"] = remote("entry-1")
        dao.upsert(local("entry-1", hours = 1f, updatedAt = 1_000L))
        editDuringUpload = {
            dao.rows.value = dao.rows.value.map { it.copy(hoursPlayed = 9f, updatedAt = 1_001L, pendingSync = true) }
        }

        sync.sync()

        val row = rowsOf("entry-1")
        assertTrue("the newer edit must still be uploaded next time", row.pendingSync)
        assertEquals(9f, row.hoursPlayed)
    }

    @Test
    fun `a removal is sent as a delete and then the row is dropped from the phone`() = runBlocking {
        server["entry-1"] = remote("entry-1")
        dao.upsert(local("entry-1").copy(pendingDelete = true))

        sync.sync()

        assertTrue(server.isEmpty())
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `a removal that could not be sent is not undone by the download that follows`() = runBlocking {
        server["entry-1"] = remote("entry-1")
        dao.upsert(local("entry-1", pending = false))
        // removed on the phone: hidden and waiting to be sent
        dao.upsert(rowsOf("entry-1").copy(pendingDelete = true, pendingSync = true))
        failWrites = true

        sync.sync() // the delete fails, and the download still lists the entry

        assertTrue(rowsOf("entry-1").pendingDelete)
        assertTrue(server.containsKey("entry-1"))

        failWrites = false
        sync.sync()

        assertTrue(server.isEmpty())
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `signing in on a new phone brings the account's library down`() = runBlocking {
        server["entry-1"] = remote("entry-1", status = LibraryStatus.PLAYING, hours = 7.5)
        server["entry-2"] = remote("entry-2", gameId = CELESTE, platform = Platform.SWITCH)

        sync.sync()

        assertEquals(2, dao.rows.value.size)
        val pulled = rowsOf("entry-1")
        assertEquals(ALICE, pulled.userId)
        assertEquals("PLAYING", pulled.status)
        assertEquals(7.5f, pulled.hoursPlayed)
        assertFalse(pulled.pendingSync)
    }

    @Test
    fun `an entry removed on another phone is dropped here, but one not uploaded yet is kept`() = runBlocking {
        dao.upsert(local("gone-elsewhere", gameId = CELESTE, pending = false))
        dao.upsert(local("brand-new", gameId = ELDEN, pending = true))

        sync.sync()

        assertNull(dao.rows.value.firstOrNull { it.libraryEntryId == "gone-elsewhere" })
        assertTrue(server.containsKey("brand-new"))
        assertTrue(dao.rows.value.any { it.libraryEntryId == "brand-new" })
    }

    @Test
    fun `a new entry that could not be uploaded is not dropped just because the account lacks it`() = runBlocking {
        dao.upsert(local("brand-new", hours = 2f))
        failWrites = true

        sync.sync() // the upload fails, then the download lists an account without it

        assertTrue(rowsOf("brand-new").pendingSync)
        assertEquals(2f, rowsOf("brand-new").hoursPlayed)

        failWrites = false
        sync.sync()

        assertTrue(server.containsKey("brand-new"))
    }

    @Test
    fun `a pending change is never overwritten by the account's older copy`() = runBlocking {
        // the upload of this change fails, so the account still has the old values when the download runs
        server["entry-1"] = remote("entry-1", status = LibraryStatus.BACKLOG)
        dao.upsert(local("entry-1", status = LibraryStatus.PLAYING, hours = 20f))
        failWrites = true

        sync.sync()

        assertEquals("PLAYING", rowsOf("entry-1").status)
        assertEquals(20f, rowsOf("entry-1").hoursPlayed)
        assertTrue(rowsOf("entry-1").pendingSync)
    }

    @Test
    fun `a game already on the account under another id keeps the account's copy`() = runBlocking {
        // added on two phones: the account has the game under the other phone's id
        server["other-phone"] = remote("other-phone", status = LibraryStatus.PLAYING)
        dao.upsert(local("this-phone"))

        sync.sync()

        assertNull(dao.rows.value.firstOrNull { it.libraryEntryId == "this-phone" })
        assertEquals(listOf("other-phone"), dao.rows.value.map { it.libraryEntryId })
        assertEquals(1, server.size)
    }

    @Test
    fun `a game finished on the phone is uploaded without its status and stays finished here`() = runBlocking {
        server["entry-1"] = remote("entry-1", status = LibraryStatus.PLAYING)
        dao.upsert(local("entry-1", status = LibraryStatus.COMPLETED, hours = 30f))

        sync.sync()
        sync.sync() // a second round downloads the account's copy, which is not completed

        assertEquals(LibraryStatus.PLAYING, server.getValue("entry-1").status)
        assertEquals(30.0, server.getValue("entry-1").hoursPlayed, 0.0)
        assertEquals("COMPLETED", rowsOf("entry-1").status)
    }

    @Test
    fun `nobody signed in means nothing is sent and nothing changes`() = runBlocking {
        dao.upsert(local("entry-1"))
        signedInAs = null

        sync.sync()

        assertTrue(seen.isEmpty())
        assertTrue(rowsOf("entry-1").pendingSync)
    }

    @Test
    fun `another account's rows on the same phone are left alone`() = runBlocking {
        dao.upsert(local("bobs-entry", gameId = CELESTE, userId = BOB, pending = false))
        server["entry-1"] = remote("entry-1")

        sync.sync()

        assertEquals(listOf("bobs-entry", "entry-1"), dao.rows.value.map { it.libraryEntryId }.sorted())
        assertEquals(BOB, rowsOf("bobs-entry").userId)
    }
    private companion object {
        const val ALICE = "user-alice"
        const val BOB = "user-bob"
        const val HADES = 1113
        const val CELESTE = 26226
        const val ELDEN = 119133
    }
}
