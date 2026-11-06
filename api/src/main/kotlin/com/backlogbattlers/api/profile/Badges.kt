package com.backlogbattlers.api.profile

import com.backlogbattlers.api.db.CompletionType
import com.backlogbattlers.api.db.LibraryStatus
import com.backlogbattlers.api.db.Platform
import com.backlogbattlers.api.scoring.competitionDate
import com.backlogbattlers.api.scoring.currentMonthPeriod
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import kotlin.uuid.Uuid

/**
 * Every badge, with how much of it takes to earn it ([target]). A badge is earned when its progress
 * reaches its target. Progress is worked out from what is stored each time the profile is read, so
 * nothing is saved about a badge, and the rules below can be changed without migrating anything.
 * The app owns the names and descriptions (in three languages); only the id travels.
 */
enum class Badge(val target: Int) {
    /** Unlock an achievement in any tracked game. */
    FIRST_BLOOD(1),

    /** Reach 100% completion on a game. */
    CLEAN_SWEEP(1),

    /** Hit 100% on ten different games. */
    COMPLETION_CLUB(10),

    /** Complete three games in one month. */
    FINISHER(3),

    /** Fully complete a game whose average time to 100% is over 60 hours. */
    THE_LONG_HAUL(1),

    /** Unlock an achievement fewer than 1% of players have. */
    NEEDLE_IN_A_HAYSTACK(1),

    /** Unlock ten achievements rarer than 5%. */
    GHOST_HUNTER(10),

    /** Unlock three sub-1% achievements in the same game. */
    STATISTICALLY_IMPROBABLE(3),

    /** Finish a game that was in the library for over six months before it was completed. */
    SHELF_CLEANER(1),

    /** Unlock an achievement in a game after 90 days without unlocking anything in it. */
    DUST_OFF(1),

    /** Complete a game in a month where nothing new was added to the library. */
    COLD_TURKEY(1),

    /** Nothing left in the backlog, with at least five games in the library. */
    BACKLOG_ZERO(5),

    /** Finish a month ahead of every friend. */
    TOP_OF_THE_TABLE(1),

    /** Win a month against a friend by fewer than 25 points. */
    PHOTO_FINISH(1),

    /** Climb three or more places on the leaderboard from one month to the next. */
    COMEBACK(3),

    /** Finish above the same friend three months running. */
    NEMESIS(3),

    /** Unlock an achievement on seven days in a row. */
    DAILY_DRIVER(7),

    /** Earn 1,000 points in one month. */
    MARATHON_MONTH(1000),

    /** Complete games across five different genres. */
    GENRE_TOURIST(5),

    /** Track games on three different platforms. */
    PLATFORM_AGNOSTIC(3),
}

/** One badge as the app gets it. [progress] never goes over [target]; the badge is [earned] when it reaches it. */
@Serializable
data class BadgeDto(val id: String, val progress: Int, val target: Int, val earned: Boolean)

// ---- what the rules read: plain facts, so they can be tested without a database ----

/** A game in the library. */
data class EntryFacts(val entryId: Uuid, val gameId: Int, val platform: Platform, val status: LibraryStatus, val addedAt: Instant)

/** One unlocked achievement; [rarityPercent] is null when Steam never told us how rare it is. */
data class UnlockFacts(val entryId: Uuid, val gameId: Int, val achievementId: String, val unlockedAt: Instant, val rarityPercent: Double?)

/** One completion record. [monthPeriod] is the month it paid out in, e.g. "2026-09". */
data class CompletionFacts(val entryId: Uuid, val gameId: Int, val type: CompletionType, val monthPeriod: String, val awardedAt: Instant)

/** What a badge needs to know about a game besides what the user did in it. */
data class GameFacts(val avg100PercentHours: Double?, val genres: Set<String>)

/**
 * One month the user scored in: their [points], their rank among everyone who scored that month
 * ([rank], ties share one), and the points of each of their friends who scored in it.
 */
data class MonthFacts(val month: String, val points: Int, val rank: Int, val friendPoints: Map<Uuid, Int>)

data class BadgeInput(
    val entries: List<EntryFacts>,
    val unlocks: List<UnlockFacts>,
    val completions: List<CompletionFacts>,
    val games: Map<Int, GameFacts>,
    val months: List<MonthFacts>,
    val hasFriends: Boolean,
    // months before this one are finished; this one is still being played
    val currentMonth: String = currentMonthPeriod(),
)

private const val ULTRA_RARE_PERCENT = 1.0
private const val RARE_PERCENT = 5.0
private const val LONG_HAUL_HOURS = 60.0
private const val DUST_OFF_DAYS = 90L
private const val PHOTO_FINISH_MARGIN = 25
private const val SHELF_MONTHS = 6L

/** Every badge, in order, with how far along the user is. */
fun evaluateBadges(input: BadgeInput): List<BadgeDto> = Badge.entries.map { badge ->
    val progress = progressOf(badge, input).coerceIn(0, badge.target)
    BadgeDto(badge.name, progress, badge.target, earned = progress >= badge.target)
}

