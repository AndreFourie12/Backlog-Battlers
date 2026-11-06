package com.backlogbattlers.api.profile

import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The badge rules, one by one, on plain facts (no database). */
class BadgesTest {

    private val now = "2026-11"
    private val entryA = Uuid.random()
    private val entryB = Uuid.random()
    private val friend = Uuid.random()
    private val otherFriend = Uuid.random()

    private fun at(text: String): Instant = Instant.parse("${text}T12:00:00Z")

    private fun entry(id: Uuid = entryA, game: Int = 1, platform: Platform = Platform.PC, status: LibraryStatus = LibraryStatus.PLAYING, added: String = "2026-01-01") =
        EntryFacts(id, game, platform, status, at(added))

    private fun unlock(code: String, on: String, rarity: Double? = 50.0, game: Int = 1, entry: Uuid = entryA) =
        UnlockFacts(entry, game, code, at(on), rarity)

    private fun done(game: Int = 1, entry: Uuid = entryA, type: CompletionType = CompletionType.MAIN_STORY, on: String = "2026-10-10") =
        CompletionFacts(entry, game, type, on.take(7), at(on))

    private fun month(month: String, points: Int, rank: Int = 1, vararg friends: Pair<Uuid, Int>) = MonthFacts(month, points, rank, friends.toMap())

    private fun input(
        entries: List<EntryFacts> = emptyList(),
        unlocks: List<UnlockFacts> = emptyList(),
        completions: List<CompletionFacts> = emptyList(),
        games: Map<Int, GameFacts> = emptyMap(),
        months: List<MonthFacts> = emptyList(),
        hasFriends: Boolean = true,
    ) = BadgeInput(entries, unlocks, completions, games, months, hasFriends, currentMonth = now)

    private fun result(badge: Badge, input: BadgeInput) = evaluateBadges(input).single { it.id == badge.name }

    private fun earned(badge: Badge, input: BadgeInput) = result(badge, input).earned

    @Test
    fun `every badge is listed, none earned by an empty profile`() {
        val all = evaluateBadges(input())

        assertEquals(Badge.entries.map { it.name }, all.map { it.id })
        assertEquals(20, all.size)
        assertFalse(all.any { it.earned })
    }

    @Test
    fun `progress never goes over the target`() {
        val many = (1..30).map { unlock("A$it", "2026-10-01", rarity = 0.5) }

        val ghost = result(Badge.GHOST_HUNTER, input(unlocks = many))

        assertEquals(10, ghost.progress)
        assertEquals(10, ghost.target)
        assertTrue(ghost.earned)
    }

    @Test
    fun `first blood is the first unlock`() {
        assertFalse(earned(Badge.FIRST_BLOOD, input()))
        assertTrue(earned(Badge.FIRST_BLOOD, input(unlocks = listOf(unlock("A", "2026-10-01")))))
    }

    @Test
    fun `clean sweep needs a 100 percent completion, a story completion is not one`() {
        assertFalse(earned(Badge.CLEAN_SWEEP, input(completions = listOf(done(type = CompletionType.MAIN_STORY)))))
        assertTrue(earned(Badge.CLEAN_SWEEP, input(completions = listOf(done(type = CompletionType.COMPLETIONIST)))))
    }

    @Test
    fun `completion club counts different games at 100 percent`() {
        val nine = (1..9).map { done(game = it, type = CompletionType.COMPLETIONIST) }
        assertEquals(9, result(Badge.COMPLETION_CLUB, input(completions = nine)).progress)
        assertFalse(earned(Badge.COMPLETION_CLUB, input(completions = nine)))
        // the same game twice is still one game
        assertEquals(9, result(Badge.COMPLETION_CLUB, input(completions = nine + done(game = 1, type = CompletionType.COMPLETIONIST))).progress)
        assertTrue(earned(Badge.COMPLETION_CLUB, input(completions = nine + done(game = 10, type = CompletionType.COMPLETIONIST))))
    }

    @Test
    fun `finisher is three different games in one month, not across months`() {
        val spread = listOf(done(1, on = "2026-08-01"), done(2, on = "2026-09-01"), done(3, on = "2026-10-01"))
        assertFalse(earned(Badge.FINISHER, input(completions = spread)))
        val oneMonth = listOf(done(1, on = "2026-10-01"), done(2, on = "2026-10-05"), done(3, on = "2026-10-20"))
        assertTrue(earned(Badge.FINISHER, input(completions = oneMonth)))
        // a story and a 100% of the same game in a month is one game
        val sameGame = listOf(done(1, on = "2026-10-01"), done(1, type = CompletionType.COMPLETIONIST, on = "2026-10-02"), done(2, on = "2026-10-03"))
        assertFalse(earned(Badge.FINISHER, input(completions = sameGame)))
    }

    @Test
    fun `the long haul is a 100 percent completion of a game that takes over 60 hours`() {
        val long = mapOf(1 to GameFacts(avg100PercentHours = 61.0, genres = emptySet()))
        val short = mapOf(1 to GameFacts(avg100PercentHours = 60.0, genres = emptySet()))
        val full = listOf(done(type = CompletionType.COMPLETIONIST))

        assertTrue(earned(Badge.THE_LONG_HAUL, input(completions = full, games = long)))
        assertFalse(earned(Badge.THE_LONG_HAUL, input(completions = full, games = short)))
        assertFalse(earned(Badge.THE_LONG_HAUL, input(completions = listOf(done()), games = long)))
    }

