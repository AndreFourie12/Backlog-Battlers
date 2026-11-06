package com.backlogbattlers.api.profile

import com.backlogbattlers.api.db.Achievements
import com.backlogbattlers.api.db.CompletionRecords
import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.Friendships
import com.backlogbattlers.api.db.GameGenres
import com.backlogbattlers.api.db.Games
import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.scoring.currentMonthPeriod
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

// how many of the rarest achievements, and the latest completions, a profile shows
private const val SHOWCASE_SLOTS = 7
private const val RECENT_COMPLETIONS = 5

/** The profile screen's whole answer: who, how far along, the stats, the badges and the two showcases. */
@Serializable
data class ProfileDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    /** Points earned in total, across every month. */
    val xp: Int,
    val level: Int,
    /** XP into the current level, out of [xpForNextLevel] (the XP the whole level takes). */
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val monthlyPoints: Int,
    /** Rank on this month's leaderboard (ties share one), or null before the user has scored this month. */
    val monthRank: Int?,
    val gamesInLibrary: Int,
    val gamesCompleted: Int,
    val achievementsUnlocked: Int,
    val badges: List<BadgeDto>,
    /** The user's rarest unlocked achievements, rarest first. */
    val rarestAchievements: List<ShowcaseAchievementDto>,
    /** The latest completions, newest first. */
    val recentCompletions: List<RecentCompletionDto>,
)

@Serializable
data class ShowcaseAchievementDto(
    val achievementId: String,
    val name: String,
    val iconUrl: String?,
    val rarityPercent: Double,
    val gameId: Int,
    val gameTitle: String?,
)

@Serializable
data class RecentCompletionDto(
    val gameId: Int,
    val gameTitle: String?,
    val completionType: CompletionType,
    val points: Int,
    /** Epoch milliseconds. */
    val completedAt: Long,
)

