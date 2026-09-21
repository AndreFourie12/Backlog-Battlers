package com.backlogbattlers.app.games

import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.search.FakeGameRepository
import com.backlogbattlers.app.search.FakeLibraryRepository
import com.backlogbattlers.app.search.game
import com.backlogbattlers.app.viewmodels.GameListState
import com.backlogbattlers.app.viewmodels.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

// the search tests' fake game repository, with popular games that can be made to fail
private class FlakyGameRepository(
    val fake: FakeGameRepository = FakeGameRepository(),
) : GameRepository by fake {

    var failing = false

    override suspend fun getPopularGames(): List<Game> {
        if (failing) throw IOException("cache unavailable")
        return fake.getPopularGames()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var games: FlakyGameRepository
    private lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        games = FlakyGameRepository()
        library = FakeLibraryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GameViewModel(games, library)

    @Test
    fun `an empty library counts no games and offers the popular ones`() = runTest {
        games.fake.popularGames = listOf(game(1, "Hades"), game(2, "Celeste"))
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.libraryCount)
        assertEquals(GameListState.Loading, viewModel.uiState.value.popular)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.libraryCount)
        assertEquals(emptySet<Int>(), state.ownedGameIds)
        assertEquals(listOf("Hades", "Celeste"), (state.popular as GameListState.Loaded).games.map { it.title })
    }

    @Test
    fun `adding a game puts it in the library and marks it owned`() = runTest {
        val hades = game(1, "Hades")
        games.fake.popularGames = listOf(hades)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addToLibrary(hades)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.libraryCount)
        assertEquals(setOf(1), viewModel.uiState.value.ownedGameIds)
    }

    @Test
    fun `adding a game twice changes nothing, and says so once`() = runTest {
        val hades = game(1, "Hades")
        val viewModel = viewModel()
        advanceUntilIdle()

        val confirmations = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.addedGames.toList(confirmations)
        }

        viewModel.addToLibrary(hades)
        advanceUntilIdle()
        viewModel.addToLibrary(hades)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.libraryCount)
        assertEquals(listOf("Hades"), confirmations)
    }

    @Test
    fun `a game is filed under its main platform, not the first one the API lists`() = runTest {
        val sekiro = game(1, "Sekiro", platforms = listOf("OTHER", "PLAYSTATION", "PC", "XBOX"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addToLibrary(sekiro)
        advanceUntilIdle()

        assertEquals(Platform.PC, library.observeLibrary().first().single().platform)
    }

    @Test
    fun `popular games that fail to load can be asked for again`() = runTest {
        games.failing = true
        games.fake.popularGames = listOf(game(1, "Hades"))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(GameListState.Error, viewModel.uiState.value.popular)

        games.failing = false
        viewModel.loadPopular()

        assertEquals(GameListState.Loading, viewModel.uiState.value.popular)

        advanceUntilIdle()

        assertEquals(
            listOf("Hades"),
            (viewModel.uiState.value.popular as GameListState.Loaded).games.map { it.title },
        )
    }
}