    @Test
    fun `needle in a haystack is an achievement under 1 percent, 1 percent exactly is not`() {
        assertTrue(earned(Badge.NEEDLE_IN_A_HAYSTACK, input(unlocks = listOf(unlock("A", "2026-10-01", rarity = 0.9)))))
        assertFalse(earned(Badge.NEEDLE_IN_A_HAYSTACK, input(unlocks = listOf(unlock("A", "2026-10-01", rarity = 1.0)))))
        assertFalse(earned(Badge.NEEDLE_IN_A_HAYSTACK, input(unlocks = listOf(unlock("A", "2026-10-01", rarity = null)))))
    }

    @Test
    fun `ghost hunter counts achievements under 5 percent, once each across platforms`() {
        val nine = (1..9).map { unlock("A$it", "2026-10-01", rarity = 4.9) }
        assertFalse(earned(Badge.GHOST_HUNTER, input(unlocks = nine)))
        // the same achievement on a second platform's entry is not a tenth
        assertFalse(earned(Badge.GHOST_HUNTER, input(unlocks = nine + unlock("A1", "2026-10-01", rarity = 4.9, entry = entryB))))
        assertTrue(earned(Badge.GHOST_HUNTER, input(unlocks = nine + unlock("A10", "2026-10-01", rarity = 4.9))))
        assertFalse(earned(Badge.GHOST_HUNTER, input(unlocks = nine + unlock("A10", "2026-10-01", rarity = 5.0))))
    }

    @Test
    fun `statistically improbable is three sub 1 percent achievements in one game`() {
        val twoInOne = listOf(unlock("A", "2026-10-01", 0.5, game = 1), unlock("B", "2026-10-01", 0.5, game = 1), unlock("C", "2026-10-01", 0.5, game = 2, entry = entryB))
        assertFalse(earned(Badge.STATISTICALLY_IMPROBABLE, input(unlocks = twoInOne)))
        assertTrue(earned(Badge.STATISTICALLY_IMPROBABLE, input(unlocks = twoInOne + unlock("D", "2026-10-02", 0.2, game = 1))))
    }

    @Test
    fun `shelf cleaner is a completion more than six months after the game was added`() {
        val entries = listOf(entry(added = "2026-01-10"))
        assertTrue(earned(Badge.SHELF_CLEANER, input(entries = entries, completions = listOf(done(on = "2026-07-11")))))
        assertFalse(earned(Badge.SHELF_CLEANER, input(entries = entries, completions = listOf(done(on = "2026-07-10")))))
    }

    @Test
    fun `dust off is an unlock in a game after 90 days without one`() {
        val gap = listOf(unlock("A", "2026-01-01"), unlock("B", "2026-04-02"))
        assertTrue(earned(Badge.DUST_OFF, input(unlocks = gap)))
        val close = listOf(unlock("A", "2026-01-01"), unlock("B", "2026-03-31"))
        assertFalse(earned(Badge.DUST_OFF, input(unlocks = close)))
        // a gap in one game is not a gap between games
        val twoGames = listOf(unlock("A", "2026-01-01", entry = entryA), unlock("B", "2026-06-01", entry = entryB, game = 2))
        assertFalse(earned(Badge.DUST_OFF, input(unlocks = twoGames)))
    }

    @Test
    fun `cold turkey is a completion in a month where nothing was added`() {
        val quiet = listOf(entry(added = "2026-01-10"))
        assertTrue(earned(Badge.COLD_TURKEY, input(entries = quiet, completions = listOf(done(on = "2026-10-10")))))
        val busy = listOf(entry(added = "2026-01-10"), entry(id = entryB, game = 2, added = "2026-10-02"))
        assertFalse(earned(Badge.COLD_TURKEY, input(entries = busy, completions = listOf(done(on = "2026-10-10")))))
    }

    @Test
    fun `backlog zero needs five games and none left in the backlog`() {
        val five = (1..5).map { entry(id = Uuid.random(), game = it, status = LibraryStatus.PLAYING) }
        assertTrue(earned(Badge.BACKLOG_ZERO, input(entries = five)))
        assertFalse(earned(Badge.BACKLOG_ZERO, input(entries = five.take(4))))
        assertFalse(earned(Badge.BACKLOG_ZERO, input(entries = five.dropLast(1) + entry(id = Uuid.random(), game = 9, status = LibraryStatus.BACKLOG))))
    }