/** The profile of [userId], or null when there is no such user. */
fun profileFor(userId: Uuid, currentMonth: String = currentMonthPeriod()): ProfileDto? = transaction {
    val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@transaction null

    val entryRows = LibraryEntries.selectAll().where { LibraryEntries.userId eq userId }.toList()
    val entries = entryRows.map {
        EntryFacts(it[LibraryEntries.id], it[LibraryEntries.gameId], it[LibraryEntries.platform], it[LibraryEntries.status], it[LibraryEntries.addedAt])
    }
    val entryGame = entries.associate { it.entryId to it.gameId }

    val completionRows = CompletionRecords.selectAll().where { CompletionRecords.userId eq userId }.toList()
    val completions = completionRows.mapNotNull { row ->
        val gameId = entryGame[row[CompletionRecords.libraryEntryId]] ?: return@mapNotNull null
        CompletionFacts(row[CompletionRecords.libraryEntryId], gameId, row[CompletionRecords.completionType], row[CompletionRecords.monthPeriod], row[CompletionRecords.awardedAt])
    }

    val gameIds = entries.map { it.gameId }.distinct()
    val achievementRows = if (gameIds.isEmpty()) emptyMap() else {
        Achievements.selectAll().where { Achievements.gameId inList gameIds }.associateBy { it[Achievements.gameId] to it[Achievements.achievementId] }
    }
    val unlockRows = if (entries.isEmpty()) emptyList() else {
        UnlockedAchievements.selectAll().where { UnlockedAchievements.libraryEntryId inList entryGame.keys.toList() }.toList()
    }
    val unlocks = unlockRows.map { row ->
        val gameId = entryGame.getValue(row[UnlockedAchievements.libraryEntryId])
        UnlockFacts(
            entryId = row[UnlockedAchievements.libraryEntryId],
            gameId = gameId,
            achievementId = row[UnlockedAchievements.achievementId],
            unlockedAt = row[UnlockedAchievements.unlockedAt],
            rarityPercent = achievementRows[gameId to row[UnlockedAchievements.achievementId]]?.get(Achievements.rarityPercent),
        )
    }

    val gameRows = if (gameIds.isEmpty()) emptyMap() else Games.selectAll().where { Games.id inList gameIds }.associateBy { it[Games.id] }
    val genres = if (gameIds.isEmpty()) emptyMap() else {
        GameGenres.selectAll().where { GameGenres.gameId inList gameIds }.groupBy({ it[GameGenres.gameId] }, { it[GameGenres.genre] })
    }
    val games = gameIds.associateWith { id ->
        GameFacts(gameRows[id]?.get(Games.avg100PercentHours), genres[id].orEmpty().toSet())
    }

    // every month the user scored in, with their rank among everyone who scored that month
    val friendIds = Friendships.selectAll()
        .where { (Friendships.userAId eq userId) or (Friendships.userBId eq userId) }
        .map { if (it[Friendships.userAId] == userId) it[Friendships.userBId] else it[Friendships.userAId] }
    val myMonths = MonthlyLeaderboardEntries.selectAll().where { MonthlyLeaderboardEntries.userId eq userId }
        .associate { it[MonthlyLeaderboardEntries.monthPeriod] to it[MonthlyLeaderboardEntries.monthlyPoints] }
    val monthRows = if (myMonths.isEmpty()) emptyList() else {
        MonthlyLeaderboardEntries.selectAll().where { MonthlyLeaderboardEntries.monthPeriod inList myMonths.keys.toList() }.toList()
    }
    val months = myMonths.map { (month, points) ->
        val inMonth = monthRows.filter { it[MonthlyLeaderboardEntries.monthPeriod] == month }
        MonthFacts(
            month = month,
            points = points,
            rank = 1 + inMonth.count { it[MonthlyLeaderboardEntries.monthlyPoints] > points },
            friendPoints = inMonth.filter { it[MonthlyLeaderboardEntries.userId] in friendIds }
                .associate { it[MonthlyLeaderboardEntries.userId] to it[MonthlyLeaderboardEntries.monthlyPoints] },
        )
    }

    val badges = evaluateBadges(BadgeInput(entries, unlocks, completions, games, months, hasFriends = friendIds.isNotEmpty(), currentMonth = currentMonth))

    val xp = completionRows.sumOf { it[CompletionRecords.pointsAwarded] }
    val level = levelFor(xp)
    val thisMonth = months.firstOrNull { it.month == currentMonth }
    val titles = if (gameIds.isEmpty()) emptyMap() else gameRows.mapValues { it.value[Games.title] }

    val rarest = unlocks.distinctBy { it.gameId to it.achievementId }
        .filter { it.rarityPercent != null }
        .sortedBy { it.rarityPercent }
        .take(SHOWCASE_SLOTS)
        .map { unlock ->
            val row = achievementRows.getValue(unlock.gameId to unlock.achievementId)
            ShowcaseAchievementDto(unlock.achievementId, row[Achievements.name], row[Achievements.iconUrl], unlock.rarityPercent!!, unlock.gameId, titles[unlock.gameId])
        }

    val pointsByCompletion = completionRows.associate { (it[CompletionRecords.libraryEntryId] to it[CompletionRecords.completionType]) to it[CompletionRecords.pointsAwarded] }
    val recent = completions.sortedByDescending { it.awardedAt }.take(RECENT_COMPLETIONS).map {
        RecentCompletionDto(it.gameId, titles[it.gameId], it.type, pointsByCompletion[it.entryId to it.type] ?: 0, it.awardedAt.toEpochMilli())
    }

    ProfileDto(
        userId = userId.toString(),
        displayName = user[Users.displayName],
        avatarUrl = user[Users.avatarUrl],
        xp = xp,
        level = level.level,
        xpIntoLevel = level.xpIntoLevel,
        xpForNextLevel = level.xpForNextLevel,
        monthlyPoints = thisMonth?.points ?: 0,
        monthRank = thisMonth?.takeIf { it.points > 0 }?.rank,
        gamesInLibrary = gameIds.size,
        gamesCompleted = entryRows.filter { it[LibraryEntries.status] == LibraryStatus.COMPLETED }.map { it[LibraryEntries.gameId] }.distinct().size,
        achievementsUnlocked = unlocks.size,
        badges = badges,
        rarestAchievements = rarest,
        recentCompletions = recent,
    )
}
//------------------------------EOF------------------------------\
