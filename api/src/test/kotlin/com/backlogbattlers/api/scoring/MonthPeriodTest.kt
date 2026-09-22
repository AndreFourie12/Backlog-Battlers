package com.backlogbattlers.api.scoring

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthPeriodTest {

    @Test
    fun `the month period uses South African time, not UTC`() {
        // 22:30 UTC on 31 Aug is already 00:30 on 1 Sep in SAST (UTC+2): a naive UTC
        // implementation would wrongly call this August.
        assertEquals("2026-09", currentMonthPeriod(Instant.parse("2026-08-31T22:30:00Z")))
        assertEquals("2026-09", currentMonthPeriod(Instant.parse("2026-09-21T10:00:00Z")))
    }

    @Test
    fun `a season ends at midnight SAST on the first of the following month`() {
        assertEquals(Instant.parse("2026-09-30T22:00:00Z"), seasonEndsAt("2026-09"))
    }

    @Test
    fun `a season ending in December rolls over to the next year`() {
        assertEquals(Instant.parse("2026-12-31T22:00:00Z"), seasonEndsAt("2026-12"))
    }
}
