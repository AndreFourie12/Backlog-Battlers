package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.UserSteamAccounts
import com.backlogbattlers.api.db.Users
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
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
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Linking a Steam account, against an in-memory database and a fake login. */
class SteamRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "steam-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { steamRoutes { caller } }
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

    private suspend fun HttpClient.link(steamId: String): HttpResponse =
        put("/users/me/steam") { contentType(ContentType.Application.Json); setBody("""{"steamId":"$steamId"}""") }

    @Test
    fun `nothing is linked until the user links an account`() = testApplication {
        installApi()
        caller = newUser()

        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/steam").status)
    }

    @Test
    fun `linking saves the steam id and it can be read back`() = testApplication {
        installApi()
        caller = newUser()

        assertEquals(HttpStatusCode.OK, client.link("76561198000000001").status)

        val read = client.get("/users/me/steam")
        assertEquals(HttpStatusCode.OK, read.status)
        assertEquals("""{"steamId":"76561198000000001"}""", read.bodyAsText())
    }

    @Test
    fun `linking again replaces the old account instead of adding a second one`() = testApplication {
        installApi()
        caller = newUser()
        client.link("76561198000000001")

        assertEquals(HttpStatusCode.OK, client.link("76561198000000002").status)
        assertEquals(HttpStatusCode.OK, client.link("76561198000000002").status) // same again is fine too

        val rows = transaction(db) { UserSteamAccounts.selectAll().map { it[UserSteamAccounts.steamId] } }
        assertEquals(listOf("76561198000000002"), rows)
    }

    @Test
    fun `an id that is not a 17 digit steam id is a 400 and nothing is saved`() = testApplication {
        installApi()
        caller = newUser()
        val bad = listOf(
            "", "123", "7656119800000000", // too short
            "765611980000000012", // too long
            "76561198000000A01", // not all digits
            "12345678901234567", // 17 digits but not a player account
            " 76561198000000001", // stray space
            "https://steamcommunity.com/id/gaben", // a profile address, not an id
        )

        bad.forEach { assertEquals(HttpStatusCode.BadRequest, client.link(it).status, "id: '$it'") }

        assertEquals(0L, transaction(db) { UserSteamAccounts.selectAll().count() })
    }

    @Test
    fun `a steam account can only belong to one user`() = testApplication {
        installApi()
        caller = newUser()
        client.link("76561198000000001")

        caller = newUser()
        val clash = client.link("76561198000000001")

        assertEquals(HttpStatusCode.Conflict, clash.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/steam").status) // the second user has nothing linked
    }

    @Test
    fun `unlinking removes the account, and unlinking nothing is a 404`() = testApplication {
        installApi()
        caller = newUser()
        client.link("76561198000000001")

        assertEquals(HttpStatusCode.NoContent, client.delete("/users/me/steam").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/steam").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/users/me/steam").status)
    }

    @Test
    fun `every steam route answers 401 when nobody is signed in`() = testApplication {
        installApi()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/steam").status)
        assertEquals(HttpStatusCode.Unauthorized, client.link("76561198000000001").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/users/me/steam").status)
    }
}
