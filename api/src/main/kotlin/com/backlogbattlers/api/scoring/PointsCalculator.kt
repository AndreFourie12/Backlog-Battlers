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

// Used when Steam has no rarity data for an achievement (console-only games, mainly), so the
// feature still works even though rarity weighting is unavailable for that title. Set to the
// middle tier ("Rare"), a middle-of-the-road guess pending a team decision on what fits best.
private const val FLAT_ACHIEVEMENT_POINTS = RARE_POINTS

private const val POINTS_PER_HOUR = 1.0

// Used when IGDB has no (or an implausible) time-to-beat figure for a game.
private const val FLAT_COMPLETION_TIME_POINTS = 20

/**
 * Points for unlocking one achievement, from the team's five rarity tiers (see the constants
 * above). Null (no Steam data for this game) gives a flat fallback instead of zero, so
 * completing a console-only game still earns something.
 */
fun achievementPoints(rarityPercent: Double?): Int = when {
    rarityPercent == null -> FLAT_ACHIEVEMENT_POINTS
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
