package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.ActivityType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.activity.ActivityPageDto
import com.backlogbattlers.api.activity.recordActivity
import com.backlogbattlers.api.friends.FriendDto
import com.backlogbattlers.api.friends.FriendRequestDto
import com.backlogbattlers.api.friends.FriendSearchResultDto
import com.backlogbattlers.api.friends.acceptRequest
import com.backlogbattlers.api.friends.sendRequest
import com.backlogbattlers.api.friends.SendRequestResult
import com.backlogbattlers.api.profile.MeDto
import com.backlogbattlers.api.profile.ProfileDto
import com.backlogbattlers.api.scoring.LeaderboardResponseDto
import com.backlogbattlers.api.scoring.currentMonthPeriod
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Changing your name and picture: it is kept on the account, and every screen other people see shows it. */
class MeRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "me-routes-${Uuid.random()}")
    private var caller: Uuid? = null
    private val json = Json { ignoreUnknownKeys = true }

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                meRoutes { caller }
                friendRoutes { caller }
                leaderboardRoutes { caller }
                activityRoutes { caller }
                profileRoutes { caller }
            }
        }
    }

    private fun newUser(name: String): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "${name.lowercase()}@example.com"
            it[avatarUrl] = "https://photos.test/${name.lowercase()}.jpg"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    private suspend fun HttpClient.change(body: String): HttpResponse = patch("/users/me") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.me(): MeDto = json.decodeFromString(get("/users/me").bodyAsText())

    private fun befriend(a: Uuid, b: Uuid) = transaction(db) {
        val request = sendRequest(a, b) as SendRequestResult.Sent
        acceptRequest(b, Uuid.parse(request.request.requestId))
    }

    @Test
    fun `both routes need a signed in user`() = testApplication {
        installApi()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.change("""{"displayName":"X"}""").status)
    }

    @Test
    fun `a new account has its Google name and photo and no picture`() = testApplication {
        installApi()
        caller = newUser("Dylan")

        val me = client.me()

        assertEquals("Dylan", me.displayName)
        assertEquals("https://photos.test/dylan.jpg", me.avatarUrl)
        assertNull(me.pictureId)
    }

    @Test
    fun `the name is saved, trimmed, and read back`() = testApplication {
        installApi()
        caller = newUser("Dylan")

        val response = client.change("""{"displayName":"  Dyl  "}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Dyl", json.decodeFromString<MeDto>(response.bodyAsText()).displayName)
        assertEquals("Dyl", client.me().displayName)
        assertEquals("Dyl", transaction(db) { Users.selectAll().where { Users.id eq caller!! }.single()[Users.displayName] })
    }

    @Test
    fun `the picture is saved, replaced, and taken away with an empty id`() = testApplication {
        installApi()
        caller = newUser("Dylan")

        client.change("""{"pictureId":"pfp_03"}""")
        assertEquals("pfp_03", client.me().pictureId)

        client.change("""{"pictureId":"pfp_12"}""")
        assertEquals("pfp_12", client.me().pictureId)

        client.change("""{"pictureId":""}""")
        assertNull(client.me().pictureId)
    }

    @Test
    fun `changing one thing leaves the other alone`() = testApplication {
        installApi()
        caller = newUser("Dylan")
        client.change("""{"pictureId":"pfp_03"}""")

        client.change("""{"displayName":"Dyl"}""")

        assertEquals("Dyl", client.me().displayName)
        assertEquals("pfp_03", client.me().pictureId)
    }

    @Test
    fun `a name or picture that is not valid is a 400 and changes nothing`() = testApplication {
        installApi()
        caller = newUser("Dylan")

        assertEquals(HttpStatusCode.BadRequest, client.change("""{"displayName":"   "}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.change("""{"displayName":"${"x".repeat(21)}"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.change("""{"pictureId":"../etc/passwd"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.change("""{}""").status)
        // a good name next to a bad picture is refused as a whole
        assertEquals(HttpStatusCode.BadRequest, client.change("""{"displayName":"Dyl","pictureId":"NOT VALID"}""").status)

        assertEquals("Dylan", client.me().displayName)
    }

    @Test
    fun `a name of exactly twenty characters is fine`() = testApplication {
        installApi()
        caller = newUser("Dylan")

        assertEquals(HttpStatusCode.OK, client.change("""{"displayName":"${"x".repeat(20)}"}""").status)
    }

    @Test
    fun `friends see the new name and picture on the friends list, requests and search`() = testApplication {
        installApi()
        val me = newUser("Me")
        val friend = newUser("Friend")
        val stranger = newUser("Stranger")
        befriend(me, friend)
        val request = transaction(db) { sendRequest(stranger, me) as SendRequestResult.Sent }
        caller = friend
        client.change("""{"displayName":"Friendly","pictureId":"pfp_07"}""")
        caller = stranger
        client.change("""{"pictureId":"pfp_09"}""")

        caller = me
        val friends = json.decodeFromString<List<FriendDto>>(client.get("/users/me/friends").bodyAsText())
        assertEquals("Friendly", friends.single().displayName)
        assertEquals("pfp_07", friends.single().pictureId)

        val requests = json.decodeFromString<List<FriendRequestDto>>(client.get("/users/me/friends/requests").bodyAsText())
        assertEquals("pfp_09", requests.single().fromPictureId)
        check(request.request.requestId.isNotEmpty())

        val found = json.decodeFromString<List<FriendSearchResultDto>>(client.get("/users/me/friends/search?q=Friendly").bodyAsText())
        assertEquals("pfp_07", found.single().pictureId)
    }

    @Test
    fun `the leaderboard, the activity feed and the profile show the new name and picture`() = testApplication {
        installApi()
        val me = newUser("Me")
        transaction(db) {
            MonthlyLeaderboardEntries.insert { it[userId] = me; it[monthPeriod] = currentMonthPeriod(); it[monthlyPoints] = 40 }
            recordActivity(me, ActivityType.GAME_COMPLETED)
        }
        caller = me
        client.change("""{"displayName":"Renamed","pictureId":"pfp_20"}""")

        val board = json.decodeFromString<LeaderboardResponseDto>(client.get("/leaderboard/monthly").bodyAsText())
        assertEquals("Renamed", board.entries.single().displayName)
        assertEquals("pfp_20", board.entries.single().pictureId)
        assertEquals("pfp_20", board.me?.pictureId)

        val feed = json.decodeFromString<ActivityPageDto>(client.get("/activity").bodyAsText())
        assertEquals("Renamed", feed.events.single().actorName)
        assertEquals("pfp_20", feed.events.single().actorPictureId)

        val profile = json.decodeFromString<ProfileDto>(client.get("/users/me/profile").bodyAsText())
        assertEquals("Renamed", profile.displayName)
        assertEquals("pfp_20", profile.pictureId)
    }
}
//------------------------------EOF------------------------------\\
