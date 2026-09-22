package com.backlogbattlers.api.scoring

import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

private const val DEFAULT_LIMIT = 20
const val MAX_LEADERBOARD_LIMIT = 100

/** One row of the leaderboard. Equal points share a rank (1, 2, 2, 4), never a tie-break by name. */
@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val monthlyPoints: Int,
)

@Serializable
data class LeaderboardResponseDto(
    val monthPeriod: String,
    /** Epoch milliseconds; when this competition resets and a fresh one begins. */
    val seasonEndsAt: Long,
    val entries: List<LeaderboardEntryDto>,
    /** The signed-in caller's own row and rank, present even when it falls outside [entries]. Null when nobody is signed in. */
    val me: LeaderboardEntryDto?,
)

/** A user's points total for [monthPeriod] (the current month by default), or 0 if they have none. */
fun monthlyPointsFor(userId: Uuid, monthPeriod: String = currentMonthPeriod()): Int = transaction {
    MonthlyLeaderboardEntries.selectAll()
        .where { (MonthlyLeaderboardEntries.userId eq userId) and (MonthlyLeaderboardEntries.monthPeriod eq monthPeriod) }
        .singleOrNull()?.get(MonthlyLeaderboardEntries.monthlyPoints) ?: 0
}

/**
 * The top [limit] scorers for [monthPeriod] (the current month by default), plus [caller]'s own
 * row and rank so the app can always pin it, even when they are outside the top [limit].
 * [caller] is null when nobody is signed in; the leaderboard itself needs no sign-in to read.
 *
 * Leaderboard is global across all users for now. `groupId`/friends-only scoping is a stretch
 * goal for the final POE, once a notion of "which group" is settled with the team.
 */
fun monthlyLeaderboard(caller: Uuid?, monthPeriod: String = currentMonthPeriod(), limit: Int = DEFAULT_LIMIT): LeaderboardResponseDto = transaction {
    val rows = MonthlyLeaderboardEntries.selectAll()
        .where { MonthlyLeaderboardEntries.monthPeriod eq monthPeriod }
        .orderBy(MonthlyLeaderboardEntries.monthlyPoints, SortOrder.DESC)
        .toList()

    val ranks = competitionRanks(rows.map { it[MonthlyLeaderboardEntries.monthlyPoints] })
    val userIds = rows.map { it[MonthlyLeaderboardEntries.userId] }
    val usersById = if (userIds.isEmpty()) emptyMap() else {
        Users.selectAll().where { Users.id inList userIds }.associateBy { it[Users.id] }
    }

    val ranked = rows.mapIndexedNotNull { index, row ->
        val user = usersById[row[MonthlyLeaderboardEntries.userId]] ?: return@mapIndexedNotNull null
        LeaderboardEntryDto(
            rank = ranks[index],
            userId = row[MonthlyLeaderboardEntries.userId].toString(),
            displayName = user[Users.displayName],
            avatarUrl = user[Users.avatarUrl],
            monthlyPoints = row[MonthlyLeaderboardEntries.monthlyPoints],
        )
    }

    val me = caller?.let { id -> callerRow(id, ranked) }
    LeaderboardResponseDto(monthPeriod, seasonEndsAt(monthPeriod).toEpochMilli(), ranked.take(limit), me)
}

/** The caller's row from [ranked] if they are on the board, otherwise a zero-point row placed one past the last rank. */
private fun callerRow(caller: Uuid, ranked: List<LeaderboardEntryDto>): LeaderboardEntryDto? {
    ranked.find { it.userId == caller.toString() }?.let { return it }

    val user = Users.selectAll().where { Users.id eq caller }.singleOrNull() ?: return null
    return LeaderboardEntryDto(
        rank = (ranked.maxOfOrNull { it.rank } ?: 0) + 1,
        userId = caller.toString(),
        displayName = user[Users.displayName],
        avatarUrl = user[Users.avatarUrl],
        monthlyPoints = 0,
    )
}

/**
 * Standard competition ranking ("1224 ranking") for points already sorted highest first: tied
 * scores share a rank, and the next distinct score picks up at "1 + how many people rank above
 * it", so a two-way tie for 2nd is followed by 4th, not 3rd. This is the ranking convention
 * sports leaderboards normally use, as opposed to dense ranking, which would give 1, 2, 2, 3.
 */
fun competitionRanks(pointsDescending: List<Int>): List<Int> {
    val ranks = ArrayList<Int>(pointsDescending.size)
    pointsDescending.forEachIndexed { index, points ->
        // A new score's rank is its 1-based position; a tie repeats the rank just above it
        ranks += if (index == 0 || points != pointsDescending[index - 1]) index + 1 else ranks[index - 1]
    }
    return ranks
}
