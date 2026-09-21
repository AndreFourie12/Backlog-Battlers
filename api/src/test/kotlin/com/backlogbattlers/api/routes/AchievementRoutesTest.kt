package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.games.AchievementDto
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.library.LibraryEntryDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
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
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** Achievement routes, tested against a fake IGDB, a fake Steam and an in-memory database. */
class AchievementRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "achievement-routes-${Uuid.random()}")
    private var caller: Uuid? = null

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""
    private val hadesJson = """[{"id":113112,"name":"Hades","platforms":[{"id":6,"name":"PC (Microsoft Windows)"}]}]"""
    private val steamListing = """[{"id":1740294,"game":113112,"uid":"1145360","external_game_source":1}]"""
    private val rarityJson = """{"achievementpercentages":{"achievements":[
        {"name":"AchClearTartarus","percent":"81.9"},
        {"name":"AchLeveledKeepsakes","percent":"5.9"},
        {"name":"AchFoundAllSummons","percent":"6.7"}]}}"""
    private val schemaJson = """{"game":{"availableGameStats":{"achievements":[
        {"name":"AchClearTartarus","displayName":"Tartarus Cleared","description":"Clear Tartarus"},
        {"name":"AchLeveledKeepsakes","displayName":"Keepsake Collector","description":"Level up every keepsake"},
        {"name":"AchFoundAllSummons","displayName":"Summoner"}]}}}"""

    private fun fakeIgdb(games: String = hadesJson, externalGames: String = steamListing) = IgdbClient(
        igdbHttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/oauth2/token" -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                    "/v4/games" -> respond(games, HttpStatusCode.OK, jsonHeaders)
                    "/v4/game_time_to_beats" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                    "/v4/external_games" -> respond(externalGames, HttpStatusCode.OK, jsonHeaders)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ),
        clientId = "id",
        clientSecret = "secret",
    )

    private fun fakeSteam(
        seen: MutableList<HttpRequestData> = mutableListOf(),
        apiKey: String = "steam-key",
        rarityStatus: HttpStatusCode = HttpStatusCode.OK,
    ) = SteamClient(
        HttpClient(
            MockEngine { request ->
                seen += request
                when {
                    request.url.encodedPath.contains("GetGlobalAchievementPercentagesForApp") -> respond(rarityJson, rarityStatus)
                    request.url.encodedPath.contains("GetSchemaForGame") -> respond(schemaJson, HttpStatusCode.OK)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ),
        apiKey,
    )

    private fun ApplicationTestBuilder.installApi(igdb: IgdbClient = fakeIgdb(), steam: SteamClient = fakeSteam()) {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                achievementRoutes(igdb, steam)
                libraryRoutes(igdb) { caller }
            }
        }
    }

    private fun storedCount() = transaction(db) { Achievements.selectAll().count() }

    private suspend fun HttpClient.achievements(gameId: Int = 113112): List<AchievementDto> =
        Json.decodeFromString(get("/games/$gameId/achievements").bodyAsText())

    @Test
    fun `achievements come from Steam rarest first, are stored, and later calls do not ask Steam again`() = testApplication {
        val steamCalls = mutableListOf<HttpRequestData>()
        installApi(steam = fakeSteam(steamCalls))

        val first = client.achievements()

        assertEquals(listOf("AchLeveledKeepsakes", "AchFoundAllSummons", "AchClearTartarus"), first.map { it.achievementId })
        assertEquals(listOf(5.9, 6.7, 81.9), first.map { it.rarityPercent })
        assertEquals(listOf("Keepsake Collector", "Summoner", "Tartarus Cleared"), first.map { it.name })
        assertEquals("Level up every keepsake", first[0].description)
        assertEquals(null, first[1].description)
        assertEquals(3, storedCount())

        val callsAfterFirst = steamCalls.size
        assertEquals(first, client.achievements())
        assertEquals(callsAfterFirst, steamCalls.size) // second call was served from our database
    }

    @Test
    fun `without a Steam key the achievements keep Steam's codes as their names`() = testApplication {
        val steamCalls = mutableListOf<HttpRequestData>()
        installApi(steam = fakeSteam(steamCalls, apiKey = ""))

        val list = client.achievements()

        assertEquals("AchLeveledKeepsakes", list[0].name)
        assertEquals(null, list[0].description)
        assertEquals(5.9, list[0].rarityPercent)
        assertFalse(steamCalls.any { it.url.encodedPath.contains("GetSchemaForGame") })
    }

    @Test
    fun `a game with no Steam listing has no achievements and nothing is stored`() = testApplication {
        val steamCalls = mutableListOf<HttpRequestData>()
        installApi(igdb = fakeIgdb(externalGames = "[]"), steam = fakeSteam(steamCalls))

        assertEquals(emptyList(), client.achievements())
        assertEquals(0, storedCount())
        assertEquals(0, steamCalls.size)
    }

    @Test
    fun `a Steam app without achievements has none and nothing is stored`() = testApplication {
        installApi(steam = fakeSteam(rarityStatus = HttpStatusCode.Forbidden))

        assertEquals(emptyList(), client.achievements())
        assertEquals(0, storedCount())
    }

    @Test
    fun `an unknown game is a 404 and a non numeric id is a 400`() = testApplication {
        installApi(igdb = fakeIgdb(games = "[]"))

        assertEquals(HttpStatusCode.NotFound, client.get("/games/1/achievements").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/games/abc/achievements").status)
    }

    @Test
    fun `a Steam outage is a 502 that stores nothing and reveals nothing`() = testApplication {
        installApi(steam = fakeSteam(rarityStatus = HttpStatusCode.InternalServerError))

        val response = client.get("/games/113112/achievements")

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals("""{"error":"Achievement data is unavailable right now"}""", response.bodyAsText())
        assertEquals(0, storedCount())
    }

    @Test
    fun `achievements fetched from Steam can be unlocked in the library`() = testApplication {
        installApi()
        caller = transaction(db) {
            Users.insert {
                it[googleSubjectId] = "google-${Uuid.random()}"
                it[displayName] = "Tester"
                it[email] = "tester@example.com"
                it[lastLoginDate] = LocalDate.now()
            }[Users.id]
        }
        val entry = Json.decodeFromString<LibraryEntryDto>(
            client.post("/users/me/library") {
                contentType(ContentType.Application.Json)
                setBody("""{"gameId":113112,"platform":"PC"}""")
            }.bodyAsText(),
        )
        val rarest = client.achievements().first().achievementId

        val response = client.patch("/users/me/library/${entry.libraryEntryId}") {
            contentType(ContentType.Application.Json)
            setBody("""{"unlockedAchievementIds":["$rarest"]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(rarest), Json.decodeFromString<LibraryEntryDto>(response.bodyAsText()).unlockedAchievementIds)
    }
}
