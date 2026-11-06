package com.backlogbattlers.api.profile

/**
 * Levels come from XP, and XP is the points a person has earned in total, across every month
 * (the monthly leaderboard resets, XP never does). Level 1 starts at 0 XP and each level costs 100
 * XP more than the one before it:
 *
 *     XP needed to reach level n  =  50 * n * (n - 1)      level 2 = 100, 3 = 300, 4 = 600, 5 = 1,000 ...
 *
 * The same formula is written up in the README, and the app only displays what this works out.
 */
fun xpToReach(level: Int): Long = 50L * level * (level - 1)

/** Where a person stands: their [level], the XP they have into it, and the XP the whole level takes. */
data class LevelProgress(val level: Int, val xpIntoLevel: Int, val xpForNextLevel: Int)

/** The level that [xp] total XP reaches. Negative XP counts as none. */
fun levelFor(xp: Int): LevelProgress {
    val total = xp.coerceAtLeast(0).toLong()
    var level = 1
    while (xpToReach(level + 1) <= total) level++
    return LevelProgress(
        level = level,
        xpIntoLevel = (total - xpToReach(level)).toInt(),
        xpForNextLevel = (xpToReach(level + 1) - xpToReach(level)).toInt(),
    )
}
//------------------------------EOF------------------------------\
