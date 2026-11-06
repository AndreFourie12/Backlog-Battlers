package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.UserSteamAccounts
import com.backlogbattlers.api.db.VerifiedUnlocks
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.SteamClient
import com.backlogbattlers.api.games.igdbHttpClient
import com.backlogbattlers.api.library.LibraryEntryDto
import com.backlogbattlers.api.scoring.CompleteResponseDto
import com.backlogbattlers.api.scoring.HundredPercentBlock
import com.backlogbattlers.api.scoring.HundredPercentRefusedDto
import com.backlogbattlers.api.scoring.currentMonthPeriod
import com.backlogbattlers.api.scoring.monthlyPointsFor
import com.backlogbattlers.api.steam.SteamCheckStatus
import com.backlogbattlers.api.steam.markVerified
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Marking games complete, end to end, against a fake IGDB and a fake Steam. Achievement points are
 * only paid for achievements the fake Steam says the player has unlocked.
 */
class CompletionRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "completion-routes-${Uuid.random()}")
    private var caller: Uuid? = null
    private val month = currentMonthPeriod() // the routes award points to the real current month

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenJson = """{"access_token":"tok","expires_in":3600,"token_type":"bearer"}"""
    private val playerSteamId = "76561198000000001"

    // The fake IGDB: Hades' Steam listing (empty = the game is not on Steam)
    private var steamListing = """[{"id":1740294,"game":113112,"uid":"1145360","external_game_source":1}]"""

    // The fake Steam: the achievements the player has really unlocked, and how Steam answers
    private var steamUnlocked: Set<String> = emptySet()
    private var steamStatus = HttpStatusCode.OK
    private var steamBody: String? = null // replaces the generated body, e.g. for a private profile
    private val steamRequests = mutableListOf<HttpRequestData>()

    private val allCodes = listOf("A_RARE", "A_COMMON", "A_VERY_RARE", "A_UNKNOWN")

    // Steam lists every achievement of the game with achieved 0 or 1
    private fun playerStatsJson() = steamBody ?: (
        """{"playerstats":{"steamID":"$playerSteamId","gameName":"Hades","success":true,"achievements":[""" +
            allCodes.joinToString(",") { """{"apiname":"$it","achieved":${if (it in steamUnlocked) 1 else 0}}""" } + "]}}"
        )

    private val fakeIgdb = IgdbClient(
        igdbHttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/oauth2/token" -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                    "/v4/external_games" -> respond(steamListing, HttpStatusCode.OK, jsonHeaders)
                    else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                }
            },
        ),
        clientId = "id",
        clientSecret = "secret",
    )

    private val fakeSteam = SteamClient(
        HttpClient(
            MockEngine { request ->
                steamRequests += request
                respond(playerStatsJson(), steamStatus, jsonHeaders)
            },
        ),
        apiKey = "steam-key",
    )

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing {
                completionRoutes(fakeIgdb, fakeSteam) { caller }
                libraryRoutes(fakeIgdb) { caller }
            }
        }
    }

    /** Links the user to a Steam account, so their achievements can be checked. */
    private fun linkSteam(user: Uuid, steamId: String = playerSteamId) = transaction(db) {
        UserSteamAccounts.insert { it[userId] = user; it[UserSteamAccounts.steamId] = steamId }
    }

    private fun newUser(): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = "Tester"
            it[email] = "tester@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    /** Hades, with its real Steam rarity for two achievements and no data for a third. */
    private fun seedHades() = transaction(db) {
        Games.upsert {
            it[id] = 113112
            it[title] = "Hades"
            it[avgCompletionHours] = 70.44444444444444
            it[avg100PercentHours] = 150.75
        }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_RARE"; it[name] = "Rare"; it[rarityPercent] = 5.9 }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_COMMON"; it[name] = "Common"; it[rarityPercent] = 81.9 }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_VERY_RARE"; it[name] = "Very Rare"; it[rarityPercent] = 2.0 }
        Achievements.insert { it[gameId] = 113112; it[achievementId] = "A_UNKNOWN"; it[name] = "Unknown"; it[rarityPercent] = null }
    }

    private fun addEntry(owner: Uuid): Uuid = transaction(db) {
        LibraryEntries.insert {
            it[userId] = owner
            it[gameId] = 113112
            it[platform] = Platform.PC
        }[LibraryEntries.id]
    }

    private fun unlock(entry: Uuid, vararg achievementIds: String) = transaction(db) {
        achievementIds.forEach { id ->
            UnlockedAchievements.insert { it[libraryEntryId] = entry; it[achievementId] = id }
        }
    }

    /** Sets the entry's logged hours directly; the app would normally do this via PATCH. */
    private fun setHoursPlayed(entry: Uuid, hours: Double) = transaction(db) {
        LibraryEntries.update({ LibraryEntries.id eq entry }) { it[hoursPlayed] = hours }
    }

    private suspend fun HttpClient.complete(entry: Uuid, type: CompletionType): HttpResponse =
        post("/users/me/library/$entry/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"completionType":"$type"}""")
        }

    @Test
    fun `completing awards points for unlocked achievements and the game's time to beat`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON") // A_UNKNOWN stays locked
        linkSteam(caller!!)
        steamUnlocked = setOf("A_RARE", "A_COMMON")

        val response = client.complete(entry, CompletionType.MAIN_STORY)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<CompleteResponseDto>(response.bodyAsText())
        assertEquals(SteamCheckStatus.VERIFIED, body.steamStatus)
        assertEquals(emptyList(), body.unverifiedAchievementIds)
        assertEquals(20 + 5, body.achievementPoints) // Rare tier (20) + Common tier (5), not the locked/unknown one
        assertEquals(70, body.completionTimePoints) // Hades' avgCompletionHours, rounded
        assertEquals(0, body.speedBonusPoints) // hoursPlayed was never set, so there is nothing to compare
        assertEquals(25 + 70, body.pointsAwarded)
        assertEquals(body.pointsAwarded, body.monthlyPoints) // this was the user's first completion
        assertEquals(body.monthlyPoints, monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `completion updates the library entry's status and completion type`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)

        client.complete(entry, CompletionType.MAIN_STORY)

        // This test only registers completionRoutes, not libraryRoutes, so check the row directly
        val row = transaction(db) { LibraryEntries.selectAll().single() }
        assertEquals(LibraryStatus.COMPLETED, row[LibraryEntries.status])
        assertEquals(CompletionType.MAIN_STORY, row[LibraryEntries.completionType])
        assertNotNull(row[LibraryEntries.completedAt])
    }

    @Test
    fun `completing the same entry with the same type twice is a 409 and awards nothing extra`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_COMMON")
        linkSteam(caller!!)
        steamUnlocked = setOf("A_COMMON")
        val first = client.complete(entry, CompletionType.MAIN_STORY)
        val firstTotal = Json.decodeFromString<CompleteResponseDto>(first.bodyAsText()).monthlyPoints

        val repeat = client.complete(entry, CompletionType.MAIN_STORY)

        assertEquals(HttpStatusCode.Conflict, repeat.status)
        assertEquals(firstTotal, monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `completing again with a different type pays only for achievements not already counted`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE") // counted on the first completion
        linkSteam(caller!!)
        steamUnlocked = setOf("A_RARE")
        client.complete(entry, CompletionType.MAIN_STORY)

        unlock(entry, "A_VERY_RARE", "A_COMMON", "A_UNKNOWN") // the rest, so 100% is possible
        steamUnlocked = allCodes.toSet()
        val second = client.complete(entry, CompletionType.COMPLETIONIST)

        val body = Json.decodeFromString<CompleteResponseDto>(second.bodyAsText())
        assertEquals(40 + 5 + 0, body.achievementPoints) // the three new ones only, not A_RARE again
        assertEquals(151, body.completionTimePoints) // Hades' avg100PercentHours, rounded
        // total for the month is both completions added together
        assertEquals((20 + 70) + (45 + 151), monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `an achievement with no rarity data earns no points, per the team's decision to only score what Steam actually reports`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_UNKNOWN")
        linkSteam(caller!!)
        steamUnlocked = setOf("A_UNKNOWN")

        val response = client.complete(entry, CompletionType.MAIN_STORY)

        val body = Json.decodeFromString<CompleteResponseDto>(response.bodyAsText())
        assertEquals(0, body.achievementPoints)
        assertEquals(70, body.completionTimePoints) // completion-time points are unaffected
        assertEquals(70, body.pointsAwarded)
    }

    @Test
    fun `finishing well ahead of the average adds a speed bonus on top of the base points`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        setHoursPlayed(entry, 15.0) // Hades' story averages 70.44h; 15h is about 79% faster

        val response = client.complete(entry, CompletionType.MAIN_STORY)

        val body = Json.decodeFromString<CompleteResponseDto>(response.bodyAsText())
        assertEquals(0, body.achievementPoints) // nothing unlocked
        assertEquals(70, body.completionTimePoints)
        assertEquals(75, body.speedBonusPoints) // Ultra Fast tier
        assertEquals(70 + 75, body.pointsAwarded)
    }

    @Test
    fun `never having logged any hours earns no speed bonus, even though that looks fastest of all`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!) // hoursPlayed defaults to 0.0, never set

        val response = client.complete(entry, CompletionType.MAIN_STORY)

        val body = Json.decodeFromString<CompleteResponseDto>(response.bodyAsText())
        assertEquals(0, body.speedBonusPoints)
    }

    @Test
    fun `an achievement Steam does not confirm earns nothing, while the ones it confirms still pay`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON") // the player ticked both...
        linkSteam(caller!!)
        steamUnlocked = setOf("A_COMMON") // ...but Steam says only one was really earned

        val body = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_STORY).bodyAsText())

        assertEquals(SteamCheckStatus.VERIFIED, body.steamStatus)
        assertEquals(5, body.achievementPoints) // Common only
        assertEquals(listOf("A_RARE"), body.unverifiedAchievementIds)
        assertEquals(5 + 70, body.pointsAwarded)
    }

    @Test
    fun `without a linked Steam account no achievement points are paid and Steam is not asked`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE")
        steamUnlocked = setOf("A_RARE") // would be confirmed, if anyone asked

        val body = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_STORY).bodyAsText())

        assertEquals(SteamCheckStatus.NOT_LINKED, body.steamStatus)
        assertEquals(0, body.achievementPoints)
        assertEquals(listOf("A_RARE"), body.unverifiedAchievementIds)
        assertEquals(70, body.pointsAwarded) // completion-time points are unaffected
        assertTrue(steamRequests.isEmpty())
    }

    @Test
    fun `a private Steam profile pays no achievement points and says why`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE")
        linkSteam(caller!!)
        steamStatus = HttpStatusCode.Forbidden
        steamBody = """{"playerstats":{"error":"Profile is not public","success":false}}"""

        val body = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_STORY).bodyAsText())

        assertEquals(SteamCheckStatus.PRIVATE_PROFILE, body.steamStatus)
        assertEquals(0, body.achievementPoints)
        assertEquals(listOf("A_RARE"), body.unverifiedAchievementIds)
    }

    @Test
    fun `a game with no Steam listing cannot have its achievements confirmed`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE")
        linkSteam(caller!!)
        steamUnlocked = setOf("A_RARE")
        steamListing = "[]"

        val body = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_STORY).bodyAsText())

        assertEquals(SteamCheckStatus.NOT_ON_STEAM, body.steamStatus)
        assertEquals(0, body.achievementPoints)
        assertTrue(steamRequests.isEmpty())
    }

    @Test
    fun `a Steam outage completes nothing and costs no points, so the player can simply retry`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE")
        linkSteam(caller!!)
        steamStatus = HttpStatusCode.InternalServerError
        steamBody = ""

        val down = client.complete(entry, CompletionType.MAIN_STORY)

        assertEquals(HttpStatusCode.BadGateway, down.status)
        assertEquals(LibraryStatus.BACKLOG, transaction(db) { LibraryEntries.selectAll().single()[LibraryEntries.status] })
        assertEquals(0, monthlyPointsFor(caller!!, month))

        steamStatus = HttpStatusCode.OK
        steamBody = null
        steamUnlocked = setOf("A_RARE")
        val retry = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_STORY).bodyAsText())
        assertEquals(20, retry.achievementPoints)
    }

    @Test
    fun `an achievement Steam did not confirm yet is still payable by a later completion`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON")
        linkSteam(caller!!)
        steamUnlocked = setOf("A_COMMON") // Steam has one of the two so far
        val story = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_STORY).bodyAsText())
        assertEquals(5, story.achievementPoints)
        assertEquals(listOf("A_RARE"), story.unverifiedAchievementIds)

        steamUnlocked = setOf("A_RARE", "A_COMMON")
        val full = Json.decodeFromString<CompleteResponseDto>(client.complete(entry, CompletionType.MAIN_EXTRA).bodyAsText())

        assertEquals(20, full.achievementPoints) // A_RARE now, and A_COMMON is not paid a second time
        assertEquals(emptyList(), full.unverifiedAchievementIds)
    }

    @Test
    fun `Steam is asked about the linked player and the game's Steam app, and only when something is waiting`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        linkSteam(caller!!)
        client.complete(entry, CompletionType.MAIN_STORY) // nothing unlocked, so nothing to check
        assertTrue(steamRequests.isEmpty())

        unlock(entry, "A_RARE")
        steamUnlocked = setOf("A_RARE")
        client.complete(entry, CompletionType.MAIN_EXTRA)

        val asked = steamRequests.single().url
        assertEquals(playerSteamId, asked.parameters["steamid"])
        assertEquals("1145360", asked.parameters["appid"])

        // everything is paid for now, so a further completion costs no Steam call
        client.complete(entry, CompletionType.COMPLETIONIST)
        assertEquals(1, steamRequests.size)
    }

    @Test
    fun `an unknown or another user's entry is a 404, and an invalid id is a 400`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val owner = newUser()
        val theirEntry = addEntry(owner)

        assertEquals(HttpStatusCode.NotFound, client.complete(Uuid.random(), CompletionType.MAIN_STORY).status)
        assertEquals(HttpStatusCode.NotFound, client.complete(theirEntry, CompletionType.MAIN_STORY).status) // not caller's
        assertEquals(HttpStatusCode.BadRequest, client.post("/users/me/library/not-an-id/complete") {
            contentType(ContentType.Application.Json); setBody("""{"completionType":"MAIN_STORY"}""")
        }.status)
    }

    private suspend fun HttpClient.refusal(entry: Uuid): HundredPercentRefusedDto {
        val response = complete(entry, CompletionType.COMPLETIONIST)
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun verifiedOf(entry: Uuid): Set<String> = transaction(db) {
        VerifiedUnlocks.selectAll().where { VerifiedUnlocks.libraryEntryId eq entry }.map { it[VerifiedUnlocks.achievementId] }.toSet()
    }

    @Test
    fun `100 percent is refused while an achievement is still locked, and nothing is awarded`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON", "A_VERY_RARE") // A_UNKNOWN is still locked
        linkSteam(caller!!)
        steamUnlocked = setOf("A_RARE", "A_COMMON", "A_VERY_RARE")

        val refused = client.refusal(entry)

        assertEquals(HundredPercentBlock.NOT_ALL_UNLOCKED, refused.reason)
        assertEquals(listOf("A_UNKNOWN"), refused.achievementIds)
        assertEquals(0, monthlyPointsFor(caller!!, month))
        assertEquals(LibraryStatus.BACKLOG, transaction(db) { LibraryEntries.selectAll().single()[LibraryEntries.status] })
    }

    @Test
    fun `100 percent is refused when Steam does not confirm every achievement`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, *allCodes.toTypedArray()) // all ticked...
        linkSteam(caller!!)
        steamUnlocked = setOf("A_RARE", "A_COMMON", "A_VERY_RARE") // ...but Steam only has three

        val refused = client.refusal(entry)

        assertEquals(HundredPercentBlock.NOT_VERIFIED, refused.reason)
        assertEquals(listOf("A_UNKNOWN"), refused.achievementIds)
        assertEquals(SteamCheckStatus.VERIFIED, refused.steamStatus)
        assertEquals(0, monthlyPointsFor(caller!!, month))
    }

    @Test
    fun `100 percent is refused without a linked Steam account, since nothing can be confirmed`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, *allCodes.toTypedArray())

        val refused = client.refusal(entry)

        assertEquals(HundredPercentBlock.NOT_VERIFIED, refused.reason)
        assertEquals(SteamCheckStatus.NOT_LINKED, refused.steamStatus)
        assertEquals(allCodes.sorted(), refused.achievementIds)
        assertTrue(steamRequests.isEmpty())
    }

    @Test
    fun `a game with no achievements cannot be completed to 100 percent, but its story can`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        transaction(db) { Games.upsert { it[id] = 5; it[title] = "Plain Game" } }
        val entry = transaction(db) {
            LibraryEntries.insert { it[userId] = caller!!; it[gameId] = 5; it[platform] = Platform.PC }[LibraryEntries.id]
        }

        assertEquals(HundredPercentBlock.NO_ACHIEVEMENTS, client.refusal(entry).reason)
        assertEquals(HttpStatusCode.OK, client.complete(entry, CompletionType.MAIN_STORY).status)
    }

    @Test
    fun `100 percent completes the game once every achievement is unlocked and confirmed, and locks them in`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, *allCodes.toTypedArray())
        linkSteam(caller!!)
        steamUnlocked = allCodes.toSet()

        val response = client.complete(entry, CompletionType.COMPLETIONIST)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<CompleteResponseDto>(response.bodyAsText())
        assertEquals(20 + 5 + 40 + 0, body.achievementPoints)
        assertEquals(151, body.completionTimePoints)
        val row = transaction(db) { LibraryEntries.selectAll().single() }
        assertEquals(LibraryStatus.COMPLETED, row[LibraryEntries.status])
        assertEquals(CompletionType.COMPLETIONIST, row[LibraryEntries.completionType])
        assertEquals(allCodes.toSet(), verifiedOf(entry))
    }

    @Test
    fun `completing the story after 100 percent does not take the 100 percent away`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, *allCodes.toTypedArray())
        linkSteam(caller!!)
        steamUnlocked = allCodes.toSet()
        client.complete(entry, CompletionType.COMPLETIONIST)

        assertEquals(HttpStatusCode.OK, client.complete(entry, CompletionType.MAIN_STORY).status)

        assertEquals(CompletionType.COMPLETIONIST, transaction(db) { LibraryEntries.selectAll().single()[LibraryEntries.completionType] })
    }

    @Test
    fun `achievements an import already confirmed are paid and count towards 100 percent without asking Steam again`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, *allCodes.toTypedArray())
        transaction(db) { markVerified(entry, allCodes) } // what an import records
        linkSteam(caller!!)
        steamUnlocked = emptySet() // Steam would deny them now, but it is never asked

        val response = client.complete(entry, CompletionType.COMPLETIONIST)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(20 + 5 + 40, Json.decodeFromString<CompleteResponseDto>(response.bodyAsText()).achievementPoints)
        assertTrue(steamRequests.isEmpty())
    }

    @Test
    fun `an achievement Steam confirmed cannot be unchecked, while one it did not is removed as asked`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON")
        linkSteam(caller!!)
        steamUnlocked = setOf("A_COMMON")
        client.complete(entry, CompletionType.MAIN_STORY) // confirms A_COMMON only

        val response = client.patch("/users/me/library/$entry") {
            contentType(ContentType.Application.Json)
            setBody("""{"unlockedAchievementIds":[]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated = Json { ignoreUnknownKeys = true }.decodeFromString<LibraryEntryDto>(response.bodyAsText())
        assertEquals(listOf("A_COMMON"), updated.unlockedAchievementIds)
        assertEquals(listOf("A_COMMON"), updated.verifiedAchievementIds)
    }

    @Test
    fun `the library lists which unlocked achievements are confirmed`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)
        unlock(entry, "A_RARE", "A_COMMON")
        transaction(db) { markVerified(entry, listOf("A_RARE")) }

        val listed = Json { ignoreUnknownKeys = true }.decodeFromString<List<LibraryEntryDto>>(client.get("/users/me/library").bodyAsText()).single()

        assertEquals(listOf("A_COMMON", "A_RARE"), listed.unlockedAchievementIds)
        assertEquals(listOf("A_RARE"), listed.verifiedAchievementIds)
    }

    @Test
    fun `nobody signed in is a 401`() = testApplication {
        installApi()
        seedHades()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.complete(Uuid.random(), CompletionType.MAIN_STORY).status)
    }

    @Test
    fun `an unrecognised completion type is a 400`() = testApplication {
        installApi()
        seedHades()
        caller = newUser()
        val entry = addEntry(caller!!)

        val response = client.post("/users/me/library/$entry/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"completionType":"NOT_A_REAL_TYPE"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
