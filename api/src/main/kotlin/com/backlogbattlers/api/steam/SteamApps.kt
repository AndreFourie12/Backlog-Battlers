package com.backlogbattlers.api.steam

import com.backlogbattlers.api.db.SteamApps
import com.backlogbattlers.api.games.IgdbClient
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Duration
import java.time.Instant

/** How long "IGDB has no game for this Steam app" is trusted before IGDB is asked again. */
val UNMATCHED_APP_RECHECK: Duration = Duration.ofDays(30)

/**
 * Which IGDB game each of [appIds] is, for the apps IGDB has a game for. Apps already in the
 * SteamApps cache cost nothing; IGDB is asked only about the rest, all in one batched lookup,
 * and every answer (including "no game") is cached for next time, for every user.
 */
suspend fun resolveSteamApps(appIds: Collection<Int>, igdb: IgdbClient, now: Instant = Instant.now()): Map<Int, Int> {
    val wanted = appIds.distinct()
    if (wanted.isEmpty()) return emptyMap()

    val cached = transaction {
        SteamApps.selectAll().where { SteamApps.appId inList wanted }.associate {
            it[SteamApps.appId] to (it[SteamApps.gameId] to it[SteamApps.checkedAt])
        }
    }
    val recheckBefore = now.minus(UNMATCHED_APP_RECHECK)
    val matched = cached.mapNotNull { (appId, row) -> row.first?.let { appId to it } }.toMap()
    // an app is asked about when it was never asked about, or when its "no game" answer is old
    val toAsk = wanted.filter { appId ->
        val row = cached[appId] ?: return@filter true
        row.first == null && row.second.isBefore(recheckBefore)
    }
    if (toAsk.isEmpty()) return matched

    val found = igdb.gameIdsForSteamApps(toAsk)
    transaction {
        toAsk.forEach { appId ->
            SteamApps.upsert {
                it[SteamApps.appId] = appId
                it[gameId] = found[appId]
                it[checkedAt] = now
            }
        }
    }
    return matched + found
}

/**
 * The Steam app of [gameId], from the SteamApps cache when it is there (no IGDB call), otherwise
 * from IGDB, remembered for next time. Null when the game is not on Steam.
 */
suspend fun steamAppIdFor(gameId: Int, igdb: IgdbClient): Int? {
    val cached = transaction {
        SteamApps.selectAll().where { SteamApps.gameId eq gameId }
            .orderBy(SteamApps.appId, SortOrder.ASC)
            .limit(1)
            .singleOrNull()?.get(SteamApps.appId)
    }
    if (cached != null) return cached

    val appId = igdb.steamAppId(gameId) ?: return null
    // insertIgnore: an app already cached under another IGDB entry (an edition) keeps that match
    transaction {
        SteamApps.insertIgnore {
            it[SteamApps.appId] = appId
            it[SteamApps.gameId] = gameId
        }
    }
    return appId
}
//------------------------------EOF------------------------------\\
