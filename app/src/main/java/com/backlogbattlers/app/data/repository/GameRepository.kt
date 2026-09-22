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

    suspend fun searchOnline(query: String, limit: Int): List<Game>

    suspend fun browse(category: String, limit: Int): List<Game>

    // fetches a cached game by game id
    suspend fun getGame(gameId: Int): Game?

    // fetches the hand picked games for new players from the API and caches each one
    suspend fun getStarterGames(): List<Recommendation>

    // the hand picked games offered under an empty library, cached so other screens can find them by id
    suspend fun getPopularGames(): List<Game>
}



//------------------------------
// implementation of GameRepository using local CachedGameDao
// room for search and lookup until RestAPI and RAWG integration where cache is checked first,
// the starter games come from the API through GameApi
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

    override suspend fun searchOnline(query: String, limit: Int): List<Game> {
        return cacheAll(gameApi.searchGames(query, limit))
    }

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
    // the popular games are built in, so this needs no request. Each one is cached so other screens can find it by id,
    // unless a copy is already cached, which can hold more than the built in one does (time to beat)
    override suspend fun getPopularGames(): List<Game> {
        val cachedAt = System.currentTimeMillis()
        val games = POPULAR_GAMES.map { it.copy(cachedAt = cachedAt) }
        games.forEach { game ->
            if (cachedGameDao.getGame(game.gameId) == null) cacheGame(game)
        }
        return games
    }

    //------------------------------
    // caches a domain Game into Room database with current timestamp
    suspend fun cacheGame(game: Game) {
        cachedGameDao.upsert(game.toEntity())
    }

    private suspend fun cacheAll(dtos: List<GameDto>): List<Game> {
        val cachedAt = System.currentTimeMillis()
        val games = dtos.map { it.toDomain(cachedAt) }
        cachedGameDao.upsertAll(games.map { it.toEntity() })
        return games
    }

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
//------------------------------EOF------------------------------\\