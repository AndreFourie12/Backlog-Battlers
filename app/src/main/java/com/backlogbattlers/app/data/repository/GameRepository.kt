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

    // fetches a cached game by game id
    suspend fun getGame(gameId: Int): Game?

    // fetches the hand picked games for new players from the API and caches each one
    suspend fun getStarterGames(): List<Recommendation>
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
        val entity = CachedGameEntity(
            gameId = game.gameId,
            title = game.title,
            coverImageUrl = game.coverImageUrl,
            artworkUrl = game.artworkUrl,
            platforms = game.platforms,
            avgCompletionHours = game.avgCompletionHours,
            avg100PercentHours = game.avg100PercentHours,
            cachedAt = System.currentTimeMillis(),
        )
        cachedGameDao.upsert(entity)
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
            avgCompletionHours = avgCompletionHours?.toFloat(),
            avg100PercentHours = avg100PercentHours?.toFloat(),
            cachedAt = cachedAt,
        )
    }
}
//------------------------------EOF------------------------------\\