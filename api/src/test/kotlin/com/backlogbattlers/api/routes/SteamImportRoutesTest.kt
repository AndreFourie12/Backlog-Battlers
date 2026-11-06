package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.ActivityEvents
import com.backlogbattlers.api.db.ActivityType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.SteamApps
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.UserSteamAccounts
import com.backlogbattlers.api.db.VerifiedUnlocks
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.steam.SteamImportDto
import com.backlogbattlers.api.steam.SteamImporter
import com.backlogbattlers.api.steam.steamAppIdFor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Importing a Steam library, against fakes of Steam and IGDB that count every request, so the
 * tests can hold the import to its call budget as well as its results.
 */
class SteamImportRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "steam-import-${Uuid.random()}")
    private var caller: Uuid? = null
    private var now = Instant.parse("2026-11-05T10:00:00Z")
    private val playerSteamId = "76561198000000001"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // ---- fake Steam: the player's owned games as (app id, minutes played) ----
    private var owned = listOf(620 to 300, 730 to 90, 999 to 45) // Portal 2, Counter-Strike 2, a tool IGDB doesn't list
    private var steamPrivate = false
    private var steamStatus = HttpStatusCode.OK
    private var steamCalls = 0 // GetOwnedGames, the library listing

    // ---- fake Steam achievements: what the player has unlocked in each app ----
    private val unlockedOnSteam = mutableMapOf(620 to listOf("ACH_PORTAL_A", "ACH_PORTAL_B"), 730 to emptyList())
    private val failingAchievementApps = mutableSetOf<Int>()
    private val unlockTimes = mutableMapOf<String, Instant>() // when Steam says each was unlocked; none by default
    private val achievementCalls = mutableListOf<Int>() // the app id of every achievement read

    private val fakeSteam = SteamClient(
        HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/ISteamUserStats/GetPlayerAchievements/v1/") {
                    val app = request.url.parameters["appid"]!!.toInt()
                    synchronized(achievementCalls) { achievementCalls += app }
                    if (app in failingAchievementApps) return@MockEngine respondError(HttpStatusCode.ServiceUnavailable)
                    val rows = (unlockedOnSteam[app].orEmpty().map { code ->
                        """{"apiname":"$code","achieved":1,"unlocktime":${unlockTimes[code]?.epochSecond ?: 0}}"""
                    } +
                        """{"apiname":"ACH_LOCKED","achieved":0}""").joinToString(",")
                    return@MockEngine respond(
                        """{"playerstats":{"steamID":"$playerSteamId","success":true,"achievements":[$rows]}}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                steamCalls++
                assertEquals("/IPlayerService/GetOwnedGames/v1/", request.url.encodedPath)
                if (steamStatus != HttpStatusCode.OK) return@MockEngine respondError(steamStatus)
                val body = if (steamPrivate) """{"response":{}}""" else
                    """{"response":{"game_count":${owned.size},"games":[""" +
                        owned.joinToString(",") { (app, minutes) -> """{"appid":$app,"playtime_forever":$minutes}""" } + "]}}"
                respond(body, HttpStatusCode.OK, jsonHeaders)
            },
        ),
        apiKey = "steam-key",
    )

    // ---- fake IGDB: which game each Steam app is, and the games' details ----
    private val igdbGameForApp = mutableMapOf(620 to 72, 730 to 1)
    private val igdbTitles = mapOf(72 to "Portal 2", 1 to "Counter-Strike 2", 5 to "Hades")
    private val igdbRequests = mutableListOf<String>() // endpoint of every query, token requests left out

    private val fakeIgdb = IgdbClient(
        igdbHttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                if (path == "/oauth2/token") {
                    return@MockEngine respond("""{"access_token":"tok","expires_in":3600,"token_type":"bearer"}""", HttpStatusCode.OK, jsonHeaders)
                }
                igdbRequests += path.removePrefix("/v4/")
                val query = (request.body as TextContent).text
                val asked = Regex("\\((.*?)\\)").find(query)!!.groupValues[1].split(",").map { it.trim('"', ' ').toInt() }
                val body = when (path) {
                    "/v4/external_games" -> asked.mapNotNull { app -> igdbGameForApp[app]?.let { """{"game":$it,"uid":"$app"}""" } }
                    "/v4/games" -> asked.mapNotNull { id -> igdbTitles[id]?.let { """{"id":$id,"name":"$it"}""" } }
                    "/v4/game_time_to_beats" -> asked.filter { it == 72 }.map { """{"game_id":72,"normally":30600,"completely":72000}""" }
                    else -> error("unexpected IGDB endpoint $path")
                }
                respond(body.joinToString(",", "[", "]"), HttpStatusCode.OK, jsonHeaders)
            },
        ),
        clientId = "id",
        clientSecret = "secret",
    )

    private val importer = SteamImporter(fakeIgdb, fakeSteam, clock = { now })

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { steamImportRoutes(importer) { caller } }
        }
    }

    private fun newUser(linkSteam: Boolean = true): Uuid = transaction(db) {
        val id = Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = "Tester"
            it[email] = "tester@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
        if (linkSteam) UserSteamAccounts.insert { it[userId] = id; it[UserSteamAccounts.steamId] = playerSteamId }
        id
    }

    private fun addEntry(owner: Uuid, gameId: Int, title: String, platform: Platform, hours: Double) = transaction(db) {
        Games.insert { it[Games.id] = gameId; it[Games.title] = title }
        LibraryEntries.insert {
            it[userId] = owner
            it[LibraryEntries.gameId] = gameId
            it[LibraryEntries.platform] = platform
            it[hoursPlayed] = hours
        }
    }

    /** gameId to (platform, status, hours) of every entry in [owner]'s library. */
    private fun library(owner: Uuid) = transaction(db) {
        LibraryEntries.selectAll().where { LibraryEntries.userId eq owner }.associate {
            it[LibraryEntries.gameId] to Triple(it[LibraryEntries.platform], it[LibraryEntries.status], it[LibraryEntries.hoursPlayed])
        }
    }

    /** gameId to the achievement codes unlocked on that game's entry in [owner]'s library. */
    private fun unlocks(owner: Uuid) = transaction(db) {
        val entries = LibraryEntries.selectAll().where { LibraryEntries.userId eq owner }
            .associate { it[LibraryEntries.id] to it[LibraryEntries.gameId] }
        UnlockedAchievements.selectAll().toList()
            .filter { it[UnlockedAchievements.libraryEntryId] in entries }
            .groupBy({ entries.getValue(it[UnlockedAchievements.libraryEntryId]) }, { it[UnlockedAchievements.achievementId] })
            .mapValues { (_, codes) -> codes.toSet() }
    }

    private suspend fun HttpClient.import(): HttpResponse = post("/users/me/steam/import")

    private fun later(duration: Duration) {
        now = now.plus(duration)
    }

    @Test
    fun `importing needs you to be signed in`() = testApplication {
        installApi()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.import().status)
        assertEquals(0, steamCalls)
    }

    @Test
    fun `without a linked steam account there is nothing to import, and steam is not asked`() = testApplication {
        installApi()
        caller = newUser(linkSteam = false)

        val response = client.import()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("No Steam account is linked"))
        assertEquals(0, steamCalls)
    }

    @Test
    fun `an import adds every game the catalogue knows, on PC in the backlog, with steam's hours`() = testApplication {
        installApi()
        caller = newUser()

        val response = client.import()

        assertEquals(HttpStatusCode.OK, response.status)
        val result = Json.decodeFromString<SteamImportDto>(response.bodyAsText())
        assertEquals(3, result.ownedOnSteam)
        assertEquals(2, result.added)
        assertEquals(1, result.notInCatalogue) // app 999
        assertEquals(setOf("Portal 2", "Counter-Strike 2"), result.games.map { it.title }.toSet())
        assertEquals(8.5, result.games.single { it.gameId == 72 }.avgCompletionHours) // 30600 seconds

        assertEquals(
            mapOf(
                72 to Triple(Platform.PC, LibraryStatus.BACKLOG, 5.0), // 300 minutes
                1 to Triple(Platform.PC, LibraryStatus.BACKLOG, 1.5), // 90 minutes
            ),
            library(caller!!),
        )
    }

    @Test
    fun `a whole import costs one steam call and three batched IGDB queries`() = testApplication {
        installApi()
        caller = newUser()

        client.import()

        assertEquals(1, steamCalls)
        assertEquals(listOf("external_games", "games", "game_time_to_beats"), igdbRequests)
        assertEquals(setOf(620, 730), achievementCalls.toSet()) // one read per played game, no more
        assertEquals(2, achievementCalls.size)
    }

    @Test
    fun `importing again with nothing new costs one steam call and no IGDB call at all`() = testApplication {
        installApi()
        caller = newUser()
        client.import()
        igdbRequests.clear()
        later(Duration.ofMinutes(5))

        val result = Json.decodeFromString<SteamImportDto>(client.import().bodyAsText())

        assertEquals(0, result.added)
        assertEquals(2, result.unchanged)
        assertEquals(emptyList(), igdbRequests) // the unmatched tool app 999 is remembered too
        assertEquals(2, steamCalls)
        assertEquals(2, achievementCalls.size) // nothing played since the first import, so no new reads
    }

    @Test
    fun `a game bought since the last import is the only one looked up`() = testApplication {
        installApi()
        caller = newUser()
        client.import()
        igdbRequests.clear()
        later(Duration.ofMinutes(5))
        owned = owned + (1145360 to 600) // Hades
        igdbGameForApp[1145360] = 5

        val result = Json.decodeFromString<SteamImportDto>(client.import().bodyAsText())

        assertEquals(listOf("Hades"), result.games.map { it.title })
        assertEquals(listOf("external_games", "games", "game_time_to_beats"), igdbRequests)
        assertEquals(10.0, library(caller!!).getValue(5).third)
    }

    @Test
    fun `another user importing the same games reuses what the first import looked up`() = testApplication {
        installApi()
        caller = newUser()
        client.import()
        igdbRequests.clear()

        caller = transaction(db) {
            Users.insert {
                it[googleSubjectId] = "google-other"
                it[displayName] = "Other"
                it[email] = "other@example.com"
                it[lastLoginDate] = LocalDate.now()
            }[Users.id].also { id -> UserSteamAccounts.insert { it[userId] = id; it[UserSteamAccounts.steamId] = "76561198000000002" } }
        }
        client.import()

        // the Steam to IGDB matches are shared; only details for the games new to this library are fetched
        assertEquals(listOf("games", "game_time_to_beats"), igdbRequests)
    }

    @Test
    fun `hours on PC only ever go up, and a game kept on another platform is left alone`() = testApplication {
        installApi()
        caller = newUser()
        addEntry(caller!!, gameId = 72, title = "Portal 2", platform = Platform.PC, hours = 1.0) // behind Steam's 5h
        addEntry(caller!!, gameId = 1, title = "Counter-Strike 2", platform = Platform.PLAYSTATION, hours = 0.0)

        val result = Json.decodeFromString<SteamImportDto>(client.import().bodyAsText())

        assertEquals(0, result.added)
        assertEquals(1, result.hoursUpdated)
        assertEquals(1, result.unchanged)
        assertEquals(
            mapOf(
                72 to Triple(Platform.PC, LibraryStatus.BACKLOG, 5.0),
                1 to Triple(Platform.PLAYSTATION, LibraryStatus.BACKLOG, 0.0), // no second, PC copy
            ),
            library(caller!!),
        )

        // hours logged by hand beyond Steam's figure are kept
        later(Duration.ofMinutes(5))
        owned = listOf(620 to 30) // Steam now says half an hour
        assertEquals(0, Json.decodeFromString<SteamImportDto>(client.import().bodyAsText()).hoursUpdated)
        assertEquals(5.0, library(caller!!).getValue(72).third)
    }

    @Test
    fun `importing again within two minutes is refused without asking steam`() = testApplication {
        installApi()
        caller = newUser()
        client.import()

        later(Duration.ofSeconds(30))
        val refused = client.import()

        assertEquals(HttpStatusCode.TooManyRequests, refused.status)
        assertEquals("91", refused.headers[HttpHeaders.RetryAfter])
        assertEquals(1, steamCalls)

        later(Duration.ofSeconds(91))
        assertEquals(HttpStatusCode.OK, client.import().status)
    }

    @Test
    fun `a private profile is a 403, and the user can retry as soon as they make it public`() = testApplication {
        installApi()
        caller = newUser()
        steamPrivate = true

        val refused = client.import()
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertTrue(refused.bodyAsText().contains("private"))
        assertEquals(emptyMap(), library(caller!!))

        steamPrivate = false
        assertEquals(HttpStatusCode.OK, client.import().status)
    }

    @Test
    fun `steam being down is a 502 that changes nothing and can be retried at once`() = testApplication {
        installApi()
        caller = newUser()
        steamStatus = HttpStatusCode.ServiceUnavailable

        assertEquals(HttpStatusCode.BadGateway, client.import().status)
        assertEquals(emptyMap(), library(caller!!))

        steamStatus = HttpStatusCode.OK
        assertEquals(HttpStatusCode.OK, client.import().status)
    }

    @Test
    fun `an app IGDB had no game for is asked about again after thirty days`() = testApplication {
        installApi()
        caller = newUser()
        client.import()
        igdbRequests.clear()

        later(Duration.ofDays(31))
        client.import()

        assertEquals(listOf("external_games"), igdbRequests) // just app 999; nothing new matched
    }

    @Test
    fun `achievement lookups reuse the steam app an import found, without asking IGDB`() = testApplication {
        installApi()
        caller = newUser()
        client.import()
        igdbRequests.clear()

        assertEquals(620, runBlocking { steamAppIdFor(72, fakeIgdb) })
        assertEquals(emptyList(), igdbRequests)
        assertEquals(3, transaction(db) { SteamApps.selectAll().count() })
    }

    @Test
    fun `an import brings in the achievements unlocked on steam, and says how many`() = testApplication {
        installApi()
        caller = newUser()

        val result = Json.decodeFromString<SteamImportDto>(client.import().bodyAsText())

        assertEquals(2, result.achievementsUnlocked)
        // locked achievements are left out, and a game with nothing unlocked has no unlocks
        assertEquals(mapOf(72 to setOf("ACH_PORTAL_A", "ACH_PORTAL_B")), unlocks(caller!!))
    }

    @Test
    fun `unlocks an import brings in are confirmed by steam, so they can no longer be unchecked`() = testApplication {
        installApi()
        caller = newUser()
        client.import()

        val confirmed = transaction(db) { VerifiedUnlocks.selectAll().map { it[VerifiedUnlocks.achievementId] }.toSet() }
        assertEquals(setOf("ACH_PORTAL_A", "ACH_PORTAL_B"), confirmed)

        // a later import confirms what an earlier version of the app only ticked by hand
        transaction(db) { VerifiedUnlocks.deleteAll() }
        later(Duration.ofMinutes(5))
        owned = listOf(620 to 360, 730 to 90, 999 to 45)
        client.import()
        assertEquals(setOf("ACH_PORTAL_A", "ACH_PORTAL_B"), transaction(db) { VerifiedUnlocks.selectAll().map { it[VerifiedUnlocks.achievementId] }.toSet() })
    }

    @Test
    fun `achievements are read again only for a game played since, and unlocks are never removed`() = testApplication {
        installApi()
        caller = newUser()
        client.import()
        achievementCalls.clear()

        later(Duration.ofMinutes(5))
        owned = listOf(620 to 360, 730 to 90, 999 to 45) // an hour more of Portal 2, nothing else
        unlockedOnSteam[620] = listOf("ACH_PORTAL_A", "ACH_PORTAL_C") // B no longer reported
        val result = Json.decodeFromString<SteamImportDto>(client.import().bodyAsText())

        assertEquals(listOf(620), achievementCalls)
        assertEquals(1, result.achievementsUnlocked)
        assertEquals(setOf("ACH_PORTAL_A", "ACH_PORTAL_B", "ACH_PORTAL_C"), unlocks(caller!!).getValue(72))
    }

    @Test
    fun `a game steam cannot read achievements for is retried next time, and the import still works`() = testApplication {
        installApi()
        caller = newUser()
        failingAchievementApps += 620

        val first = client.import()
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(2, library(caller!!).size)
        assertEquals(emptyMap(), unlocks(caller!!))

        later(Duration.ofMinutes(5))
        failingAchievementApps.clear()
        achievementCalls.clear()
        client.import()

        assertEquals(listOf(620), achievementCalls) // only the game that failed; 730 was read already
        assertEquals(setOf("ACH_PORTAL_A", "ACH_PORTAL_B"), unlocks(caller!!).getValue(72))
    }

    @Test
    fun `a game kept on another platform gets no steam achievements`() = testApplication {
        installApi()
        caller = newUser()
        addEntry(caller!!, gameId = 72, title = "Portal 2", platform = Platform.PLAYSTATION, hours = 0.0)

        client.import()

        assertEquals(listOf(730), achievementCalls)
        assertEquals(emptyMap(), unlocks(caller!!))
    }

    @Test
    fun `unlocks keep the time steam gives, and only the last week's reach the activity feed`() = testApplication {
        installApi()
        caller = newUser()
        val recent = now.minus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val old = Instant.parse("2020-03-14T09:26:00Z")
        unlockTimes["ACH_PORTAL_A"] = recent
        unlockTimes["ACH_PORTAL_B"] = old

        client.import()

        val stored = transaction(db) {
            UnlockedAchievements.selectAll().associate { it[UnlockedAchievements.achievementId] to it[UnlockedAchievements.unlockedAt] }
        }
        assertEquals(mapOf("ACH_PORTAL_A" to recent, "ACH_PORTAL_B" to old), stored)
        val feed = transaction(db) { ActivityEvents.selectAll().toList() }.single()
        assertEquals(ActivityType.ACHIEVEMENT_UNLOCKED, feed[ActivityEvents.type])
        assertEquals(1, feed[ActivityEvents.itemCount]) // the 2020 unlock is history, not news
        assertEquals(72, feed[ActivityEvents.gameId])
        assertEquals(recent, feed[ActivityEvents.createdAt])
        assertEquals(caller, feed[ActivityEvents.actorId])
    }

    @Test
    fun `unlocks steam gives no time for are imported but never announced in the feed`() = testApplication {
        installApi()
        caller = newUser()

        client.import()

        assertEquals(2, unlocks(caller!!).getValue(72).size)
        assertEquals(0, transaction(db) { ActivityEvents.selectAll().count() })
    }
}
//------------------------------EOF------------------------------\
