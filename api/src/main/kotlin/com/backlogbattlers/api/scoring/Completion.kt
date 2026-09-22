package com.backlogbattlers.api.scoring

import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.CompletionRecords
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.UnlockedAchievements
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid

/** Body of `POST /users/me/library/{id}/complete`. */
@Serializable
data class CompleteRequest(val completionType: CompletionType)

/** Response body: the points this call awarded, and the user's new running total for the month. */
@Serializable
data class CompleteResponseDto(
    val libraryEntryId: String,
    val completionType: CompletionType,
    val achievementPoints: Int,
    val completionTimePoints: Int,
    val speedBonusPoints: Int,
    val pointsAwarded: Int,
    val monthlyPoints: Int,
)

sealed interface CompletionResult {
    data class Completed(val response: CompleteResponseDto) : CompletionResult
    data object NotFound : CompletionResult
    data object AlreadyCompleted : CompletionResult
}

/**
 * Marks one of [owner]'s library entries complete for [completionType], and awards points for it.
 *
 * Achievement points are paid only for unlocks that have never been counted before (see
 * [UnlockedAchievements.countedForPoints]), so completing an entry twice - once for its story,
 * later for 100% - never pays out the same unlock a second time. Completion-time points use the
 * game's average hours for that milestone, with a flat fallback when IGDB has no figure. A speed
 * bonus tops that up when the player's own logged hours beat that average (see [speedBonusPoints]).
 *
 * The same (entry, completionType) pair can only ever be completed once; a repeat call returns
 * [CompletionResult.AlreadyCompleted] rather than awarding points again.
 */
fun completeLibraryEntry(
    owner: Uuid,
    entryId: Uuid,
    completionType: CompletionType,
    monthPeriod: String = currentMonthPeriod(),
): CompletionResult = transaction {
    val entry = LibraryEntries.selectAll()
        .where { (LibraryEntries.id eq entryId) and (LibraryEntries.userId eq owner) }
        .singleOrNull() ?: return@transaction CompletionResult.NotFound

    val alreadyDone = CompletionRecords.selectAll()
        .where { (CompletionRecords.libraryEntryId eq entryId) and (CompletionRecords.completionType eq completionType) }
        .any()
    if (alreadyDone) return@transaction CompletionResult.AlreadyCompleted

    val gameId = entry[LibraryEntries.gameId]
    val game = Games.selectAll().where { Games.id eq gameId }.single()

    // Achievement points: only for unlocks on this entry that have never paid out before.
    // ponytail: a client that unlocks an achievement, has it counted, then removes and re-adds
    // it via PATCH would be paid for it twice, since the re-added row starts uncounted again.
    // Fine for a prototype; a real version would track this on the achievement itself.
    val uncounted = UnlockedAchievements.selectAll()
        .where { (UnlockedAchievements.libraryEntryId eq entryId) and (UnlockedAchievements.countedForPoints eq false) }
        .map { it[UnlockedAchievements.achievementId] }

    val achievementPointsTotal = uncounted.sumOf { achievementId ->
        val rarity = Achievements.selectAll()
            .where { (Achievements.gameId eq gameId) and (Achievements.achievementId eq achievementId) }
            .singleOrNull()?.get(Achievements.rarityPercent)
        achievementPoints(rarity)
    }
    if (uncounted.isNotEmpty()) {
        UnlockedAchievements.update({
            (UnlockedAchievements.libraryEntryId eq entryId) and (UnlockedAchievements.achievementId inList uncounted)
        }) {
            it[countedForPoints] = true
        }
    }

    // "story" uses the average time to finish the main story; "100%" uses the average to fully
    // complete it. MAIN_EXTRA (main story + extras) is treated the same as MAIN_STORY: IGDB's
    // three-tier time-to-beat data (hastily/normally/completely) is not fetched, only two tiers.
    val hours = if (completionType == CompletionType.COMPLETIONIST) {
        game[Games.avg100PercentHours]
    } else {
        game[Games.avgCompletionHours]
    }
    val timePoints = completionTimePoints(hours)
    // Rewards the player's own logged hours beating that average, on top of the base points above
    val speedPoints = speedBonusPoints(actualHours = entry[LibraryEntries.hoursPlayed], averageHours = hours)
    val totalPoints = achievementPointsTotal + timePoints + speedPoints

    CompletionRecords.insert {
        it[CompletionRecords.userId] = owner
        it[CompletionRecords.libraryEntryId] = entryId
        it[CompletionRecords.completionType] = completionType
        it[CompletionRecords.achievementPoints] = achievementPointsTotal
        it[CompletionRecords.completionTimePoints] = timePoints
        it[CompletionRecords.speedBonusPoints] = speedPoints
        it[CompletionRecords.pointsAwarded] = totalPoints
        it[CompletionRecords.monthPeriod] = monthPeriod
    }

    LibraryEntries.update({ LibraryEntries.id eq entryId }) {
        it[LibraryEntries.status] = LibraryStatus.COMPLETED
        it[LibraryEntries.completionType] = completionType
        it[LibraryEntries.completedAt] = Instant.now()
        it[LibraryEntries.hoursPlayedAtCompletion] = entry[LibraryEntries.hoursPlayed]
        it[LibraryEntries.updatedAt] = Instant.now()
    }

    val newMonthlyTotal = addMonthlyPoints(owner, monthPeriod, totalPoints)

    CompletionResult.Completed(
        CompleteResponseDto(
            libraryEntryId = entryId.toString(),
            completionType = completionType,
            achievementPoints = achievementPointsTotal,
            completionTimePoints = timePoints,
            speedBonusPoints = speedPoints,
            pointsAwarded = totalPoints,
            monthlyPoints = newMonthlyTotal,
        ),
    )
}

/**
 * Adds [amount] to [userId]'s running total for [monthPeriod], creating the row if this is
 * their first score of the month. Must be called from inside an existing transaction.
 *
 * ponytail: reads then writes within one transaction rather than an atomic `points + amount`
 * update, so two completions by the same user landing on different connections at the exact
 * same moment could race. Not a concern for a prototype used by one person at a time.
 */
private fun addMonthlyPoints(userId: Uuid, monthPeriod: String, amount: Int): Int {
    val existing = MonthlyLeaderboardEntries.selectAll()
        .where { (MonthlyLeaderboardEntries.userId eq userId) and (MonthlyLeaderboardEntries.monthPeriod eq monthPeriod) }
        .singleOrNull()

    val newTotal = (existing?.get(MonthlyLeaderboardEntries.monthlyPoints) ?: 0) + amount

    if (existing == null) {
        MonthlyLeaderboardEntries.insert {
            it[MonthlyLeaderboardEntries.userId] = userId
            it[MonthlyLeaderboardEntries.monthPeriod] = monthPeriod
            it[MonthlyLeaderboardEntries.monthlyPoints] = newTotal
        }
    } else {
        MonthlyLeaderboardEntries.update({
            (MonthlyLeaderboardEntries.userId eq userId) and (MonthlyLeaderboardEntries.monthPeriod eq monthPeriod)
        }) {
            it[monthlyPoints] = newTotal
        }
    }
    return newTotal
}
