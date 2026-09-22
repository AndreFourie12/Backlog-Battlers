package com.backlogbattlers.api.scoring

import kotlin.math.roundToInt

// A rare achievement is worth more than a common one, and a long game is worth more than a
// short one. See the Part 1 design document, "Weighted Point Scoring".
//
// Achievement points come straight from the team's rarity tiers, not a formula:
//   >=50% Common 5 | 20-49.9% Uncommon 10 | 5-19.9% Rare 20 | 1-4.9% Very Rare 40 | <1% Ultra Rare 75
private const val COMMON_POINTS = 5
private const val UNCOMMON_POINTS = 10
private const val RARE_POINTS = 20
private const val VERY_RARE_POINTS = 40
private const val ULTRA_RARE_POINTS = 75

private const val POINTS_PER_HOUR = 1.0

// Used when IGDB has no (or an implausible) time-to-beat figure for a game.
private const val FLAT_COMPLETION_TIME_POINTS = 20

// Same five-value ladder as the rarity tiers above, reused so the two bonuses feel consistent,
// but read the other way round: a BIGGER percentage (faster than average) earns MORE here,
// where a SMALLER percentage (rarer) earned more there.
private const val SPEED_BASELINE_POINTS = 5
private const val SPEED_SLIGHT_POINTS = 10
private const val SPEED_FAST_POINTS = 20
private const val SPEED_VERY_FAST_POINTS = 40
private const val SPEED_ULTRA_FAST_POINTS = 75

/**
 * Points for unlocking one achievement, from the team's five rarity tiers (see the constants
 * above). Only achievements with real Steam rarity data are ever scored - null earns nothing,
 * rather than guessing at a value. In practice this only ever stores what Steam actually reports
 * (see [com.backlogbattlers.api.games.achievementsFor]), so null should not occur today; it is
 * kept as a safe default in case a future, rarity-less data source is ever added.
 */
fun achievementPoints(rarityPercent: Double?): Int = when {
    rarityPercent == null -> 0
    rarityPercent >= 50.0 -> COMMON_POINTS
    rarityPercent >= 20.0 -> UNCOMMON_POINTS
    rarityPercent >= 5.0 -> RARE_POINTS
    rarityPercent >= 1.0 -> VERY_RARE_POINTS
    else -> ULTRA_RARE_POINTS
}

/**
 * Points for reaching a completion milestone, from the average hours IGDB reports for it
 * (see [com.backlogbattlers.api.db.Games]). A 100-hour game is worth more than a 5-hour one.
 * Null or a non-positive value (no usable IGDB data) gives a flat fallback instead of zero.
 */
fun completionTimePoints(averageHours: Double?): Int {
    if (averageHours == null || averageHours <= 0.0) return FLAT_COMPLETION_TIME_POINTS
    return (averageHours * POINTS_PER_HOUR).roundToInt()
}

/**
 * A bonus for finishing faster than the average player, e.g. beating a game 25% or more faster
 * than average earns more than beating it 10% or more faster. [actualHours] is what the player
 * logged for this entry; [averageHours] is IGDB's average for the same milestone (the same value
 * [completionTimePoints] uses). Needs real data on both sides: no IGDB average, or no hours ever
 * logged for this entry, earns no bonus rather than rewarding an entry nobody actually played.
 */
fun speedBonusPoints(actualHours: Double, averageHours: Double?): Int {
    if (averageHours == null || averageHours <= 0.0 || actualHours <= 0.0) return 0

    val percentFaster = (averageHours - actualHours) / averageHours * 100.0
    return when {
        percentFaster >= 75.0 -> SPEED_ULTRA_FAST_POINTS
        percentFaster >= 50.0 -> SPEED_VERY_FAST_POINTS
        percentFaster >= 25.0 -> SPEED_FAST_POINTS
        percentFaster >= 10.0 -> SPEED_SLIGHT_POINTS
        else -> SPEED_BASELINE_POINTS // average, slower than average, or a small speed edge
    }
}
