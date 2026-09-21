package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.library.LibraryEntryDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Library routes, tested end to end against a fake IGDB, an in-memory database and a fake login. */
class LibraryRoutesTest {

    // A fresh in-memory database for every test
    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "library-routes-${Uuid.random()}")

    // The fake login: whoever this holds is "signed in". Tests change it to switch user.
    private var caller: Uuid? = null

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""
    private val hadesJson = """[{"id":113112,"name":"Hades","platforms":[{"id":6,"name":"PC (Microsoft Windows)"}]}]"""
    private val hadesTimeJson = """[{"id":2005,"game_id":113112,"normally":253600,"completely":542700,"count":13}]"""

    private fun fakeIgdb(games: String = hadesJson) = IgdbClient(
        igdbHttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/oauth2/token" -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                    "/v4/games" -> respond(games, HttpStatusCode.OK, jsonHeaders)
                    "/v4/game_time_to_beats" -> respond(hadesTimeJson, HttpStatusCode.OK, jsonHeaders)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ),
        clientId = "id",
        clientSecret = "secret",
    )

    private fun ApplicationTestBuilder.installApi(igdb: IgdbClient = fakeIgdb()) {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { libraryRoutes(igdb) { caller } }
        }
    }

    private fun newUser(): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = "Tester"
            it[email] = "tester@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    /** Stores Hades and three achievements, so unlocking can be tested without calling IGDB. */
    private fun seedHadesAchievements() = transaction(db) {
        Games.insert {
            it[id] = 113112
            it[title] = "Hades"
        }
        listOf("A1", "A2", "A3").forEach { code ->
            Achievements.insert {
                it[gameId] = 113112
                it[achievementId] = code
                it[name] = "Achievement $code"
            }
        }
    }

    private suspend fun HttpClient.postJson(path: String, body: String): HttpResponse =
        post(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpClient.patchJson(path: String, body: String): HttpResponse =
        patch(path) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpClient.addHades(platform: String = "PC"): LibraryEntryDto {
        val response = postJson("/users/me/library", """{"gameId":113112,"platform":"$platform"}""")
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.library(): List<LibraryEntryDto> =
        Json.decodeFromString(get("/users/me/library").bodyAsText())

    @Test
    fun `every library route answers 401 when nobody is signed in`() = testApplication {
        installApi()
        caller = null
        val someId = Uuid.random()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/library").status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/users/me/library", """{"gameId":1,"platform":"PC"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patchJson("/users/me/library/$someId", "{}").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/users/me/library/$someId").status)
    }

    @Test
    fun `adding a game fetches it from IGDB, returns 201 and lists it with defaults`() = testApplication {
        installApi()
        caller = newUser()

        val added = client.addHades()

        assertEquals(113112, added.gameId)
        assertEquals(Platform.PC, added.platform)
        assertEquals(LibraryStatus.BACKLOG, added.status)
        assertEquals(0.0, added.hoursPlayed)
        assertEquals(emptyList(), added.unlockedAchievementIds)
        assertEquals(null, added.completionType)
        assertTrue(added.addedAt > 0)
        // The game was saved with its time-to-beat, because the library refers to our own copy
        val stored = transaction(db) { Games.selectAll().toList() }
        assertEquals("Hades", stored.single()[Games.title])
        assertEquals(1, client.library().size)
    }

    @Test
    fun `the same game on the same platform is a 409 but another platform is fine`() = testApplication {
        installApi()
        caller = newUser()
        client.addHades("PC")

        val duplicate = client.postJson("/users/me/library", """{"gameId":113112,"platform":"PC"}""")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        client.addHades("XBOX")
        assertEquals(2, client.library().size)
    }

    @Test
    fun `adding rejects bad input with 400`() = testApplication {
        installApi()
        caller = newUser()
        val invalidBodies = listOf(
            """{"gameId":113112,"platform":"NINTENDO64"}""", // not one of the platforms
            """{"gameId":113112}""", // platform missing
            """{"gameId":0,"platform":"PC"}""", // not a real game id
            """{"gameId":113112,"platform":"PC","status":"COMPLETED"}""", // completing has its own endpoint
            """this is not json""",
        )

        invalidBodies.forEach { body ->
            assertEquals(HttpStatusCode.BadRequest, client.postJson("/users/me/library", body).status, body)
        }
        assertEquals(0, client.library().size)
    }

    @Test
    fun `adding a game IGDB does not know is a 404`() = testApplication {
        installApi(fakeIgdb(games = "[]"))
        caller = newUser()

        val response = client.postJson("/users/me/library", """{"gameId":999,"platform":"PC"}""")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(0, client.library().size)
    }

    @Test
    fun `patching changes status and hours and refreshes updatedAt`() = testApplication {
        installApi()
        caller = newUser()
        val added = client.addHades()
        Thread.sleep(5) // make sure the clock moves so updatedAt can differ

        val response = client.patchJson("/users/me/library/${added.libraryEntryId}", """{"status":"PLAYING","hoursPlayed":12.5}""")

        assertEquals(HttpStatusCode.OK, response.status)
        val updated = Json.decodeFromString<LibraryEntryDto>(response.bodyAsText())
        assertEquals(LibraryStatus.PLAYING, updated.status)
        assertEquals(12.5, updated.hoursPlayed)
        assertTrue(updated.updatedAt > added.updatedAt)
        assertEquals(added.addedAt, updated.addedAt)
    }

    @Test
    fun `patching rejects impossible values and leaves the entry alone`() = testApplication {
        installApi()
        caller = newUser()
        seedHadesAchievements()
        val added = client.addHades()
        val path = "/users/me/library/${added.libraryEntryId}"

        assertEquals(HttpStatusCode.BadRequest, client.patchJson(path, """{"hoursPlayed":-1}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.patchJson(path, """{"hoursPlayed":100001}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.patchJson(path, """{"status":"COMPLETED"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.patchJson(path, """{"unlockedAchievementIds":[""]}""").status)
        val unknown = client.patchJson(path, """{"unlockedAchievementIds":["NOPE"]}""")
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertTrue(unknown.bodyAsText().contains("NOPE"))

        assertEquals(added.copy(), client.library().single())
    }

    @Test
    fun `unlocked achievements are stored as a set that each update replaces`() = testApplication {
        installApi()
        caller = newUser()
        seedHadesAchievements()
        val path = "/users/me/library/${client.addHades().libraryEntryId}"
        fun storedUnlocked() = transaction(db) { UnlockedAchievements.selectAll().count() }

        val first = client.patchJson(path, """{"unlockedAchievementIds":["A1","A2"]}""")
        assertEquals(listOf("A1", "A2"), Json.decodeFromString<LibraryEntryDto>(first.bodyAsText()).unlockedAchievementIds)

        val replaced = client.patchJson(path, """{"unlockedAchievementIds":["A2","A3","A3"]}""") // duplicate is ignored
        assertEquals(listOf("A2", "A3"), Json.decodeFromString<LibraryEntryDto>(replaced.bodyAsText()).unlockedAchievementIds)
        assertEquals(2, storedUnlocked())

        val cleared = client.patchJson(path, """{"unlockedAchievementIds":[]}""")
        assertEquals(emptyList(), Json.decodeFromString<LibraryEntryDto>(cleared.bodyAsText()).unlockedAchievementIds)
        assertEquals(0, storedUnlocked())
    }

    @Test
    fun `users only ever see and change their own entries`() = testApplication {
        installApi()
        val alice = newUser()
        val bob = newUser()

        caller = alice
        val alicesEntry = client.addHades()
        val path = "/users/me/library/${alicesEntry.libraryEntryId}"

        caller = bob
        assertEquals(emptyList(), client.library())
        assertEquals(HttpStatusCode.NotFound, client.patchJson(path, """{"status":"PLAYING"}""").status)
        assertEquals(HttpStatusCode.NotFound, client.delete(path).status)

        caller = alice
        assertEquals(alicesEntry, client.library().single()) // untouched by Bob's attempts
    }

    @Test
    fun `deleting removes the entry and its unlocked achievements`() = testApplication {
        installApi()
        caller = newUser()
        seedHadesAchievements()
        val path = "/users/me/library/${client.addHades().libraryEntryId}"
        client.patchJson(path, """{"unlockedAchievementIds":["A1"]}""")

        assertEquals(HttpStatusCode.NoContent, client.delete(path).status)

        assertEquals(0, transaction(db) { LibraryEntries.selectAll().count() })
        assertEquals(0, transaction(db) { UnlockedAchievements.selectAll().count() })
        assertEquals(HttpStatusCode.NotFound, client.delete(path).status) // already gone
    }

    @Test
    fun `an entry id that is not a valid id is a 400`() = testApplication {
        installApi()
        caller = newUser()

        assertEquals(HttpStatusCode.BadRequest, client.patchJson("/users/me/library/not-an-id", "{}").status)
        assertEquals(HttpStatusCode.BadRequest, client.delete("/users/me/library/not-an-id").status)
    }
}
