package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.scoring.LeaderboardResponseDto
import com.backlogbattlers.api.scoring.currentMonthPeriod
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** GET /leaderboard/monthly, end to end. The route reads the real current month, so tests give
 * everyone the same points and only check ordering, ranks and shape, never exact totals. */
class LeaderboardRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "leaderboard-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { leaderboardRoutes { caller } }
        }
    }

    private fun newUser(name: String): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "$name@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    // The route reads the real current month by default, so seeded rows must use it too
    private fun givePoints(user: Uuid, points: Int, monthPeriod: String = currentMonthPeriod()) = transaction(db) {
        MonthlyLeaderboardEntries.insert {
            it[userId] = user
            it[MonthlyLeaderboardEntries.monthPeriod] = monthPeriod
            it[monthlyPoints] = points
        }
    }

    @Test
    fun `the board is readable without signing in, and has no me row when nobody has`() = testApplication {
        installApi()
        givePoints(newUser("Alice"), 100)
        caller = null

        val response = client.get("/leaderboard/monthly")

        assertEquals(HttpStatusCode.OK, response.status)
        val board = Json.decodeFromString<LeaderboardResponseDto>(response.bodyAsText())
        assertEquals(listOf("Alice"), board.entries.map { it.displayName })
        assertNull(board.me)
    }

    @Test
    fun `a signed-in caller gets their own row pinned`() = testApplication {
        installApi()
        val leader = newUser("Leader")
        givePoints(leader, 900)
        caller = newUser("Middling")
        givePoints(caller!!, 50)

        val board = Json.decodeFromString<LeaderboardResponseDto>(client.get("/leaderboard/monthly").bodyAsText())

        assertEquals("Middling", board.me?.displayName)
        assertEquals(2, board.me?.rank)
    }

    @Test
    fun `entries are ordered highest first and tied scores share a rank`() = testApplication {
        installApi()
        givePoints(newUser("First"), 300)
        givePoints(newUser("SecondA"), 200)
        givePoints(newUser("SecondB"), 200)
        givePoints(newUser("Fourth"), 100)

        val board = Json.decodeFromString<LeaderboardResponseDto>(client.get("/leaderboard/monthly").bodyAsText())

        assertEquals(listOf(1, 2, 2, 4), board.entries.map { it.rank })
        assertEquals(listOf("First", "SecondA", "SecondB", "Fourth"), board.entries.map { it.displayName })
    }

    @Test
    fun `limit trims the page but the season and me still come through`() = testApplication {
        installApi()
        repeat(5) { givePoints(newUser("Player$it"), 100 - it) }
        caller = newUser("Caller")
        givePoints(caller!!, 1)

        val board = Json.decodeFromString<LeaderboardResponseDto>(client.get("/leaderboard/monthly?limit=2").bodyAsText())

        assertEquals(2, board.entries.size)
        assertEquals("Caller", board.me?.displayName)
        assertEquals(currentMonthPeriod(), board.monthPeriod)
    }

    @Test
    fun `an invalid limit is a 400, but the parameter is optional`() = testApplication {
        installApi()

        assertEquals(HttpStatusCode.OK, client.get("/leaderboard/monthly").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/leaderboard/monthly?limit=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/leaderboard/monthly?limit=abc").status)
    }
}
