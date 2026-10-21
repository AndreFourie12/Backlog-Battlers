package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UserSettings
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.settings.SettingsDto
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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Saving the app's preferences to the account, and reading them back on another phone. */
class SettingsRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "settings-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                settingsRoutes { caller }
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

    private val afrikaansOnPlayStation = SettingsDto(
        language = "af",
        themeMode = "LIGHT",
        homeBackdrop = "COAST",
        defaultPlatform = Platform.PLAYSTATION,
        achievementNotifications = true,
        rankChangeNotifications = false,
        seasonResetNotifications = true,
        friendActivityNotifications = false,
    )

    private suspend fun HttpClient.putSettings(body: String): HttpResponse =
        put("/users/me/settings") { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpClient.putSettings(settings: SettingsDto): HttpResponse =
        putSettings(Json.encodeToString(SettingsDto.serializer(), settings))

    @Test
    fun `settings need you to be signed in`() = testApplication {
        installApi()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/settings").status)
        assertEquals(HttpStatusCode.Unauthorized, client.putSettings(afrikaansOnPlayStation).status)
    }

    @Test
    fun `a user who never saved settings gets a 404, so the app uploads its own`() = testApplication {
        installApi()
        caller = newUser()

        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/settings").status)
    }

    @Test
    fun `saved settings come back exactly, as they would on another phone`() = testApplication {
        installApi()
        caller = newUser()

        val saved = client.putSettings(afrikaansOnPlayStation)
        assertEquals(HttpStatusCode.OK, saved.status)
        val savedBody = Json.decodeFromString<SettingsDto>(saved.bodyAsText())
        assertNotNull(savedBody.updatedAt)

        val read = Json.decodeFromString<SettingsDto>(client.get("/users/me/settings").bodyAsText())
        assertEquals(afrikaansOnPlayStation, read.copy(updatedAt = null))
        assertEquals(savedBody.updatedAt, read.updatedAt)
    }

    @Test
    fun `saving again replaces the settings, and the newest save wins`() = testApplication {
        installApi()
        caller = newUser()
        client.putSettings(afrikaansOnPlayStation)

        val first = Json.decodeFromString<SettingsDto>(client.get("/users/me/settings").bodyAsText())
        val changed = afrikaansOnPlayStation.copy(language = "zu", defaultPlatform = Platform.SWITCH)
        client.putSettings(changed)

        val read = Json.decodeFromString<SettingsDto>(client.get("/users/me/settings").bodyAsText())
        assertEquals(changed, read.copy(updatedAt = null))
        assertTrue(read.updatedAt!! >= first.updatedAt!!)
        assertEquals(1, transaction(db) { UserSettings.selectAll().count() })
    }

    @Test
    fun `each user only ever sees their own settings`() = testApplication {
        installApi()
        caller = newUser()
        client.putSettings(afrikaansOnPlayStation)

        caller = newUser()

        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/settings").status)
    }

    @Test
    fun `values the app does not offer are a 400 with a clear message`() = testApplication {
        installApi()
        caller = newUser()

        val badLanguage = client.putSettings(afrikaansOnPlayStation.copy(language = "fr"))
        assertEquals(HttpStatusCode.BadRequest, badLanguage.status)
        assertTrue(badLanguage.bodyAsText().contains("language must be one of en, af, zu"))

        assertEquals(HttpStatusCode.BadRequest, client.putSettings(afrikaansOnPlayStation.copy(themeMode = "PURPLE")).status)
        assertEquals(HttpStatusCode.BadRequest, client.putSettings(afrikaansOnPlayStation.copy(homeBackdrop = "SPACE")).status)
        assertEquals(HttpStatusCode.BadRequest, client.putSettings(afrikaansOnPlayStation.copy(defaultPlatform = Platform.OTHER)).status)
        // a platform that does not exist at all fails to parse
        assertEquals(HttpStatusCode.BadRequest, client.putSettings("""{"language":"en","defaultPlatform":"DREAMCAST"}""").status)

        // nothing was stored by any of them
        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/settings").status)
    }

    @Test
    fun `deleting the account deletes its settings`() = testApplication {
        installApi()
        caller = newUser()
        client.putSettings(afrikaansOnPlayStation)

        client.delete("/users/me")

        assertEquals(0, transaction(db) { UserSettings.selectAll().count() })
    }
}
//------------------------------EOF------------------------------\\
