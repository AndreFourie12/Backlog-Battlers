package com.backlogbattlers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    @Test
    fun `an achievement's points follow its rarity tier`() {
        assertEquals(5, achievementPoints(50.0))
        assertEquals(5, achievementPoints(99.9))
        assertEquals(10, achievementPoints(20.0))
        assertEquals(10, achievementPoints(49.9))
        assertEquals(20, achievementPoints(5.0))
        assertEquals(20, achievementPoints(19.9))
        assertEquals(40, achievementPoints(1.0))
        assertEquals(40, achievementPoints(4.9))
        assertEquals(75, achievementPoints(0.8))
        assertEquals(75, achievementPoints(0.0))
    }

    @Test
    fun `unknown rarity earns nothing rather than a guess`() {
        assertEquals(0, achievementPoints(null))
    }

    @Test
    fun `completion percent rounds to the nearest whole number`() {
        assertEquals(0, completionPercent(0, 42))
        assertEquals(86, completionPercent(36, 42))
        assertEquals(100, completionPercent(42, 42))
    }

    @Test
    fun `an unknown or zero total reads as 0 percent, not a crash`() {
        assertEquals(0, completionPercent(0, null))
        assertEquals(0, completionPercent(0, 0))
    }
}
