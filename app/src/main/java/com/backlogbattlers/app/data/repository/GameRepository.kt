package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.local.dao.CachedGameDao
import com.backlogbattlers.app.data.local.entity.CachedGameEntity
import com.backlogbattlers.app.data.remote.GameApi
import com.backlogbattlers.app.data.remote.GameDto
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.Recommendation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

//------------------------------
// interface providing game search and retrieval operations
interface GameRepository {

    // searches cached games matching query string as a Flow
    fun search(query: String): Flow<List<Game>>

    // searches the whole catalogue through the API, so any game IGDB knows can be found
    suspend fun searchOnline(query: String, limit: Int): List<Game>

    // the games behind one of the browse chips, e.g. "rpg", most talked about first
    suspend fun browse(category: String, limit: Int): List<Game>

    // fetches a cached game by game id
    suspend fun getGame(gameId: Int): Game?

    // fetches the hand picked games for new players from the API and caches each one
    suspend fun getStarterGames(): List<Recommendation>
}



//------------------------------
// implementation of GameRepository using local CachedGameDao
// room holds what has already been seen, so a game opened from a search result is still there
// without another request, while searching itself always goes to the API through GameApi
class GameRepositoryImpl(
    private val cachedGameDao: CachedGameDao,
    private val gameApi: GameApi,
) : GameRepository {

    //------------------------------
    // searches cached games by delegating to CachedGameDao and mapping to domain Game list
    override fun search(query: String): Flow<List<Game>> {
        return cachedGameDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    //------------------------------
    // asks the API to search IGDB, then caches every result so opening one needs no second request
    override suspend fun searchOnline(query: String, limit: Int): List<Game> {
        return cacheAll(gameApi.searchGames(query, limit))
    }

    //------------------------------
    // asks the API for one browse category, then caches every result
    override suspend fun browse(category: String, limit: Int): List<Game> {
        return cacheAll(gameApi.browseGames(category, limit))
    }

    //------------------------------
    // fetches game by delegating to CachedGameDao and mapping to domain Game
    override suspend fun getGame(gameId: Int): Game? {
        return cachedGameDao.getGame(gameId)?.toDomain()
    }

    //------------------------------
    // asks the API for the starter games, then caches every game so other screens can show it without another request
    override suspend fun getStarterGames(): List<Recommendation> {
        val cachedAt = System.currentTimeMillis()
        val recommendations = gameApi.starterGames().map { dto ->
            Recommendation(game = dto.game.toDomain(cachedAt), reason = dto.reason)
        }
        recommendations.forEach { cacheGame(it.game) }
        return recommendations
    }

    //------------------------------
    // caches a domain Game into Room database with current timestamp
    suspend fun cacheGame(game: Game) {
        cachedGameDao.upsert(game.toEntity())
    }

    //------------------------------
    // maps a page of API games to domain games and stores the whole page in one write
    private suspend fun cacheAll(dtos: List<GameDto>): List<Game> {
        val cachedAt = System.currentTimeMillis()
        val games = dtos.map { it.toDomain(cachedAt) }
        cachedGameDao.upsertAll(games.map { it.toEntity() })
        return games
    }

    //------------------------------
    // converts a domain Game to the row Room stores
    private fun Game.toEntity(): CachedGameEntity {
        return CachedGameEntity(
            gameId = gameId,
            title = title,
            coverImageUrl = coverImageUrl,
            artworkUrl = artworkUrl,
            platforms = platforms,
            genres = genres,
            avgCompletionHours = avgCompletionHours,
            avg100PercentHours = avg100PercentHours,
            cachedAt = System.currentTimeMillis(),
        )
    }

    //------------------------------
    // converts CachedGameEntity to domain Game
    private fun CachedGameEntity.toDomain(): Game {
        return Game(
            gameId = gameId,
            title = title,
            coverImageUrl = coverImageUrl,
            artworkUrl = artworkUrl,
            platforms = platforms,
            genres = genres,
            avgCompletionHours = avgCompletionHours,
            avg100PercentHours = avg100PercentHours,
            cachedAt = cachedAt,
        )
    }

    //------------------------------
    // converts the API's GameDto to domain Game, the API sends hours as doubles
    private fun GameDto.toDomain(cachedAt: Long): Game {
        return Game(
            gameId = gameId,
            title = title,
            coverImageUrl = coverImageUrl,
            artworkUrl = artworkUrl,
            platforms = platforms,
            genres = genres,
            avgCompletionHours = avgCompletionHours?.toFloat(),
            avg100PercentHours = avg100PercentHours?.toFloat(),
            cachedAt = cachedAt,
        )
    }
}
//------------------------------EOF------------------------------\
