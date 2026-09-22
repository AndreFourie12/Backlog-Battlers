package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.friends.FriendDto
import com.backlogbattlers.api.friends.FriendRelationshipStatus
import com.backlogbattlers.api.friends.FriendRequestDto
import com.backlogbattlers.api.friends.FriendSearchResultDto
import com.backlogbattlers.api.friends.SendRequestResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

// tests end to end friend management routes with in memory database and fake authentication
class FriendRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "friend-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    //------------------------------
    // installs api plugins and friend routes for testing
    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { friendRoutes { caller } }
        }
    }

    //------------------------------
    // creates a new user in the database and returns their id
    private fun newUser(displayName: String = "Tester"): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[this.displayName] = displayName
            it[email] = "${displayName.lowercase().replace(" ", "")}@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    //------------------------------
    // helper extension to post json content
    private suspend fun HttpClient.postJson(path: String, body: String): HttpResponse =
        post(path) { contentType(ContentType.Application.Json); setBody(body) }

    @Test
    //------------------------------
    // verifies that all friend routes return 401 unauthorized when caller is null
    fun `every friend route answers 401 when nobody is signed in`() = testApplication {
        installApi()
        caller = null
        val someId = Uuid.random()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/friends").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/friends/search?q=test").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/friends/requests").status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/users/me/friends/requests", """{"targetUserId":"$someId"}""").status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/users/me/friends/requests/$someId/accept", "").status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/users/me/friends/requests/$someId/reject", "").status)
    }

    @Test
    //------------------------------
    // verifies sending a request creates a pending request visible to the receiver
    fun `sending a request creates a pending row visible in receivers requests`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        val bob = newUser("Bob")

        caller = alice
        val sendResponse = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        assertEquals(HttpStatusCode.Created, sendResponse.status)
        val sendResult = Json.decodeFromString<SendRequestResponseDto>(sendResponse.bodyAsText())
        assertEquals(bob.toString(), sendResult.targetUserId)

        caller = bob
        val requestsResponse = client.get("/users/me/friends/requests")
        assertEquals(HttpStatusCode.OK, requestsResponse.status)
        val requests = Json.decodeFromString<List<FriendRequestDto>>(requestsResponse.bodyAsText())
        assertEquals(1, requests.size)
        assertEquals(alice.toString(), requests.single().fromUserId)
        assertEquals("Alice", requests.single().fromDisplayName)
    }

    @Test
    //------------------------------
    // verifies sending a friend request to oneself returns 400 bad request
    fun `sending a request to yourself is 400`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        caller = alice

        val response = client.postJson("/users/me/friends/requests", """{"targetUserId":"$alice"}""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    //------------------------------
    // verifies sending a request to an existing friend returns 409 conflict
    fun `sending a request to someone you are already friends with is 409`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        val bob = newUser("Bob")

        caller = alice
        val sendResp = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        val reqId = Json.decodeFromString<SendRequestResponseDto>(sendResp.bodyAsText()).requestId

        caller = bob
        client.postJson("/users/me/friends/requests/$reqId/accept", "")

        caller = alice
        val duplicateResp = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        assertEquals(HttpStatusCode.Conflict, duplicateResp.status)
    }

    @Test
    //------------------------------
    // verifies duplicate pending requests in either direction return 409 conflict
    fun `sending a duplicate pending request either direction is 409`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        val bob = newUser("Bob")

        caller = alice
        client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")

        val duplicateFromAlice = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        assertEquals(HttpStatusCode.Conflict, duplicateFromAlice.status)

        caller = bob
        val reverseFromBob = client.postJson("/users/me/friends/requests", """{"targetUserId":"$alice"}""")
        assertEquals(HttpStatusCode.Conflict, reverseFromBob.status)
    }

    @Test
    //------------------------------
    // verifies accepting a request removes it from requests and adds both users as friends
    fun `accepting a request removes it from requests and adds both users to friends`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        val bob = newUser("Bob")

        caller = alice
        val sendResp = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        val reqId = Json.decodeFromString<SendRequestResponseDto>(sendResp.bodyAsText()).requestId

        caller = bob
        val acceptResp = client.postJson("/users/me/friends/requests/$reqId/accept", "")
        assertEquals(HttpStatusCode.OK, acceptResp.status)

        val bobRequests = Json.decodeFromString<List<FriendRequestDto>>(client.get("/users/me/friends/requests").bodyAsText())
        assertEquals(0, bobRequests.size)

        val bobFriends = Json.decodeFromString<List<FriendDto>>(client.get("/users/me/friends").bodyAsText())
        assertEquals(1, bobFriends.size)
        assertEquals(alice.toString(), bobFriends.single().userId)

        caller = alice
        val aliceFriends = Json.decodeFromString<List<FriendDto>>(client.get("/users/me/friends").bodyAsText())
        assertEquals(1, aliceFriends.size)
        assertEquals(bob.toString(), aliceFriends.single().userId)
    }

    @Test
    //------------------------------
    // verifies rejecting a request removes it without creating a friendship
    fun `rejecting a request removes it without creating a friendship`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        val bob = newUser("Bob")

        caller = alice
        val sendResp = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        val reqId = Json.decodeFromString<SendRequestResponseDto>(sendResp.bodyAsText()).requestId

        caller = bob
        val rejectResp = client.postJson("/users/me/friends/requests/$reqId/reject", "")
        assertEquals(HttpStatusCode.NoContent, rejectResp.status)

        val bobRequests = Json.decodeFromString<List<FriendRequestDto>>(client.get("/users/me/friends/requests").bodyAsText())
        assertEquals(0, bobRequests.size)

        val bobFriends = Json.decodeFromString<List<FriendDto>>(client.get("/users/me/friends").bodyAsText())
        assertEquals(0, bobFriends.size)

        caller = alice
        val aliceFriends = Json.decodeFromString<List<FriendDto>>(client.get("/users/me/friends").bodyAsText())
        assertEquals(0, aliceFriends.size)
    }

    @Test
    //------------------------------
    // verifies accepting or rejecting a request not addressed to the caller returns 404
    fun `accepting or rejecting a request not addressed to caller is 404`() = testApplication {
        installApi()
        val alice = newUser("Alice")
        val bob = newUser("Bob")
        val charlie = newUser("Charlie")

        caller = alice
        val sendResp = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        val reqId = Json.decodeFromString<SendRequestResponseDto>(sendResp.bodyAsText()).requestId

        caller = charlie
        val acceptResp = client.postJson("/users/me/friends/requests/$reqId/accept", "")
        assertEquals(HttpStatusCode.NotFound, acceptResp.status)

        val rejectResp = client.postJson("/users/me/friends/requests/$reqId/reject", "")
        assertEquals(HttpStatusCode.NotFound, rejectResp.status)
    }

    @Test
    //------------------------------
    // verifies friend search excludes caller and tags status correctly in four relationship states
    fun `friend search excludes caller and tags relationship status correctly`() = testApplication {
        installApi()
        val alice = newUser("User Alice")
        val bob = newUser("User Bob")
        val charlie = newUser("User Charlie")
        val david = newUser("User David")
        val eve = newUser("User Eve")

        caller = alice
        val sendToBob = client.postJson("/users/me/friends/requests", """{"targetUserId":"$bob"}""")
        val reqIdBob = Json.decodeFromString<SendRequestResponseDto>(sendToBob.bodyAsText()).requestId
        caller = bob
        client.postJson("/users/me/friends/requests/$reqIdBob/accept", "")

        caller = alice
        client.postJson("/users/me/friends/requests", """{"targetUserId":"$charlie"}""")

        caller = david
        client.postJson("/users/me/friends/requests", """{"targetUserId":"$alice"}""")


        caller = alice
        val searchResp = client.get("/users/me/friends/search?q=User")
        assertEquals(HttpStatusCode.OK, searchResp.status)
        val results = Json.decodeFromString<List<FriendSearchResultDto>>(searchResp.bodyAsText())

        // Alice should be excluded from search results
        val selfInResults = results.any { it.userId == alice.toString() }
        assertTrue(!selfInResults)

        val bobResult = results.first { it.userId == bob.toString() }
        assertEquals(FriendRelationshipStatus.FRIENDS, bobResult.status)

        val charlieResult = results.first { it.userId == charlie.toString() }
        assertEquals(FriendRelationshipStatus.PENDING_SENT, charlieResult.status)

        val davidResult = results.first { it.userId == david.toString() }
        assertEquals(FriendRelationshipStatus.PENDING_RECEIVED, davidResult.status)

        val eveResult = results.first { it.userId == eve.toString() }
        assertEquals(FriendRelationshipStatus.NONE, eveResult.status)
    }
}
//------------------------------EOF------------------------------\\