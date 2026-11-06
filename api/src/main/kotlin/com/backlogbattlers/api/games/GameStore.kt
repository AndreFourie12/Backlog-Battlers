package com.backlogbattlers.api.games

import com.backlogbattlers.api.db.GameGenres
import com.backlogbattlers.api.db.Games
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

// the length of the genre column
private const val MAX_GENRE_LENGTH = 100

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
        // a copy of the game that came without genres must not wipe the ones already stored
        val genres = game.genres.map { it.trim().take(MAX_GENRE_LENGTH) }.filter { it.isNotEmpty() }.distinct()
        if (genres.isNotEmpty()) {
            GameGenres.deleteWhere { GameGenres.gameId eq game.gameId }
            genres.forEach { name ->
                GameGenres.insert {
                    it[gameId] = game.gameId
                    it[genre] = name
                }
            }
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
