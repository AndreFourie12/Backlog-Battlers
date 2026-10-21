package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.CompletionRecords
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.FriendRequests
import com.backlogbattlers.api.db.Friendships
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.Users
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Deleting your own account, and everything that goes with it. */
class AccountRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "account-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { accountRoutes { caller } }
        }
    }

    private fun newUser(name: String = "Tester"): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "${name.lowercase()}@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    /** A library entry with an unlocked achievement, a completion and a month's points, all owned by [owner]. */
    private fun seedProgress(owner: Uuid) = transaction(db) {
        if (Games.selectAll().where { Games.id eq 113112 }.empty()) {
            Games.insert { it[id] = 113112; it[title] = "Hades" }
        }
        val entry = LibraryEntries.insert {
            it[userId] = owner
            it[gameId] = 113112
            it[platform] = Platform.PC
        }[LibraryEntries.id]
        UnlockedAchievements.insert { it[libraryEntryId] = entry; it[achievementId] = "A_RARE" }
        CompletionRecords.insert {
            it[userId] = owner
            it[libraryEntryId] = entry
            it[completionType] = CompletionType.MAIN_STORY
            it[achievementPoints] = 20
            it[completionTimePoints] = 70
            it[speedBonusPoints] = 0
            it[pointsAwarded] = 90
            it[monthPeriod] = "2026-10"
        }
        MonthlyLeaderboardEntries.insert { it[userId] = owner; it[monthPeriod] = "2026-10"; it[monthlyPoints] = 90 }
    }

    private fun count(table: Table): Long = transaction(db) { table.selectAll().count() }

    @Test
    fun `deleting your account removes it and everything you own`() = testApplication {
        installApi()
        val me = newUser("Me")
        val friend = newUser("Friend")
        val stranger = newUser("Stranger")
        seedProgress(me)
        seedProgress(friend)
        transaction(db) {
            Friendships.insert { it[userAId] = me; it[userBId] = friend }
            FriendRequests.insert { it[senderId] = me; it[receiverId] = stranger }
        }
        caller = me

        val response = client.delete("/users/me")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(0, transaction(db) { Users.selectAll().where { Users.id eq me }.count() })
        assertEquals(0, count(Friendships))
        assertEquals(0, count(FriendRequests))
        // only the friend's own progress is left
        assertEquals(1, count(LibraryEntries))
        assertEquals(1, count(UnlockedAchievements))
        assertEquals(1, count(CompletionRecords))
        assertEquals(1, count(MonthlyLeaderboardEntries))
        assertEquals(friend, transaction(db) { LibraryEntries.selectAll().single()[LibraryEntries.userId] })
        // the other two accounts are untouched
        assertEquals(2, count(Users))
    }

    @Test
    fun `deleting needs you to be signed in`() = testApplication {
        installApi()
        newUser()
        caller = null

        val response = client.delete("/users/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, count(Users))
    }

    @Test
    fun `deleting an account that is already gone is a 404`() = testApplication {
        installApi()
        caller = Uuid.random()

        val response = client.delete("/users/me")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
//------------------------------EOF------------------------------\\
