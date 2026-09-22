package com.backlogbattlers.api.scoring

import kotlin.test.Test
import kotlin.test.assertEquals

class PointsCalculatorTest {

    @Test
    fun `rarer achievements are worth more, using Hades' real Steam figures`() {
        assertEquals(20, achievementPoints(5.9)) // AchLeveledKeepsakes: Rare tier
        assertEquals(5, achievementPoints(81.9)) // AchClearTartarus: Common tier
    }

    @Test
    fun `no rarity data gives a flat fallback so the feature still works`() {
        assertEquals(20, achievementPoints(null)) // the middle tier, Rare
    }

    @Test
    fun `every tier boundary lands in the tier the design calls for`() {
        // >=50% Common(5) | 20-49.9% Uncommon(10) | 5-19.9% Rare(20) | 1-4.9% Very Rare(40) | <1% Ultra Rare(75)
        assertEquals(5, achievementPoints(100.0))
        assertEquals(5, achievementPoints(50.0)) // the boundary itself counts as Common
        assertEquals(10, achievementPoints(49.9))
        assertEquals(10, achievementPoints(20.0))
        assertEquals(20, achievementPoints(19.9))
        assertEquals(20, achievementPoints(5.0))
        assertEquals(40, achievementPoints(4.9))
        assertEquals(40, achievementPoints(1.0))
        assertEquals(75, achievementPoints(0.99))
        assertEquals(75, achievementPoints(0.0))
    }

    @Test
    fun `longer games are worth more, using Hades' real time-to-beat figures`() {
        assertEquals(70, completionTimePoints(70.44444444444444)) // main story
        assertEquals(151, completionTimePoints(150.75)) // 100%
    }

    @Test
    fun `missing or non positive hours give a flat fallback`() {
        assertEquals(20, completionTimePoints(null))
        assertEquals(20, completionTimePoints(0.0))
        assertEquals(20, completionTimePoints(-5.0))
    }
}
