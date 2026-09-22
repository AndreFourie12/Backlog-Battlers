package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.scoring.CompleteResponseDto
import com.backlogbattlers.api.scoring.monthlyPointsFor
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * Marking games complete, end to end. No IGDB or Steam calls happen here: completion only ever
 * reads Games/Achievements rows the catalogue and achievement features already stored.
 */
class CompletionRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "completion-routes-${Uuid.random()}")
    private var caller: Uuid? = null
    private val month = "2026-09" // routes use the real current month; seeded data here is timeless

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { completionRoutes { caller } }
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

    /** Hades, with its real Steam rarity for two achievements and no data for a third. */
    private fun seedHades() = transaction(db) {
        Games.upsert {
            it[id] = 113112
            it[title] = "Hades"
            it[avgCompletionHours] = 70.44444444444444
            it[avg100PercentHours] = 150.75
        }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_RARE"; it[name] = "Rare"; it[rarityPercent] = 5.9 }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_COMMON"; it[name] = "Common"; it[rarityPercent] = 81.9 }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_UNKNOWN"; it[name] = "Unknown"; it[rarityPercent] = null }
    }

    private fun addEntry(owner: Uuid): Uuid = transaction(db) {
        LibraryEntries.insert {
            it[userId] = owner
            it[gameId] = 113112
            it[platform] = Platform.PC
        }[LibraryEntries.id]
    }

    private fun unlock(entry: Uuid, vararg achievementIds: String) = transaction(db) {
        achievementIds.forEach { id ->
            UnlockedAchievements.insert { it[libraryEntryId] = entry; it[achievementId] = id }
        }
    }

    private suspend fun HttpClient.complete(entry: Uuid, type: CompletionType): HttpResponse =
        post("/users/me/library/$entry/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"completionType":"$type"}""")
        }

    @Test
    fun `completing awards points for unlocked achievements and the game's time to beat`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON") // A_UNKNOWN stays locked

        val response = client.complete(entry, CompletionType.MAIN_STORY)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<CompleteResponseDto>(response.bodyAsText())
        assertEquals(20 + 5, body.achievementPoints) // Rare tier (20) + Common tier (5), not the locked/unknown one
        assertEquals(70, body.completionTimePoints) // Hades' avgCompletionHours, rounded
        assertEquals(25 + 70, body.pointsAwarded)
        assertEquals(body.pointsAwarded, body.monthlyPoints) // this was the user's first completion
        assertEquals(body.monthlyPoints, monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `completion updates the library entry's status and completion type`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)

        client.complete(entry, CompletionType.MAIN_STORY)

        // This test only registers completionRoutes, not libraryRoutes, so check the row directly
        val row = transaction(db) { LibraryEntries.selectAll().single() }
        assertEquals(LibraryStatus.COMPLETED, row[LibraryEntries.status])
        assertEquals(CompletionType.MAIN_STORY, row[LibraryEntries.completionType])
        assertNotNull(row[LibraryEntries.completedAt])
    }

    @Test
    fun `completing the same entry with the same type twice is a 409 and awards nothing extra`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_COMMON")
        val first = client.complete(entry, CompletionType.MAIN_STORY)
        val firstTotal = Json.decodeFromString<CompleteResponseDto>(first.bodyAsText()).monthlyPoints

        val repeat = client.complete(entry, CompletionType.MAIN_STORY)

        assertEquals(HttpStatusCode.Conflict, repeat.status)
        assertEquals(firstTotal, monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `completing again with a different type pays only for achievements not already counted`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE") // counted on the first completion
        client.complete(entry, CompletionType.MAIN_STORY)

        unlock(entry, "A_UNKNOWN") // only this one is new for the second completion
        val second = client.complete(entry, CompletionType.COMPLETIONIST)

        val body = Json.decodeFromString<CompleteResponseDto>(second.bodyAsText())
        assertEquals(20, body.achievementPoints) // A_UNKNOWN's flat fallback only, not A_RARE again
        assertEquals(151, body.completionTimePoints) // Hades' avg100PercentHours, rounded
        // total for the month is both completions added together
        assertEquals((20 + 70) + (20 + 151), monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `an unknown or another user's entry is a 404, and an invalid id is a 400`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val owner = newUser()
        val theirEntry = addEntry(owner)

        assertEquals(HttpStatusCode.NotFound, client.complete(Uuid.random(), CompletionType.MAIN_STORY).status)
        assertEquals(HttpStatusCode.NotFound, client.complete(theirEntry, CompletionType.MAIN_STORY).status) // not caller's
        assertEquals(HttpStatusCode.BadRequest, client.post("/users/me/library/not-an-id/complete") {
            contentType(ContentType.Application.Json); setBody("""{"completionType":"MAIN_STORY"}""")
        }.status)
    }

    @Test
    fun `nobody signed in is a 401`() = testApplication {
        installApi()
        seedHades()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.complete(Uuid.random(), CompletionType.MAIN_STORY).status)
    }

    @Test
    fun `an unrecognised completion type is a 400`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)

        val response = client.post("/users/me/library/$entry/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"completionType":"NOT_A_REAL_TYPE"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
