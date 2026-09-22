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
    fun `no rarity data earns nothing, rather than a guessed value`() {
        assertEquals(0, achievementPoints(null))
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

    @Test
    fun `finishing further ahead of the average earns a bigger speed bonus`() {
        // A 100-hour average game: 5% faster, 15% faster, 30% faster, 55% faster, 80% faster
        assertEquals(5, speedBonusPoints(actualHours = 95.0, averageHours = 100.0))
        assertEquals(10, speedBonusPoints(actualHours = 85.0, averageHours = 100.0))
        assertEquals(20, speedBonusPoints(actualHours = 70.0, averageHours = 100.0))
        assertEquals(40, speedBonusPoints(actualHours = 45.0, averageHours = 100.0))
        assertEquals(75, speedBonusPoints(actualHours = 20.0, averageHours = 100.0))
    }

    @Test
    fun `exactly average or slower earns the baseline, never a penalty`() {
        assertEquals(5, speedBonusPoints(actualHours = 100.0, averageHours = 100.0)) // same as average
        assertEquals(5, speedBonusPoints(actualHours = 150.0, averageHours = 100.0)) // slower than average
    }

    @Test
    fun `no bonus without real data on both sides`() {
        assertEquals(0, speedBonusPoints(actualHours = 10.0, averageHours = null))
        assertEquals(0, speedBonusPoints(actualHours = 10.0, averageHours = 0.0))
        // 0 logged hours means the entry was never really played, not a free "instant" speedrun
        assertEquals(0, speedBonusPoints(actualHours = 0.0, averageHours = 100.0))
    }
}
