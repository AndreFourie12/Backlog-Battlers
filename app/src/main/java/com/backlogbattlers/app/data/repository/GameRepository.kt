package com.backlogbattlers.app.data.repository

import com.backlogbattlers.app.data.local.dao.CachedGameDao
import com.backlogbattlers.app.data.local.entity.CachedGameEntity
import com.backlogbattlers.app.data.remote.AchievementDto
import com.backlogbattlers.app.data.remote.GameApi
import com.backlogbattlers.app.data.remote.GameDto
import com.backlogbattlers.app.domain.model.Achievement
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

    // fetches one game fresh from the API, with its real IGDB time-to-beat, and re-caches it.
    // Existing cached artwork is kept: this endpoint does not carry Steam art the way search does
    suspend fun refreshGame(gameId: Int): Game

    // fetches the hand picked games for new players from the API and caches each one
    suspend fun getStarterGames(): List<Recommendation>

    // the hand picked games offered under an empty library, cached so other screens can find them by id
    suspend fun getPopularGames(): List<Game>

    // fetches a game's achievements with their global rarity, rarest first, straight from the API
    suspend fun getAchievements(gameId: Int): List<Achievement>
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
    // refreshes one game from the API for its real time-to-beat, which search and browse skip
    override suspend fun refreshGame(gameId: Int): Game {
        return cacheGame(gameApi.getGame(gameId).toDomain(System.currentTimeMillis()))
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
    // fetches a game's achievements straight from the API; the achievements themselves are not
    // cached, but the game's total count is, so the library grid can show a real completion percent
    override suspend fun getAchievements(gameId: Int): List<Achievement> {
        val achievements = gameApi.achievementsFor(gameId).map { it.toDomain() }
        val cached = cachedGameDao.getGame(gameId)
        if (cached != null && cached.totalAchievements != achievements.size) {
            cachedGameDao.upsert(cached.copy(totalAchievements = achievements.size))
        }
        return achievements
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
    // caches a domain Game into Room, keeping its artwork and known achievement total when the
    // incoming copy does not carry one (search, starter and the single-game endpoint each leave
    // out fields the others have), rather than letting a partial response clear good cached data.
    // Returns the Game as it was actually stored
    suspend fun cacheGame(game: Game): Game {
        val existing = cachedGameDao.getGame(game.gameId)
        val merged = game.copy(
            artworkUrl = game.artworkUrl ?: existing?.artworkUrl,
            totalAchievements = game.totalAchievements ?: existing?.totalAchievements,
        )
        cachedGameDao.upsert(merged.toEntity())
        return merged
    }

    private suspend fun cacheAll(dtos: List<GameDto>): List<Game> {
        val cachedAt = System.currentTimeMillis()
        return dtos.map { cacheGame(it.toDomain(cachedAt)) }
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
            totalAchievements = totalAchievements,
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
            totalAchievements = totalAchievements,
        )
    }

    //------------------------------
    // converts the API's AchievementDto to domain Achievement
    private fun AchievementDto.toDomain(): Achievement {
        return Achievement(
            achievementId = achievementId,
            name = name,
            description = description,
            rarityPercent = rarityPercent,
            iconUrl = iconUrl,
            iconGrayUrl = iconGrayUrl,
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