package com.backlogbattlers.app.util

import kotlin.math.roundToInt

// mirrors the backend's api/.../scoring/PointsCalculator.kt so an unlock earns the same amount
// on the phone, live, as it will once the backend awards it for real. Keep the two in step.
//
// Achievement points come straight from the team's rarity tiers, not a formula:
//   >=50% Common 5 | 20-49.9% Uncommon 10 | 5-19.9% Rare 20 | 1-4.9% Very Rare 40 | <1% Ultra Rare 75
private const val COMMON_POINTS = 5
private const val UNCOMMON_POINTS = 10
private const val RARE_POINTS = 20
private const val VERY_RARE_POINTS = 40
private const val ULTRA_RARE_POINTS = 75

// points for one achievement, from its global rarity. Only real Steam rarity data is ever
// scored - unknown rarity earns nothing rather than guessing at a value
fun achievementPoints(rarityPercent: Double?): Int = when {
    rarityPercent == null -> 0
    rarityPercent >= 50.0 -> COMMON_POINTS
    rarityPercent >= 20.0 -> UNCOMMON_POINTS
    rarityPercent >= 5.0 -> RARE_POINTS
    rarityPercent >= 1.0 -> VERY_RARE_POINTS
    else -> ULTRA_RARE_POINTS
}

// whole achievements unlocked out of the whole list, not a points ratio. A null or unknown total
// (achievements never fetched for this game yet) reads as 0%, not a divide-by-zero crash
fun completionPercent(unlockedCount: Int, totalCount: Int?): Int {
    if (totalCount == null || totalCount <= 0) return 0
    return (unlockedCount * 100f / totalCount).roundToInt()
}
//------------------------------End Of File------------------------------\\