private fun progressOf(badge: Badge, input: BadgeInput): Int = when (badge) {
    Badge.FIRST_BLOOD -> input.unlocks.size.coerceAtMost(1)

    Badge.CLEAN_SWEEP -> if (input.fullCompletions().isNotEmpty()) 1 else 0

    Badge.COMPLETION_CLUB -> input.fullCompletions().map { it.gameId }.distinct().size

    // the most different games finished in any one month
    Badge.FINISHER -> input.completions.groupBy { it.monthPeriod }.maxOfOrNull { (_, done) -> done.map { it.gameId }.distinct().size } ?: 0

    Badge.THE_LONG_HAUL -> if (input.fullCompletions().any { (input.games[it.gameId]?.avg100PercentHours ?: 0.0) > LONG_HAUL_HOURS }) 1 else 0

    Badge.NEEDLE_IN_A_HAYSTACK -> if (input.distinctUnlocks().any { it.isRarerThan(ULTRA_RARE_PERCENT) }) 1 else 0

    Badge.GHOST_HUNTER -> input.distinctUnlocks().count { it.isRarerThan(RARE_PERCENT) }

    Badge.STATISTICALLY_IMPROBABLE ->
        input.distinctUnlocks().filter { it.isRarerThan(ULTRA_RARE_PERCENT) }.groupBy { it.gameId }.maxOfOrNull { it.value.size } ?: 0

    Badge.SHELF_CLEANER -> {
        val addedAt = input.entries.associate { it.entryId to competitionDate(it.addedAt) }
        // "over six months" is counted in calendar months from the day it was added
        val shelved = input.completions.any { done ->
            val added = addedAt[done.entryId]
            added != null && competitionDate(done.awardedAt).isAfter(added.plusMonths(SHELF_MONTHS))
        }
        if (shelved) 1 else 0
    }

    // there is no record of when a game was last played, so a game counts as dusty when 90 days
    // passed between two of its unlocks
    Badge.DUST_OFF -> {
        val dusty = input.unlocks.groupBy { it.entryId }.values.any { forEntry ->
            forEntry.map { it.unlockedAt }.sorted().zipWithNext().any { (before, after) -> Duration.between(before, after).toDays() >= DUST_OFF_DAYS }
        }
        if (dusty) 1 else 0
    }

    Badge.COLD_TURKEY -> {
        val monthsWithAdditions = input.entries.map { monthOf(it.addedAt) }.toSet()
        if (input.completions.any { it.monthPeriod !in monthsWithAdditions }) 1 else 0
    }

    // the badge is only in reach while nothing is left in the backlog
    Badge.BACKLOG_ZERO -> if (input.entries.any { it.status == LibraryStatus.BACKLOG }) 0 else input.entries.map { it.gameId }.distinct().size

    Badge.TOP_OF_THE_TABLE -> if (input.wonMonths().isNotEmpty()) 1 else 0

    // a win over a friend who scored too, and by less than 25 points
    Badge.PHOTO_FINISH -> {
        val close = input.wonMonths().any { month ->
            val runnerUp = month.friendPoints.values.maxOrNull() ?: 0
            runnerUp > 0 && month.points - runnerUp < PHOTO_FINISH_MARGIN
        }
        if (close) 1 else 0
    }

    // the biggest climb between two months that follow each other
    Badge.COMEBACK -> input.months.sortedBy { it.month }.zipWithNext()
        .filter { (before, after) -> YearMonth.parse(before.month).plusMonths(1) == YearMonth.parse(after.month) }
        .maxOfOrNull { (before, after) -> before.rank - after.rank } ?: 0

    // the longest run of finished months in a row that the user beat the same friend in
    Badge.NEMESIS -> {
        val friends = input.finishedMonths().flatMap { it.friendPoints.keys }.distinct()
        friends.maxOfOrNull { friend ->
            val beaten = input.finishedMonths()
                .filter { month -> month.friendPoints[friend]?.let { month.points > it } == true }
                .map { YearMonth.parse(it.month) }
            longestRun(beaten)
        } ?: 0
    }

    Badge.DAILY_DRIVER -> {
        val days = input.unlocks.map { competitionDate(it.unlockedAt).toEpochDay() }.distinct().sorted()
        var longest = 0
        var run = 0
        days.forEachIndexed { index, day ->
            run = if (index > 0 && day == days[index - 1] + 1) run + 1 else 1
            longest = maxOf(longest, run)
        }
        longest
    }

    Badge.MARATHON_MONTH -> input.months.maxOfOrNull { it.points } ?: 0

    Badge.GENRE_TOURIST -> input.completions.map { it.gameId }.distinct()
        .flatMap { input.games[it]?.genres.orEmpty() }.distinct().size

    Badge.PLATFORM_AGNOSTIC -> input.entries.map { it.platform }.distinct().size
}

private fun BadgeInput.fullCompletions() = completions.filter { it.type == CompletionType.COMPLETIONIST }

// the same achievement unlocked on two platforms of one game is one achievement
private fun BadgeInput.distinctUnlocks() = unlocks.distinctBy { it.gameId to it.achievementId }

private fun UnlockFacts.isRarerThan(percent: Double) = rarityPercent != null && rarityPercent < percent

private fun BadgeInput.finishedMonths() = months.filter { it.month < currentMonth }

// finished months the user scored in and every friend scored less in; with no friends nobody can be beaten
private fun BadgeInput.wonMonths() =
    if (!hasFriends) emptyList() else finishedMonths().filter { month -> month.points > 0 && month.friendPoints.values.all { it < month.points } }

private fun monthOf(at: Instant): String = currentMonthPeriod(at)

// how many months in a row there are in [months], at most
private fun longestRun(months: List<YearMonth>): Int {
    var longest = 0
    var run = 0
    months.sorted().forEachIndexed { index, month ->
        run = if (index > 0 && month == months.sorted()[index - 1].plusMonths(1)) run + 1 else 1
        longest = maxOf(longest, run)
    }
    return longest
}
//------------------------------EOF------------------------------\
