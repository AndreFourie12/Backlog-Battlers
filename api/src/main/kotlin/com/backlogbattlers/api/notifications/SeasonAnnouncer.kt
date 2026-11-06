package com.backlogbattlers.api.notifications

import com.backlogbattlers.api.db.Devices
import com.backlogbattlers.api.db.MonthlyLeaderboardEntries
import com.backlogbattlers.api.db.SeasonAnnouncements
import com.backlogbattlers.api.scoring.competitionRanks
import com.backlogbattlers.api.scoring.currentMonthPeriod
import com.backlogbattlers.api.scoring.previousMonthPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Tells everyone with the app on a phone that a new season has begun, once, when the competition month
 * changes: the leaderboard starts again from zero, and the push says how they finished last month.
 * Each person's Settings switch is checked by the [Notifier], like for every push.
 *
 * Which months were announced is kept in [SeasonAnnouncements], so a restart or a second instance never
 * sends it twice. The first time it ever runs it only records the month it finds, because a season that
 * began weeks ago is not news.
 */
class SeasonAnnouncer(
    private val notifier: Notifier,
    private val clock: () -> Instant = Instant::now,
) {

    /** Announces the current month if it has not been announced. Returns how many people it told (before their switches). */
    fun announceIfNew(): Int {
        val current = currentMonthPeriod(clock())
        val shouldTell = transaction {
            if (SeasonAnnouncements.selectAll().where { SeasonAnnouncements.monthPeriod eq current }.any()) return@transaction false
            val firstRun = SeasonAnnouncements.selectAll().empty()
            // recorded before anyone is told, so the same month is never announced twice
            SeasonAnnouncements.insert { it[monthPeriod] = current }
            !firstRun
        }
        if (!shouldTell) return 0

        val (recipients, standings) = transaction {
            val phones = Devices.selectAll().map { it[Devices.userId] }.distinct()
            val rows = MonthlyLeaderboardEntries.selectAll()
                .where { MonthlyLeaderboardEntries.monthPeriod eq previousMonthPeriod(current) }
                .orderBy(MonthlyLeaderboardEntries.monthlyPoints, SortOrder.DESC)
                .toList()
            val ranks = competitionRanks(rows.map { it[MonthlyLeaderboardEntries.monthlyPoints] })
            phones to rows.mapIndexed { index, row ->
                row[MonthlyLeaderboardEntries.userId] to (ranks[index] to row[MonthlyLeaderboardEntries.monthlyPoints])
            }.toMap()
        }
        recipients.forEach { user ->
            val standing = standings[user]
            val points = standing?.second ?: 0
            notifier.seasonStarted(user, current, lastRank = standing?.first?.takeIf { points > 0 }, lastPoints = points)
        }
        log.info("Season {} announced to {} people", current, recipients.size)
        return recipients.size
    }

    /** Checks every [every] for the month to have changed, for as long as [scope] lives. */
    fun startIn(scope: CoroutineScope, every: Duration = Duration.ofMinutes(10)) {
        scope.launch {
            while (isActive) {
                try {
                    announceIfNew()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("The season announcement failed, trying again next time", e)
                }
                delay(every.toMillis())
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SeasonAnnouncer::class.java)
    }
}
//------------------------------EOF------------------------------\\
