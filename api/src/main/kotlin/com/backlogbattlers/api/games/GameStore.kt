package com.backlogbattlers.api.games

import com.backlogbattlers.api.db.Games
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

/** Keeps our own copy of the game so leaderboards and scoring can use it without calling IGDB. */
fun saveGame(game: GameDto) {
    transaction {
        // upsert = insert, or update if this game id is already stored
        Games.upsert {
            it[id] = game.gameId
            it[title] = game.title
            it[avgCompletionHours] = game.avgCompletionHours
            it[avg100PercentHours] = game.avg100PercentHours
        }
    }
}

/**
 * Makes sure [gameId] is in our Games table, fetching it from IGDB the first time it is needed.
 * Returns false when IGDB does not know the game either.
 */
suspend fun ensureGameSaved(igdb: IgdbClient, gameId: Int): Boolean {
    val alreadyStored = transaction { Games.selectAll().where { Games.id eq gameId }.any() }
    if (alreadyStored) return true

    val game = igdb.game(gameId) ?: return false
    saveGame(game.toDto(igdb.timeToBeat(gameId)))
    return true
}
