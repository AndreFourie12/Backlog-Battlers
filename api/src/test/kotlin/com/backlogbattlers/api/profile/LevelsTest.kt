package com.backlogbattlers.api.profile

import kotlin.test.Test
import kotlin.test.assertEquals

/** Level 1 starts at 0 XP, and each level costs 100 XP more than the last. */
class LevelsTest {

    @Test
    fun `the xp to reach each level follows 50 n times n minus 1`() {
        assertEquals(listOf(0L, 100L, 300L, 600L, 1000L, 1500L), (1..6).map { xpToReach(it) })
    }

    @Test
    fun `no xp is level 1 with 100 to go`() {
        assertEquals(LevelProgress(level = 1, xpIntoLevel = 0, xpForNextLevel = 100), levelFor(0))
    }

    @Test
    fun `the last xp of a level is still that level, and the next one starts the next`() {
        assertEquals(LevelProgress(1, 99, 100), levelFor(99))
        assertEquals(LevelProgress(2, 0, 200), levelFor(100))
        assertEquals(LevelProgress(2, 199, 200), levelFor(299))
        assertEquals(LevelProgress(3, 0, 300), levelFor(300))
    }

    @Test
    fun `xp part of the way through a level is shown as progress into it`() {
        assertEquals(LevelProgress(level = 4, xpIntoLevel = 250, xpForNextLevel = 400), levelFor(850))
    }

    @Test
    fun `negative xp is no xp`() {
        assertEquals(levelFor(0), levelFor(-50))
    }

    @Test
    fun `a huge total does not overflow`() {
        val level = levelFor(Int.MAX_VALUE)
        assertEquals(true, level.level > 6000)
        assertEquals(true, level.xpIntoLevel in 0 until level.xpForNextLevel)
    }
}
//------------------------------EOF------------------------------\\
