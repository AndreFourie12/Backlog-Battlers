package com.backlogbattlers.api.routes

import com.backlogbattlers.api.configureErrors
import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.CompletionRecords
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.GameGenres
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.games.GameDto
import com.backlogbattlers.api.games.saveGame
import com.backlogbattlers.api.profile.ProfileDto
import com.backlogbattlers.api.scoring.currentMonthPeriod
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** The profile: what it works out from a user's library, completions and points. */
class ProfileRoutesTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "profile-routes-${Uuid.random()}")
    private var caller: Uuid? = null
    private val month = currentMonthPeriod()

    private fun ApplicationTestBuilder.installApi() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { profileRoutes { caller } }
        }
    }

    private fun newUser(name: String = "Dylan"): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "${name.lowercase()}@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    private fun seedGame(id: Int, title: String) = transaction(db) {
        Games.insert { it[Games.id] = id; it[Games.title] = title }
    }

    private fun addEntry(owner: Uuid, gameId: Int, status: LibraryStatus = LibraryStatus.PLAYING, platform: Platform = Platform.PC): Uuid = transaction(db) {
        LibraryEntries.insert {
            it[userId] = owner
            it[LibraryEntries.gameId] = gameId
            it[LibraryEntries.platform] = platform
            it[LibraryEntries.status] = status
        }[LibraryEntries.id]
    }

    private fun complete(owner: Uuid, entry: Uuid, type: CompletionType, points: Int, at: String) = transaction(db) {
        CompletionRecords.insert {
            it[userId] = owner
            it[libraryEntryId] = entry
            it[completionType] = type
            it[achievementPoints] = 0
            it[completionTimePoints] = points
            it[speedBonusPoints] = 0
            it[pointsAwarded] = points
            it[monthPeriod] = at.take(7)
            it[awardedAt] = Instant.parse("${at}T12:00:00Z")
        }
    }

    private fun achievement(gameId: Int, code: String, rarity: Double?) = transaction(db) {
        Achievements.insert {
            it[Achievements.gameId] = gameId
            it[achievementId] = code
            it[name] = "$code name"
            it[rarityPercent] = rarity
            it[iconUrl] = "https://icons.test/$code.jpg"
        }
    }

    private fun unlock(entry: Uuid, code: String) = transaction(db) {
        UnlockedAchievements.insert { it[libraryEntryId] = entry; it[achievementId] = code }
    }

    private fun points(user: Uuid, period: String, amount: Int) = transaction(db) {
        MonthlyLeaderboardEntries.insert { it[userId] = user; it[monthPeriod] = period; it[monthlyPoints] = amount }
    }

    private suspend fun ApplicationTestBuilder.profile(): ProfileDto {
        val response = client.get("/users/me/profile")
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun `the profile needs a signed in user`() = testApplication {
        installApi()
        caller = null

        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me/profile").status)
    }

    @Test
    fun `a user that no longer exists is 404`() = testApplication {
        installApi()
        caller = Uuid.random()

        assertEquals(HttpStatusCode.NotFound, client.get("/users/me/profile").status)
    }

    @Test
    fun `a new user is level 1 with empty stats and no badges`() = testApplication {
        installApi()
        caller = newUser("Dylan")

        val p = profile()

        assertEquals("Dylan", p.displayName)
        assertEquals(0, p.xp)
        assertEquals(1, p.level)
        assertEquals(0, p.xpIntoLevel)
        assertEquals(100, p.xpForNextLevel)
        assertEquals(0, p.gamesInLibrary)
        assertEquals(0, p.gamesCompleted)
        assertEquals(0, p.achievementsUnlocked)
        assertNull(p.monthRank)
        assertEquals(20, p.badges.size)
        assertEquals(0, p.badges.count { it.earned })
        assertEquals(emptyList(), p.rarestAchievements)
        assertEquals(emptyList(), p.recentCompletions)
    }

    @Test
    fun `xp is every point earned in any month, and the level follows from it`() = testApplication {
        installApi()
        seedGame(1, "Hades")
        seedGame(2, "Celeste")
        val me = newUser()
        val hades = addEntry(me, 1, LibraryStatus.COMPLETED)
        val celeste = addEntry(me, 2, LibraryStatus.COMPLETED)
        complete(me, hades, CompletionType.MAIN_STORY, 120, "2026-08-10")
        complete(me, celeste, CompletionType.MAIN_STORY, 130, "2026-09-10")
        caller = me

        val p = profile()

        assertEquals(250, p.xp)
        assertEquals(2, p.level) // 100 XP reaches level 2, 300 reaches level 3
        assertEquals(150, p.xpIntoLevel)
        assertEquals(200, p.xpForNextLevel)
    }

    @Test
    fun `the stats count games, completed games, achievements and this months rank`() = testApplication {
        installApi()
        seedGame(1, "Hades")
        seedGame(2, "Celeste")
        seedGame(3, "Portal 2")
        achievement(1, "H1", 10.0)
        achievement(1, "H2", 20.0)
        val me = newUser()
        val rival = newUser("Rival")
        val behind = newUser("Behind")
        val hades = addEntry(me, 1, LibraryStatus.COMPLETED)
        addEntry(me, 2, LibraryStatus.BACKLOG)
        addEntry(me, 3, LibraryStatus.PLAYING, Platform.XBOX)
        // the same game on two platforms is one game in the library
        addEntry(me, 1, LibraryStatus.PLAYING, Platform.SWITCH)
        unlock(hades, "H1")
        unlock(hades, "H2")
        points(me, month, 80)
        points(rival, month, 200)
        points(behind, month, 10)
        points(rival, "2020-01", 9999) // another month's points do not change this month's rank
        caller = me

        val p = profile()

        assertEquals(3, p.gamesInLibrary)
        assertEquals(1, p.gamesCompleted)
        assertEquals(2, p.achievementsUnlocked)
        assertEquals(80, p.monthlyPoints)
        assertEquals(2, p.monthRank)
    }

    @Test
    fun `the showcase is the rarest unlocked achievements, rarest first, at most seven`() = testApplication {
        installApi()
        seedGame(1, "Hades")
        val me = newUser()
        val entry = addEntry(me, 1)
        (1..9).forEach { achievement(1, "A$it", rarity = it * 3.0) }
        achievement(1, "NO_RARITY", null)
        achievement(1, "NOT_MINE", 0.1)
        (1..9).forEach { unlock(entry, "A$it") }
        unlock(entry, "NO_RARITY")
        caller = me

        val p = profile()

        assertEquals((1..7).map { "A$it" }, p.rarestAchievements.map { it.achievementId })
        assertEquals(3.0, p.rarestAchievements.first().rarityPercent)
        assertEquals("A1 name", p.rarestAchievements.first().name)
        assertEquals("https://icons.test/A1.jpg", p.rarestAchievements.first().iconUrl)
        assertEquals("Hades", p.rarestAchievements.first().gameTitle)
    }

    @Test
    fun `recent completions are the latest five with their points`() = testApplication {
        installApi()
        val me = newUser()
        (1..6).forEach { n ->
            seedGame(n, "Game $n")
            complete(me, addEntry(me, n, LibraryStatus.COMPLETED), CompletionType.MAIN_STORY, n * 10, "2026-10-0$n")
        }
        caller = me

        val recent = profile().recentCompletions

        assertEquals(listOf("Game 6", "Game 5", "Game 4", "Game 3", "Game 2"), recent.map { it.gameTitle })
        assertEquals(listOf(60, 50, 40, 30, 20), recent.map { it.points })
        assertEquals(CompletionType.MAIN_STORY, recent.first().completionType)
    }

    @Test
    fun `badges are worked out from the data`() = testApplication {
        installApi()
        seedGame(1, "Hades")
        achievement(1, "RARE", 0.4)
        val me = newUser()
        val entry = addEntry(me, 1, LibraryStatus.COMPLETED)
        unlock(entry, "RARE")
        complete(me, entry, CompletionType.COMPLETIONIST, 200, "2026-09-10")
        caller = me

        val earned = profile().badges.filter { it.earned }.map { it.id }.toSet()

        assertEquals(setOf("FIRST_BLOOD", "CLEAN_SWEEP", "NEEDLE_IN_A_HAYSTACK"), earned.intersect(setOf("FIRST_BLOOD", "CLEAN_SWEEP", "NEEDLE_IN_A_HAYSTACK", "DAILY_DRIVER", "COMPLETION_CLUB")))
    }

    @Test
    fun `saving a game keeps its genres, and a copy without genres does not wipe them`() {
        fun dto(genres: List<String>) = GameDto(gameId = 7, title = "Hades", coverImageUrl = null, platforms = emptyList(), avgCompletionHours = null, avg100PercentHours = null, genres = genres)

        saveGameIn { saveGame(dto(listOf("Roguelike", "Action", "Roguelike"))) }
        assertEquals(setOf("Roguelike", "Action"), genresOf(7))

        saveGameIn { saveGame(dto(emptyList())) }
        assertEquals(setOf("Roguelike", "Action"), genresOf(7))

        saveGameIn { saveGame(dto(listOf("Indie"))) }
        assertEquals(setOf("Indie"), genresOf(7))
    }

    private fun saveGameIn(block: () -> Unit) = block()

    private fun genresOf(gameId: Int) = transaction(db) {
        GameGenres.selectAll().where { GameGenres.gameId eq gameId }.map { it[GameGenres.genre] }.toSet()
    }
}
//------------------------------EOF------------------------------\\
