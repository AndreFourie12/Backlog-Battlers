package com.backlogbattlers.api.scoring

import com.backlogbattlers.api.db.DatabaseFactory
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Users
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class LeaderboardTest {

    @Test
    fun `competition ranking gives a two way tie the same rank and skips the rank after it`() {
        assertEquals(listOf(1, 2, 2, 4), competitionRanks(listOf(100, 80, 80, 50)))
    }

    @Test
    fun `a run of everybody tied gives everybody rank 1`() {
        assertEquals(listOf(1, 1, 1), competitionRanks(listOf(50, 50, 50)))
    }

    @Test
    fun `no ties at all is a plain 1, 2, 3`() {
        assertEquals(listOf(1, 2, 3), competitionRanks(listOf(30, 20, 10)))
    }

    @Test
    fun `an empty list has no ranks`() {
        assertEquals(emptyList(), competitionRanks(emptyList()))
    }

    // The tests below need a database, since monthlyLeaderboard and monthlyPointsFor read one.
    private val db = DatabaseFactory.init(jdbcUrl = null, memoryName = "leaderboard-${Uuid.random()}")
    private val month = "2026-09"

    private fun newUser(name: String): Uuid = transaction(db) {
        Users.insert {
            it[googleSubjectId] = "google-${Uuid.random()}"
            it[displayName] = name
            it[email] = "$name@example.com"
            it[lastLoginDate] = LocalDate.now()
        }[Users.id]
    }

    private fun givePoints(user: Uuid, points: Int) = transaction(db) {
        MonthlyLeaderboardEntries.insert {
            it[MonthlyLeaderboardEntries.userId] = user
            it[monthPeriod] = month
            it[monthlyPoints] = points
        }
    }

    @Test
    fun `monthlyPointsFor is 0 for someone with no score this month`() {
        val user = newUser("Nobody")
        assertEquals(0, monthlyPointsFor(user, month))
    }

    @Test
    fun `monthlyPointsFor reads back what was stored`() {
        val user = newUser("Scorer")
        givePoints(user, 340)
        assertEquals(340, monthlyPointsFor(user, month))
    }

    @Test
    fun `the leaderboard is ordered highest first with the real ranking rule applied`() {
        val alice = newUser("Alice")
        val bob = newUser("Bob")
        val cara = newUser("Cara")
        givePoints(alice, 500)
        givePoints(bob, 300)
        givePoints(cara, 300) // ties with Bob

        val board = monthlyLeaderboard(caller = null, monthPeriod = month)

        assertEquals(listOf("Alice", "Bob", "Cara"), board.entries.map { it.displayName })
        assertEquals(listOf(1, 2, 2), board.entries.map { it.rank })
        assertEquals(month, board.monthPeriod)
    }

    @Test
    fun `the caller's own row is pinned even when it falls outside the returned page`() {
        val leader = newUser("Leader")
        val last = newUser("LastPlace")
        givePoints(leader, 1000)
        givePoints(last, 1)

        val board = monthlyLeaderboard(caller = last, monthPeriod = month, limit = 1)

        assertEquals(listOf("Leader"), board.entries.map { it.displayName })
        assertEquals("LastPlace", board.me?.displayName)
        assertEquals(2, board.me?.rank)
        assertEquals(1, board.me?.monthlyPoints)
    }

    @Test
    fun `a caller with no score this month is placed one past everyone else, at zero points`() {
        val alice = newUser("Alice")
        val bob = newUser("Bob")
        givePoints(alice, 100)
        givePoints(bob, 100) // ties with Alice, both rank 1

        val unscored = newUser("Unscored")
        val board = monthlyLeaderboard(caller = unscored, monthPeriod = month)

        assertEquals(2, board.me?.rank) // one past the highest rank actually used (1), not 3
        assertEquals(0, board.me?.monthlyPoints)
    }

    @Test
    fun `nobody signed in means no me row, but the board itself still comes back`() {
        givePoints(newUser("Someone"), 50)

        val board = monthlyLeaderboard(caller = null, monthPeriod = month)

        assertNull(board.me)
        assertEquals(1, board.entries.size)
    }

    @Test
    fun `an empty month has an empty board and no crash for a signed-in caller`() {
        val user = newUser("EarlyBird")

        val board = monthlyLeaderboard(caller = user, monthPeriod = "2020-01")

        assertEquals(emptyList(), board.entries)
        assertEquals(1, board.me?.rank)
        assertEquals(0, board.me?.monthlyPoints)
    }
}
