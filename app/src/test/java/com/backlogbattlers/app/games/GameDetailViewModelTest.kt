package com.backlogbattlers.app.games

import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.search.FakeGameRepository
import com.backlogbattlers.app.search.FakeLibraryRepository
import com.backlogbattlers.app.search.achievement
import com.backlogbattlers.app.search.game
import com.backlogbattlers.app.viewmodels.GameDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var games: FakeGameRepository
    private lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        games = FakeGameRepository()
        library = FakeLibraryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GameDetailViewModel(games, library)

    @Test
    fun `a freshly loaded game starts fully locked`() = runTest {
        games.cachedGames = mapOf(1 to game(1, "Elden Ring"))
        games.achievementsByGame = mapOf(
            1 to listOf(
                achievement("common", rarityPercent = 60.0), // 5 points
                achievement("rare", rarityPercent = 10.0), // 20 points
            ),
        )
        val viewModel = viewModel()

        viewModel.load(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Elden Ring", state.game?.title)
        assertEquals(0, state.unlockedCount)
        assertEquals(2, state.totalCount)
        assertEquals(0, state.earnedPoints)
        assertEquals(25, state.totalPoints)
        assertEquals(0, state.completionPercent)
        assertTrue(state.achievements.all { !it.unlocked })
    }

    @Test
    fun `achievements are sorted from least to most rare`() = runTest {
        games.cachedGames = mapOf(1 to game(1, "Elden Ring"))
        games.achievementsByGame = mapOf(
            1 to listOf(
                achievement("rare", rarityPercent = 5.0),
                achievement("common", rarityPercent = 80.0),
                achievement("middle", rarityPercent = 40.0),
            ),
        )
        val viewModel = viewModel()

        viewModel.load(1)
        advanceUntilIdle()

        val achievements = viewModel.uiState.value.achievements
        assertEquals("common", achievements[0].achievement.achievementId)
        assertEquals("middle", achievements[1].achievement.achievementId)
        assertEquals("rare", achievements[2].achievement.achievementId)
    }

    @Test
    fun `unlocking an achievement earns its points and moves completion`() = runTest {
        games.cachedGames = mapOf(1 to game(1, "Elden Ring", platforms = listOf("PLAYSTATION")))
        games.achievementsByGame = mapOf(
            1 to listOf(
                achievement("common", rarityPercent = 60.0), // 5 points
                achievement("ultra", rarityPercent = 0.5), // 75 points
            ),
        )
        val viewModel = viewModel()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.toggleAchievement("ultra")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.unlockedCount)
        assertEquals(75, state.earnedPoints)
        assertEquals(80, state.totalPoints)
        assertEquals(50, state.completionPercent)
        assertTrue(state.achievements.first { it.achievement.achievementId == "ultra" }.unlocked)

        // tracking an achievement implies tracking the game, on the platform it is filed under
        assertEquals(1, library.observeLibrary().first().size)
        assertEquals(Platform.PLAYSTATION, library.observeLibrary().first().single().platform)
    }

    @Test
    fun `tapping an unlocked achievement locks it back`() = runTest {
        games.cachedGames = mapOf(1 to game(1, "Elden Ring"))
        games.achievementsByGame = mapOf(1 to listOf(achievement("common", rarityPercent = 60.0)))
        val viewModel = viewModel()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.toggleAchievement("common")
        advanceUntilIdle()
        viewModel.toggleAchievement("common")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.unlockedCount)
        assertEquals(0, state.earnedPoints)
        assertFalse(state.achievements.single().unlocked)
    }

    @Test
    fun `a failed achievement fetch can be retried`() = runTest {
        games.cachedGames = mapOf(1 to game(1, "Elden Ring"))
        games.achievementsByGame = mapOf(1 to listOf(achievement("common")))
        games.failing = true
        val viewModel = viewModel()

        viewModel.load(1)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.achievementsFailed)
        assertFalse(state.achievementsLoading)
        assertTrue(state.achievements.isEmpty())
        // the header does not need achievements to have loaded
        assertEquals("Elden Ring", state.game?.title)

        games.failing = false
        viewModel.retryAchievements()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertFalse(state.achievementsFailed)
        assertEquals(1, state.totalCount)
    }

    @Test
    fun `the header refreshes with the real time-to-beat, which search and popular never carry`() = runTest {
        games.cachedGames = mapOf(1 to game(1, "Elden Ring", avgCompletionHours = null))
        games.refreshedGames = mapOf(1 to game(1, "Elden Ring", avgCompletionHours = 119.2f))
        val viewModel = viewModel()

        viewModel.load(1)
        advanceUntilIdle()

        assertEquals(119.2f, viewModel.uiState.value.game?.avgCompletionHours)
    }
}
