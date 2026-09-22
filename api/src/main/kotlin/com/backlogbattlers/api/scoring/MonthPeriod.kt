package com.backlogbattlers.api.scoring

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// South Africa (SAST) is UTC+2 year-round with no daylight saving, so a fixed offset is used
// instead of a named time zone. The monthly competition resets at midnight in this zone.
private val COMPETITION_ZONE = ZoneOffset.ofHours(2)

private val MONTH_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")

/** The current competition month, e.g. "2026-09". [now] is a parameter so tests can fix the clock. */
fun currentMonthPeriod(now: Instant = Instant.now()): String =
    YearMonth.from(now.atZone(COMPETITION_ZONE)).format(MONTH_PERIOD_FORMAT)

/** The instant [monthPeriod] ends: midnight at the start of the following month. */
fun seasonEndsAt(monthPeriod: String): Instant =
    YearMonth.parse(monthPeriod, MONTH_PERIOD_FORMAT).plusMonths(1).atDay(1).atStartOfDay(COMPETITION_ZONE).toInstant()
