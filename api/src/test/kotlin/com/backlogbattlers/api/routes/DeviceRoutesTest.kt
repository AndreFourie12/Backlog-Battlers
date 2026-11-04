package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Devices
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.notifications.MAX_TOKEN_LENGTH
import com.backlogbattlers.api.notifications.tokensFor
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Storing and forgetting the FCM tokens pushes are sent to. */
class DeviceRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "device-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    // shaped like a real FCM token: an instance id, a colon, then a long url-safe string
    private val phoneToken = "dXk3Nm9wZ2Q6:APA91bH-" + "x".repeat(140) + "_Zq"
    private val tabletToken = "cZ2pMlQ0:APA91bG-" + "y".repeat(140) + "_Wr"

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                deviceRoutes { caller }
                accountRoutes { caller }
            }
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

    private suspend fun HttpClient.register(token: String): HttpResponse =
        post("/devices") { contentType(ContentType.Application.Json); setBody("""{"token":"$token"}""") }

    private suspend fun HttpClient.unregister(token: String): HttpResponse =
        delete("/devices/${token.encodeURLPathPart()}")

    private fun deviceCount() = transaction(db) { Devices.selectAll().count() }

    @Test
    fun `devices need you to be signed in`() = testApplication {
        installApi()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.register(phoneToken).status)
        assertEquals(HttpStatusCode.Unauthorized, client.unregister(phoneToken).status)
        assertEquals(0, deviceCount())
    }

    @Test
    fun `a registered token is stored against the caller`() = testApplication {
        installApi()
        caller = newUser()

        assertEquals(HttpStatusCode.NoContent, client.register(phoneToken).status)
        assertEquals(HttpStatusCode.NoContent, client.register(tabletToken).status)

        assertEquals(setOf(phoneToken, tabletToken), tokensFor(caller!!).toSet())
    }

    @Test
    fun `registering the same token again keeps one row`() = testApplication {
        installApi()
        caller = newUser()

        client.register(phoneToken)
        client.register(phoneToken)

        assertEquals(1, deviceCount())
    }

    @Test
    fun `a token moves to whoever signs in on that phone next`() = testApplication {
        installApi()
        val first = newUser()
        val second = newUser()

        caller = first
        client.register(phoneToken)
        caller = second
        client.register(phoneToken)

        assertEquals(emptyList(), tokensFor(first))
        assertEquals(listOf(phoneToken), tokensFor(second))
    }

    @Test
    fun `a blank or oversized token is a 400 and nothing is stored`() = testApplication {
        installApi()
        caller = newUser()

        val blank = client.register("   ")
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        assertTrue(blank.bodyAsText().contains("token is required"))
        assertEquals(HttpStatusCode.BadRequest, client.register("a".repeat(MAX_TOKEN_LENGTH + 1)).status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/devices") { contentType(ContentType.Application.Json); setBody("{}") }.status)

        assertEquals(0, deviceCount())
    }

    @Test
    fun `signing out removes the token, and doing it twice is harmless`() = testApplication {
        installApi()
        caller = newUser()
        client.register(phoneToken)
        client.register(tabletToken)

        assertEquals(HttpStatusCode.NoContent, client.unregister(phoneToken).status)
        assertEquals(HttpStatusCode.NoContent, client.unregister(phoneToken).status)

        assertEquals(listOf(tabletToken), tokensFor(caller!!))
    }

    @Test
    fun `you cannot remove someone else's token`() = testApplication {
        installApi()
        val owner = newUser()
        caller = owner
        client.register(phoneToken)

        caller = newUser()
        client.unregister(phoneToken)

        assertEquals(listOf(phoneToken), tokensFor(owner))
    }

    @Test
    fun `deleting the account removes its devices`() = testApplication {
        installApi()
        caller = newUser()
        client.register(phoneToken)
        client.register(tabletToken)

        client.delete("/users/me")

        assertEquals(0, deviceCount())
    }
}
//------------------------------EOF------------------------------\\
