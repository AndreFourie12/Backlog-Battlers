package com.backlogbattlers.api.notifications

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Devices
import com.backlogbattlers.api.db.Friendships
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.routes.completionRoutes
import com.backlogbattlers.api.routes.friendRoutes
import com.backlogbattlers.api.scoring.currentMonthPeriod
import com.backlogbattlers.api.settings.SettingsDto
import com.backlogbattlers.api.settings.saveSettings
import com.backlogbattlers.api.friends.FriendRequestDto
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Who gets told about what, and that the Settings switches are checked before anything is sent. */
class NotifierTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "notifier-${Uuid.random()}")
    private var caller: Uuid? = null

    /** One push as it reached the sender. */
    private data class Sent(val tokens: List<String>, val data: Map<String, String>)

    private val sent = mutableListOf<Sent>()
    private var deadTokens = emptyList<String>()
    private val sender = PushSender { tokens, data -> sent += Sent(tokens, data); deadTokens }
    private val notifier = Notifier(sender, launch = Notifier.inline)

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                friendRoutes(notifier) { caller }
                completionRoutes(notifier) { caller }
            }
        }
    }

    private fun newUser(name: String, vararg tokens: String): Uuid = transaction(db) {
        val id = Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "${name.lowercase()}@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
        tokens.forEach { token -> Devices.insert { it[Devices.token] = token; it[userId] = id } }
        id
    }

    private fun everythingOn() = SettingsDto(
        language = "en",
        themeMode = "DARK",
        homeBackdrop = "HILLS",
        defaultPlatform = Platform.PC,
        achievementNotifications = true,
        rankChangeNotifications = true,
        seasonResetNotifications = true,
        friendActivityNotifications = true,
    )

    private fun befriend(a: Uuid, b: Uuid) = transaction(db) {
        val (first, second) = if (a.toString() < b.toString()) a to b else b to a
        Friendships.insert { it[userAId] = first; it[userBId] = second }
    }

    /** A library entry for Hades, which is worth 70 points to complete (its average hours, no achievements). */
    private fun hadesEntry(owner: Uuid): Uuid = transaction(db) {
        Games.upsert { it[id] = 113112; it[title] = "Hades"; it[avgCompletionHours] = 70.0; it[avg100PercentHours] = 150.0 }
        LibraryEntries.insert { it[userId] = owner; it[gameId] = 113112; it[platform] = Platform.PC }[LibraryEntries.id]
    }

    private fun setMonthlyPoints(userId: Uuid, points: Int) = transaction(db) {
        MonthlyLeaderboardEntries.insert {
            it[MonthlyLeaderboardEntries.userId] = userId
            it[monthPeriod] = currentMonthPeriod()
            it[monthlyPoints] = points
        }
    }

    // ---- the preference filter ----

    @Test
    fun `with no saved settings every push is allowed, because the app ships with every switch on`() {
        PushType.entries.forEach { assertTrue(allowsPush(settings = null, pushEnabled = true, type = it)) }
    }

    @Test
    fun `the friend activity switch covers friend requests, acceptances and completions, and nothing else`() {
        val friendsOff = everythingOn().copy(friendActivityNotifications = false)

        assertFalse(allowsPush(friendsOff, true, PushType.FRIEND_REQUEST_RECEIVED))
        assertFalse(allowsPush(friendsOff, true, PushType.FRIEND_REQUEST_ACCEPTED))
        assertFalse(allowsPush(friendsOff, true, PushType.FRIEND_COMPLETED_GAME))
        assertTrue(allowsPush(friendsOff, true, PushType.OVERTAKEN))
    }

    @Test
    fun `the rank changes switch covers being overtaken`() {
        val rankOff = everythingOn().copy(rankChangeNotifications = false)

        assertFalse(allowsPush(rankOff, true, PushType.OVERTAKEN))
        assertTrue(allowsPush(rankOff, true, PushType.FRIEND_REQUEST_RECEIVED))
    }

    @Test
    fun `the achievement and season switches do not block the four pushes`() {
        val othersOff = everythingOn().copy(achievementNotifications = false, seasonResetNotifications = false)

        PushType.entries.forEach { assertTrue(allowsPush(othersOff, true, it)) }
    }

    @Test
    fun `the account-wide push switch turns everything off`() {
        PushType.entries.forEach { assertFalse(allowsPush(everythingOn(), pushEnabled = false, type = it)) }
    }

    @Test
    fun `wantsPush reads the saved settings, and says no for a user who does not exist`() {
        val user = newUser("Ana")
        assertTrue(wantsPush(user, PushType.OVERTAKEN))

        saveSettings(user, everythingOn().copy(rankChangeNotifications = false))
        assertFalse(wantsPush(user, PushType.OVERTAKEN))

        transaction(db) { Users.update({ Users.id eq user }) { it[pushNotificationsEnabled] = false } }
        assertFalse(wantsPush(user, PushType.FRIEND_REQUEST_RECEIVED))

        assertFalse(wantsPush(Uuid.random(), PushType.FRIEND_REQUEST_RECEIVED))
    }

    // ---- who is overtaken ----

    @Test
    fun `overtaken is everyone level with or ahead of you before, and behind you now`() {
        val me = Uuid.random()
        val level = Uuid.random()
        val passed = Uuid.random()
        val stillAhead = Uuid.random()
        val alreadyBehind = Uuid.random()
        val nowLevel = Uuid.random()
        val standings = mapOf(me to 120, level to 40, passed to 90, stillAhead to 200, alreadyBehind to 10, nowLevel to 120)

        assertEquals(setOf(level, passed), overtaken(me, pointsBefore = 40, pointsAfter = 120, standings).toSet())
    }

    @Test
    fun `nobody is overtaken without a points gain, and people with no points are never counted`() {
        val me = Uuid.random()
        val zero = Uuid.random()

        assertEquals(emptyList(), overtaken(me, 50, 50, mapOf(Uuid.random() to 50)))
        assertEquals(emptyList(), overtaken(me, 0, 30, mapOf(zero to 0)))
    }

    // ---- the four events, end to end ----

    @Test
    fun `a friend request pushes to every phone of the person it was sent to`() = testApplication {
        installApi()
        val ana = newUser("Ana")
        val ben = newUser("Ben", "ben-phone", "ben-tablet")
        caller = ana

        client.post("/users/me/friends/requests") { contentType(ContentType.Application.Json); setBody("""{"targetUserId":"$ben"}""") }

        val push = sent.single()
        assertEquals(setOf("ben-phone", "ben-tablet"), push.tokens.toSet())
        assertEquals("FRIEND_REQUEST_RECEIVED", push.data["type"])
        assertEquals("Ana", push.data["actorName"])
        assertEquals(ben.toString(), push.data["userId"])
    }

    @Test
    fun `nothing is sent to someone who switched friend activity off`() = testApplication {
        installApi()
        val ana = newUser("Ana")
        val ben = newUser("Ben", "ben-phone")
        saveSettings(ben, everythingOn().copy(friendActivityNotifications = false))
        caller = ana

        client.post("/users/me/friends/requests") { contentType(ContentType.Application.Json); setBody("""{"targetUserId":"$ben"}""") }

        assertEquals(emptyList(), sent)
    }

    @Test
    fun `accepting a request tells the person who sent it`() = testApplication {
        installApi()
        val ana = newUser("Ana", "ana-phone")
        val ben = newUser("Ben")
        caller = ana
        client.post("/users/me/friends/requests") { contentType(ContentType.Application.Json); setBody("""{"targetUserId":"$ben"}""") }

        caller = ben
        val requestId = Json.decodeFromString<List<FriendRequestDto>>(client.get("/users/me/friends/requests").bodyAsText()).single().requestId
        client.post("/users/me/friends/requests/$requestId/accept")

        val push = sent.single()
        assertEquals(listOf("ana-phone"), push.tokens)
        assertEquals("FRIEND_REQUEST_ACCEPTED", push.data["type"])
        assertEquals("Ben", push.data["actorName"])
    }

    @Test
    fun `completing a game tells your friends, and tells whoever you passed on the leaderboard`() = testApplication {
        installApi()
        val ana = newUser("Ana")
        val friend = newUser("Ben", "ben-phone")
        val rival = newUser("Cleo", "cleo-phone")
        val stranger = newUser("Dev", "dev-phone")
        befriend(ana, friend)
        setMonthlyPoints(rival, 50) // Ana goes from 0 to 70 and passes her
        setMonthlyPoints(stranger, 500) // still well ahead
        caller = ana

        client.post("/users/me/library/${hadesEntry(ana)}/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"completionType":"${CompletionType.MAIN_STORY}"}""")
        }

        val byToken = sent.associateBy { it.tokens.single() }
        assertEquals(setOf("ben-phone", "cleo-phone"), byToken.keys)
        assertEquals("FRIEND_COMPLETED_GAME", byToken.getValue("ben-phone").data["type"])
        assertEquals("Hades", byToken.getValue("ben-phone").data["gameTitle"])
        assertEquals("OVERTAKEN", byToken.getValue("cleo-phone").data["type"])
        assertEquals("Ana", byToken.getValue("cleo-phone").data["actorName"])
    }

    @Test
    fun `someone with rank changes off is not told they were overtaken`() = testApplication {
        installApi()
        val ana = newUser("Ana")
        val rival = newUser("Cleo", "cleo-phone")
        saveSettings(rival, everythingOn().copy(rankChangeNotifications = false))
        setMonthlyPoints(rival, 50)
        caller = ana

        client.post("/users/me/library/${hadesEntry(ana)}/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"completionType":"${CompletionType.MAIN_STORY}"}""")
        }

        assertEquals(emptyList(), sent)
    }

    @Test
    fun `tokens FCM says are gone are forgotten after a send`() {
        val ben = newUser("Ben", "ben-phone", "ben-old-phone")
        deadTokens = listOf("ben-old-phone")

        kotlinx.coroutines.runBlocking { notifier.deliver(ben, Push(PushType.FRIEND_REQUEST_RECEIVED, actorName = "Ana")) }

        assertEquals(listOf("ben-phone"), tokensFor(ben))
    }

    @Test
    fun `a user with no phones registered costs no send at all`() {
        val ben = newUser("Ben")

        kotlinx.coroutines.runBlocking { notifier.deliver(ben, Push(PushType.FRIEND_REQUEST_RECEIVED, actorName = "Ana")) }

        assertEquals(emptyList(), sent)
    }
}
//------------------------------EOF------------------------------\\
