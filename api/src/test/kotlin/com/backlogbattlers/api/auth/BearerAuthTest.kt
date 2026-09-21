package com.backlogbattlers.api.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.backlogbattlers.api.Config
import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.library.LibraryEntryDto
import com.backlogbattlers.api.module
import com.backlogbattlers.api.routes.libraryRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.LocalDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Access tokens put through the real library routes, against an in-memory database. */
class BearerAuthTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "bearer-auth-${Uuid.random()}")
    private val secret = "bearer-test-secret-1234567890"
    private val issuer = "bearer-test-issuer"
    private val jwt = JwtService(secret, issuer)

    // The library routes need an IGDB client to exist, but reading a library never calls it
    private val unusedIgdb = IgdbClient(
        igdbHttpClient(MockEngine { respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }),
        clientId = "id",
        clientSecret = "secret",
    )

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { libraryRoutes(unusedIgdb, bearerCurrentUser(jwt)) }
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

    /** Gives [owner] one library entry and returns its id. */
    private fun addEntryFor(owner: Uuid): String = transaction(db) {
        Games.upsert {
            it[id] = 1
            it[title] = "Hades"
        }
        LibraryEntries.insert {
            it[LibraryEntries.userId] = owner
            it[LibraryEntries.gameId] = 1
            it[LibraryEntries.platform] = Platform.PC
        }[LibraryEntries.id].toString()
    }

    private suspend fun HttpClient.library(authorization: String?): HttpResponse =
        get("/users/me/library") { if (authorization != null) header(HttpHeaders.Authorization, authorization) }

    private suspend fun HttpResponse.entryIds(): List<String> =
        Json.decodeFromString<List<LibraryEntryDto>>(bodyAsText()).map { it.libraryEntryId }

    /** An access token built by hand, for the cases the service would never issue. */
    private fun handMadeToken(userId: String, expiresInMs: Long = 60_000): String =
        JWT.create()
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
            .sign(Algorithm.HMAC256(secret))

    @Test
    fun `an access token identifies its owner, who sees only their own entries`() = testApplication {
        installApi()
        val alice = newUser()
        val bob = newUser()
        val alicesEntry = addEntryFor(alice)
        val bobsEntry = addEntryFor(bob)

        val asAlice = client.library("Bearer ${jwt.generateAccessToken(alice.toString())}")
        val asBob = client.library("Bearer ${jwt.generateAccessToken(bob.toString())}")

        assertEquals(HttpStatusCode.OK, asAlice.status)
        assertEquals(listOf(alicesEntry), asAlice.entryIds())
        assertEquals(listOf(bobsEntry), asBob.entryIds())
    }

    @Test
    fun `the Bearer scheme name is not case sensitive`() = testApplication {
        installApi()
        val token = jwt.generateAccessToken(newUser().toString())

        assertEquals(HttpStatusCode.OK, client.library("bearer $token").status)
        assertEquals(HttpStatusCode.OK, client.library("BEARER $token").status)
    }

    @Test
    fun `a missing, malformed or tampered header is a 401`() = testApplication {
        installApi()
        val token = jwt.generateAccessToken(newUser().toString())
        val unusable = listOf(
            null,
            "Bearer",
            "Bearer ",
            "Basic $token", // right token, wrong scheme
            "Bearer not-a-token",
            "Bearer ${token}x", // last character of the signature changed
            "Bearer ${"a".repeat(5000)}", // absurdly long
        )

        unusable.forEach { header ->
            assertEquals(HttpStatusCode.Unauthorized, client.library(header).status, "header: ${header?.take(40)}")
        }
    }

    @Test
    fun `a refresh token and an expired token are both refused`() = testApplication {
        installApi()
        val user = newUser().toString()

        assertEquals(HttpStatusCode.Unauthorized, client.library("Bearer ${jwt.generateRefreshToken(user)}").status)
        assertEquals(HttpStatusCode.Unauthorized, client.library("Bearer ${handMadeToken(user, expiresInMs = -60_000)}").status)
    }

    @Test
    fun `a valid token for a user that does not exist, or no longer exists, is a 401`() = testApplication {
        installApi()

        val neverExisted = jwt.generateAccessToken(Uuid.random().toString())
        assertEquals(HttpStatusCode.Unauthorized, client.library("Bearer $neverExisted").status)

        val user = newUser()
        val token = jwt.generateAccessToken(user.toString())
        assertEquals(HttpStatusCode.OK, client.library("Bearer $token").status)
        transaction(db) { Users.deleteWhere { Users.id eq user } }
        assertEquals(HttpStatusCode.Unauthorized, client.library("Bearer $token").status)
    }

    @Test
    fun `a token whose user id is not a valid id is a 401`() = testApplication {
        installApi()

        assertEquals(HttpStatusCode.Unauthorized, client.library("Bearer ${jwt.generateAccessToken("not-a-uuid")}").status)
    }

    @Test
    fun `the real application accepts a login access token on the library`() = testApplication {
        // module() opens whatever database is configured; never put test users into a real one
        assumeTrue(Config.default.get("DB_URL") == null, "a real database is configured, so this test is skipped")
        application { module() }
        startApplication()
        // No database is named here on purpose: this uses the one module() just opened, not this class's own
        val user = transaction {
            Users.insert {
                it[googleSubjectId] = "google-${Uuid.random()}"
                it[displayName] = "Tester"
                it[email] = "tester@example.com"
                it[lastLoginDate] = LocalDate.now()
            }[Users.id]
        }
        val realJwt = JwtService(Config.default.get("JWT_SECRET")!!, Config.default.get("JWT_ISSUER")!!)

        assertEquals(HttpStatusCode.Unauthorized, client.library(null).status)
        assertEquals(HttpStatusCode.OK, client.library("Bearer ${realJwt.generateAccessToken(user.toString())}").status)
    }
}
