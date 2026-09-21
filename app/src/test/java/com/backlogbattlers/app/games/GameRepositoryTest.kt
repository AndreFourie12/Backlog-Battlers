package com.backlogbattlers.app.games

import com.backlogbattlers.app.data.local.dao.CachedGameDao
import com.backlogbattlers.app.data.local.entity.CachedGameEntity
import com.backlogbattlers.app.data.remote.GameApi
import com.backlogbattlers.app.data.repository.GameRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// an in memory stand in for the cached games table
private class FakeCachedGameDao : CachedGameDao {

    val rows = mutableMapOf<Int, CachedGameEntity>()

    override suspend fun getGame(gameId: Int): CachedGameEntity? = rows[gameId]

    override fun search(query: String): Flow<List<CachedGameEntity>> =
        flowOf(rows.values.filter { it.title.contains(query, ignoreCase = true) })

    override suspend fun upsert(game: CachedGameEntity) {
        rows[game.gameId] = game
    }

    override suspend fun upsertAll(games: List<CachedGameEntity>) {
        games.forEach { rows[it.gameId] = it }
    }
}

class GameRepositoryTest {

    private val dao = FakeCachedGameDao()

    // the popular games are built in, so the API is never asked
    private val repository = GameRepositoryImpl(
        cachedGameDao = dao,
        gameApi = GameApi(
            HttpClient(MockEngine { error("the popular games need no request") }),
            baseUrl = "http://localhost",
        ),
    )

    @Test
    fun `the popular games are The Witcher 3, Cuphead and Elden Ring, in that order`() = runTest {
        val games = repository.getPopularGames()

        assertEquals(listOf("The Witcher 3: Wild Hunt", "Cuphead", "Elden Ring"), games.map { it.title })
        // IGDB's ids, so a search finds these same games and shows them as owned once added
        assertEquals(listOf(1942, 9061, 119133), games.map { it.gameId })
    }

    @Test
    fun `every popular game has landscape art, a platform and a genre for its row`() = runTest {
        repository.getPopularGames().forEach { game ->
            assertTrue("${game.title} has no art", game.artworkUrl?.startsWith("https://") == true)
            assertTrue("${game.title} lists no platform", game.platforms.isNotEmpty())
            assertTrue("${game.title} lists no genre", game.genres.isNotEmpty())
        }
    }

    @Test
    fun `the popular games are cached, so other screens can find them by id`() = runTest {
        repository.getPopularGames()

        assertEquals(3, dao.rows.size)
        assertEquals("Elden Ring", repository.getGame(119133)?.title)
    }

    @Test
    fun `a fuller cached copy of a popular game is left alone`() = runTest {
        dao.rows[9061] = CachedGameEntity(
            gameId = 9061,
            title = "Cuphead",
            coverImageUrl = null,
            platforms = listOf("PC"),
            avgCompletionHours = 4.5f,
            avg100PercentHours = 12f,
            cachedAt = 1L,
        )

        repository.getPopularGames()

        assertEquals(4.5f, dao.rows.getValue(9061).avgCompletionHours)
    }
}