    @Test
    fun `top of the table is a finished month ahead of every friend`() {
        assertTrue(earned(Badge.TOP_OF_THE_TABLE, input(months = listOf(month("2026-09", 100, 1, friend to 50, otherFriend to 99)))))
        assertFalse(earned(Badge.TOP_OF_THE_TABLE, input(months = listOf(month("2026-09", 100, 1, friend to 100)))))
        // a month still being played, or no friends to beat, is not a win
        assertFalse(earned(Badge.TOP_OF_THE_TABLE, input(months = listOf(month("2026-11", 100, 1, friend to 50)))))
        assertFalse(earned(Badge.TOP_OF_THE_TABLE, input(months = listOf(month("2026-09", 100, 1)), hasFriends = false)))
        // friends who did not score do not stop a win
        assertTrue(earned(Badge.TOP_OF_THE_TABLE, input(months = listOf(month("2026-09", 100, 1)))))
    }

    @Test
    fun `photo finish is a win by under 25 points over a friend who scored`() {
        assertTrue(earned(Badge.PHOTO_FINISH, input(months = listOf(month("2026-09", 100, 1, friend to 76)))))
        assertFalse(earned(Badge.PHOTO_FINISH, input(months = listOf(month("2026-09", 100, 1, friend to 75)))))
        assertFalse(earned(Badge.PHOTO_FINISH, input(months = listOf(month("2026-09", 10, 1)))))
        assertFalse(earned(Badge.PHOTO_FINISH, input(months = listOf(month("2026-09", 100, 1, friend to 101)))))
    }

    @Test
    fun `comeback is climbing three places into the next month`() {
        assertTrue(earned(Badge.COMEBACK, input(months = listOf(month("2026-08", 10, 7), month("2026-09", 90, 4)))))
        assertEquals(2, result(Badge.COMEBACK, input(months = listOf(month("2026-08", 10, 7), month("2026-09", 90, 5)))).progress)
        // months with a gap between them are not one month to the next, and dropping is not climbing
        assertFalse(earned(Badge.COMEBACK, input(months = listOf(month("2026-07", 10, 9), month("2026-09", 90, 1)))))
        assertEquals(0, result(Badge.COMEBACK, input(months = listOf(month("2026-08", 10, 1), month("2026-09", 90, 8)))).progress)
    }

    @Test
    fun `nemesis is beating the same friend three finished months running`() {
        val three = listOf(
            month("2026-07", 100, 1, friend to 50),
            month("2026-08", 100, 1, friend to 60),
            month("2026-09", 100, 1, friend to 70),
        )
        assertTrue(earned(Badge.NEMESIS, input(months = three)))
        // a month lost in the middle breaks the run
        val broken = three.toMutableList().also { it[1] = month("2026-08", 40, 2, friend to 60) }
        assertEquals(1, result(Badge.NEMESIS, input(months = broken)).progress)
        // three months over three different friends is not one nemesis
        val different = listOf(
            month("2026-07", 100, 1, friend to 50),
            month("2026-08", 100, 1, otherFriend to 60),
            month("2026-09", 100, 1, Uuid.random() to 70),
        )
        assertFalse(earned(Badge.NEMESIS, input(months = different)))
    }

    @Test
    fun `daily driver is unlocks on seven days in a row`() {
        val six = (1..6).map { unlock("A$it", "2026-10-0$it") }
        assertEquals(6, result(Badge.DAILY_DRIVER, input(unlocks = six)).progress)
        assertTrue(earned(Badge.DAILY_DRIVER, input(unlocks = six + unlock("A7", "2026-10-07"))))
        // two unlocks on one day are one day, and a missed day starts again
        assertFalse(earned(Badge.DAILY_DRIVER, input(unlocks = six + unlock("A7", "2026-10-06") + unlock("A8", "2026-10-08"))))
    }

    @Test
    fun `marathon month is 1000 points in one month, not over several`() {
        assertTrue(earned(Badge.MARATHON_MONTH, input(months = listOf(month("2026-09", 1000)))))
        assertFalse(earned(Badge.MARATHON_MONTH, input(months = listOf(month("2026-08", 600), month("2026-09", 600)))))
        // the month being played counts, since the points are already earned
        assertTrue(earned(Badge.MARATHON_MONTH, input(months = listOf(month("2026-11", 1200)))))
    }

    @Test
    fun `genre tourist is five genres among the games completed`() {
        val games = (1..6).associateWith { GameFacts(null, setOf("Genre $it")) } + (7 to GameFacts(null, setOf("Genre 1", "Genre 2")))
        val four = (1..4).map { done(game = it) }
        assertFalse(earned(Badge.GENRE_TOURIST, input(completions = four, games = games)))
        assertTrue(earned(Badge.GENRE_TOURIST, input(completions = four + done(game = 5), games = games)))
        // genres of a game that was not completed do not count
        assertFalse(earned(Badge.GENRE_TOURIST, input(completions = four, games = games + (8 to GameFacts(null, setOf("Genre 8"))))))
    }

    @Test
    fun `platform agnostic is games on three platforms`() {
        val two = listOf(entry(platform = Platform.PC), entry(id = entryB, game = 2, platform = Platform.XBOX))
        assertFalse(earned(Badge.PLATFORM_AGNOSTIC, input(entries = two)))
        assertTrue(earned(Badge.PLATFORM_AGNOSTIC, input(entries = two + entry(id = Uuid.random(), game = 3, platform = Platform.SWITCH))))
    }
}
//------------------------------EOF------------------------------\\
