package com.backlogbattlers.api.notifications

import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.Devices
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.db.SeasonAnnouncements
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.settings.SettingsDto
import com.backlogbattlers.api.settings.saveSettings
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The "a new season has started" push: once per month, to people with the app on a phone, behind their switch. */
class SeasonAnnouncerTest {

    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "season-${Uuid.random()}")

    private data class Sent(val tokens: List<String>, val data: Map<String, String>)

    private val sent = mutableListOf<Sent>()
    private val notifier = Notifier({ tokens, data -> sent += Sent(tokens, data); emptyList() }, launch = Notifier.inline)
    private var now = Instant.parse("2026-09-15T10:00:00Z")
    private val announcer = SeasonAnnouncer(notifier, clock = { now })

    private fun newUser(name: String, phone: String? = null): Uuid = transaction(db) {
        val id = Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "${name.lowercase()}@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
        if (phone != null) Devices.insert { it[token] = phone; it[userId] = id }
        id
    }

    private fun scored(user: Uuid, month: String, points: Int) = transaction(db) {
        MonthlyLeaderboardEntries.insert { it[userId] = user; it[monthPeriod] = month; it[monthlyPoints] = points }
    }

    private fun announced() = transaction(db) { SeasonAnnouncements.selectAll().map { it[SeasonAnnouncements.monthPeriod] }.toSet() }

    private fun everythingOn() = SettingsDto("en", "DARK", "HILLS", Platform.PC, true, true, true, true)

    @Test
    fun `the very first run only records the month it finds, because a season that began weeks ago is not news`() {
        newUser("Ana", "ana-phone")

        assertEquals(0, announcer.announceIfNew())

        assertEquals(emptyList(), sent)
        assertEquals(setOf("2026-09"), announced())
    }

    @Test
    fun `a new month tells everyone with a phone how they finished the month before`() {
        val ana = newUser("Ana", "ana-phone")
        val ben = newUser("Ben", "ben-phone")
        newUser("Cleo") // no phone
        val dev = newUser("Dev", "dev-phone")
        scored(ana, "2026-09", 120)
        scored(ben, "2026-09", 300)
        announcer.announceIfNew() // September is recorded

        now = Instant.parse("2026-10-01T10:00:00Z")
        assertEquals(3, announcer.announceIfNew())

        val byToken = sent.associateBy { it.tokens.single() }
        assertEquals(setOf("ana-phone", "ben-phone", "dev-phone"), byToken.keys)
        byToken.getValue("ana-phone").data.let {
            assertEquals("SEASON_RESET", it["type"])
            assertEquals("2026-10", it["monthPeriod"])
            assertEquals("2", it["lastRank"]) // Ben was ahead of her
            assertEquals("120", it["lastPoints"])
            assertEquals(ana.toString(), it["userId"])
        }
        assertEquals("1", byToken.getValue("ben-phone").data["lastRank"])
        // someone who scored nothing is still told a season began, without a rank
        byToken.getValue("dev-phone").data.let {
            assertTrue("lastRank" !in it)
            assertEquals("0", it["lastPoints"])
        }
        assertEquals(setOf("2026-09", "2026-10"), announced())
        check(dev != ana)
    }

    @Test
    fun `the same month is never announced twice`() {
        newUser("Ana", "ana-phone")
        announcer.announceIfNew()
        now = Instant.parse("2026-10-01T10:00:00Z")
        announcer.announceIfNew()
        sent.clear()

        assertEquals(0, announcer.announceIfNew())
        now = Instant.parse("2026-10-20T10:00:00Z")
        assertEquals(0, announcer.announceIfNew())

        assertEquals(emptyList(), sent)
    }

    @Test
    fun `someone with the season switch off is not told`() {
        val ana = newUser("Ana", "ana-phone")
        newUser("Ben", "ben-phone")
        saveSettings(ana, everythingOn().copy(seasonResetNotifications = false))
        announcer.announceIfNew()

        now = Instant.parse("2026-10-01T10:00:00Z")
        announcer.announceIfNew()

        assertEquals(listOf("ben-phone"), sent.map { it.tokens.single() })
    }

    @Test
    fun `a year ending rolls over to the next one`() {
        val ana = newUser("Ana", "ana-phone")
        now = Instant.parse("2026-12-15T10:00:00Z")
        announcer.announceIfNew()
        scored(ana, "2026-12", 50)

        now = Instant.parse("2027-01-01T10:00:00Z")
        announcer.announceIfNew()

        assertEquals("2027-01", sent.single().data["monthPeriod"])
        assertEquals("50", sent.single().data["lastPoints"])
    }
}
//------------------------------EOF------------------------------\\
