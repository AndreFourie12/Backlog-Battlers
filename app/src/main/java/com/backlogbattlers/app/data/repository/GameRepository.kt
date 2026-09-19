package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.local.dao.CachedGameDao
import com.backlogbattlers.app.data.local.entity.CachedGameEntity
import com.backlogbattlers.app.domain.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

//------------------------------
// interface providing game search and retrieval operations
interface GameRepository {

    // searches cached games matching query string as a Flow
    fun search(query: String): Flow<List<Game>>

    // fetches a cached game by game id
    suspend fun getGame(gameId: Int): Game?
}



//------------------------------
// implementation of GameRepository using local CachedGameDao
// room only for now, until RestAPI and RAWG integration where cache is checked first
class GameRepositoryImpl(
    private val cachedGameDao: CachedGameDao,
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
    // caches a domain Game into Room database with current timestamp
    suspend fun cacheGame(game: Game) {
        val entity = CachedGameEntity(
            gameId = game.gameId,
            title = game.title,
            coverImageUrl = game.coverImageUrl,
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
            platforms = platforms,
            avgCompletionHours = avgCompletionHours,
            avg100PercentHours = avg100PercentHours,
            cachedAt = cachedAt,
        )
    }
}
//------------------------------EOF------------------------------\\