/**
 * POE evidence: API request logging. Every request is logged with a request ID that the Android app
 * also prints in Logcat, so one action can be followed from the phone to the hosted API and the database.
 */
package com.backlogbattlers.api.logging

import com.backlogbattlers.api.auth.GoogleIdTokenVerifier
import com.backlogbattlers.api.auth.JwtService
import com.backlogbattlers.api.auth.bearerCurrentUser
import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.routes.accountRoutes
import com.backlogbattlers.api.routes.authRoutes
import com.backlogbattlers.api.routes.healthRoutes
import com.backlogbattlers.api.routes.libraryRoutes
import com.backlogbattlers.api.routes.settingsRoutes
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Tests covering request tracing IDs, CallLogging request lines, slow warnings, and the no-token rule. */
class RequestLoggingTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "request-logging-${Uuid.random()}")
    private val secret = "request-logging-test-secret-12345"
    private val issuer = "request-logging-test-issuer"
    private val jwt = JwtService(secret, issuer)

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""
    private val hadesJson = """[{"id":113112,"name":"Hades","platforms":[{"id":6,"name":"PC (Microsoft Windows)"}]}]"""
    private val hadesTimeJson = """[{"id":2005,"game_id":113112,"normally":253600,"completely":542700,"count":13}]"""

    private val authHttpClient = igdbHttpClient(MockEngine { respond("[]", HttpStatusCode.OK, jsonHeaders) })

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

    private lateinit var logCapture: LogCapture

    @BeforeTest
    fun setUp() {
        logCapture = LogCapture()
    }

    @AfterTest
    fun tearDown() {
        logCapture.close()
    }

    private fun ApplicationTestBuilder.installApi() {
        val igdb = fakeIgdb()
        application {
            configureRequestLogging()
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                healthRoutes()
                authRoutes(
                    googleVerifier = GoogleIdTokenVerifier(authHttpClient, "test-client-id"),
                    jwtService = jwt,
                )
                libraryRoutes(igdb, currentUser = bearerCurrentUser(jwt))
                accountRoutes(currentUser = bearerCurrentUser(jwt))
                settingsRoutes(currentUser = bearerCurrentUser(jwt))
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

    @Test
    fun `a response carries a generated request id when the app sends none`() = testApplication {
        installApi()
        val response = client.get("/users/me/library")
        val requestId = response.headers[HttpHeaders.XRequestId]
        assertNotNull(requestId)
        assertTrue(Regex("^[0-9a-f]{12}$").matches(requestId))
    }

    @Test
    fun `a valid request id from the app is sent back unchanged`() = testApplication {
        installApi()
        val response = client.get("/users/me/library") {
            header(HttpHeaders.XRequestId, "abc123def456")
        }
        assertEquals("abc123def456", response.headers[HttpHeaders.XRequestId])
    }

    @Test
    fun `an invalid request id is replaced`() = testApplication {
        installApi()
        val response = client.get("/users/me/library") {
            header(HttpHeaders.XRequestId, "bad id!")
        }
        val requestId = response.headers[HttpHeaders.XRequestId]
        assertNotNull(requestId)
        assertNotEquals("bad id!", requestId)
        assertTrue(Regex("^[0-9a-f]{12}$").matches(requestId))
    }

    @Test
    fun `health checks are not logged`() = testApplication {
        installApi()
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(logCapture.messages.none { it.contains("/health") })
    }

    @Test
    fun `the request line shows status, method and path but never the query string`() = testApplication {
        installApi()
        val response = client.get("/users/me/library?secretQuery=hunter2")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(logCapture.messages.any { it.contains("401 GET /users/me/library") && it.contains("user=-") })
        assertTrue(logCapture.messages.none { it.contains("hunter2") })
    }

    @Test
    fun `the request line shows the signed-in user`() = testApplication {
        installApi()
        val user = newUser()
        val token = jwt.generateAccessToken(user.toString())
        val response = client.get("/users/me/library") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(logCapture.messages.any { it.contains("200 GET /users/me/library") && it.contains("user=$user") })
    }

    @Test
    fun `tokens are never written to the log`() = testApplication {
        installApi()
        val fakeToken = "distinctive-secret-token-12345"
        val fakeRefreshToken = "distinctive-secret-refresh-67890"

        client.get("/users/me/library") {
            header(HttpHeaders.Authorization, "Bearer $fakeToken")
        }
        client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$fakeRefreshToken"}""")
        }

        assertTrue(logCapture.messages.none { it.contains(fakeToken) || it.contains(fakeRefreshToken) })
        assertTrue(logCapture.events.none { event ->
            event.mdcPropertyMap.values.any { it?.contains(fakeToken) == true || it?.contains(fakeRefreshToken) == true }
        })
    }

    @Test
    fun `every line written during a request carries its request id`() = testApplication {
        installApi()
        val expectedTraceId = "trace1234567"
        // Trigger server startup before the test request so startup events don't pollute this test
        client.get("/health")
        logCapture.clear()

        client.get("/users/me/library") {
            header(HttpHeaders.XRequestId, expectedTraceId)
        }
        val events = logCapture.events
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.mdcPropertyMap["callId"] == expectedTraceId })
    }

    @Test
    fun `a slow request is logged as a warning`() = testApplication {
        application {
            configureRequestLogging(slowRequestMillis = 10)
            routing {
                get("/slow") {
                    delay(50)
                    call.respondText("ok")
                }
            }
        }
        val response = client.get("/slow")
        assertEquals(HttpStatusCode.OK, response.status)
        val warnEvents = logCapture.events.filter { it.level == ch.qos.logback.classic.Level.WARN }
        assertTrue(warnEvents.any { it.formattedMessage.contains("Slow request: GET /slow") })
    }

    @Test
    fun `deleting an account logs an event`() = testApplication {
        installApi()
        val user = newUser()
        val token = jwt.generateAccessToken(user.toString())
        val response = client.delete("/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(logCapture.messages.any { it.contains("Deleted account $user") })
    }

    @Test
    fun `saving settings logs an event`() = testApplication {
        installApi()
        val user = newUser()
        val token = jwt.generateAccessToken(user.toString())
        val body = """{"language":"af","themeMode":"LIGHT","homeBackdrop":"COAST","defaultPlatform":"PLAYSTATION","achievementNotifications":true,"rankChangeNotifications":false,"seasonResetNotifications":true,"friendActivityNotifications":false}"""
        val response = client.put("/users/me/settings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(logCapture.messages.any { it.contains("saved settings: language=") })
    }

    @Test
    fun `adding a game to the library logs an event`() = testApplication {
        installApi()
        val user = newUser()
        val token = jwt.generateAccessToken(user.toString())
        val response = client.post("/users/me/library") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":113112,"platform":"PC"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(logCapture.messages.any {
            it.contains("User $user added game 113112 on PC as library entry")
        })
    }
}
//------------------------------EOF------------------------------\\
