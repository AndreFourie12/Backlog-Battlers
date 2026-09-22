package com.backlogbattlers.app.games

import com.backlogbattlers.app.data.repository.GameRepository
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.domain.model.LibrarySort
import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.search.FakeGameRepository
import com.backlogbattlers.app.search.FakeLibraryRepository
import com.backlogbattlers.app.search.achievement
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
    fun `the library list pairs each entry with its cached game`() = runTest {
        val hades = game(1, "Hades")
        games.fake.cachedGames = mapOf(1 to hades)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addToLibrary(hades)
        advanceUntilIdle()

        val libraryGame = viewModel.uiState.value.libraryGames.single()
        assertEquals("Hades", libraryGame.game.title)
        assertEquals(LibraryStatus.BACKLOG, libraryGame.entry.status)
    }

    @Test
    fun `adding a game fetches its achievements, so the library tile knows a real total`() = runTest {
        val hades = game(1, "Hades")
        games.fake.cachedGames = mapOf(1 to hades)
        games.fake.achievementsByGame = mapOf(1 to listOf(achievement("a"), achievement("b"), achievement("c")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addToLibrary(hades)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.libraryGames.single().game.totalAchievements)
    }

    @Test
    fun `a library game added before achievement totals were tracked catches up on the next load`() = runTest {
        val hades = game(1, "Hades") // no totalAchievements set, as if added before this existed
        games.fake.cachedGames = mapOf(1 to hades)
        games.fake.achievementsByGame = mapOf(1 to listOf(achievement("a"), achievement("b")))
        val viewModel = viewModel()
        advanceUntilIdle()
        library.addToLibrary(gameId = 1, platform = Platform.PC, status = LibraryStatus.BACKLOG)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.libraryGames.single().game.totalAchievements)
    }

    @Test
    fun `a game the cache has not seen yet is counted but left out of the library list`() = runTest {
        val hades = game(1, "Hades") // deliberately not put in games.fake.cachedGames
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addToLibrary(hades)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.libraryCount)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.libraryGames)
    }

    @Test
    fun `the status chips filter the library list, and All keeps everything`() = runTest {
        val hades = game(1, "Hades")
        val celeste = game(2, "Celeste")
        games.fake.cachedGames = mapOf(1 to hades, 2 to celeste)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.addToLibrary(hades)
        viewModel.addToLibrary(celeste)
        advanceUntilIdle()

        // both are BACKLOG: adding a game does not offer any other starting status yet
        viewModel.setStatusFilter(LibraryStatus.BACKLOG)
        assertEquals(
            setOf("Hades", "Celeste"),
            viewModel.uiState.value.visibleLibraryGames.map { it.game.title }.toSet(),
        )

        viewModel.setStatusFilter(LibraryStatus.COMPLETED)
        assertEquals(emptyList<String>(), viewModel.uiState.value.visibleLibraryGames.map { it.game.title })

        viewModel.setStatusFilter(null)
        assertEquals(2, viewModel.uiState.value.visibleLibraryGames.size)
    }

    @Test
    fun `the search box narrows the library list by title, and clears back to everything`() = runTest {
        val hades = game(1, "Hades")
        val hollow = game(2, "Hollow Knight")
        games.fake.cachedGames = mapOf(1 to hades, 2 to hollow)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.addToLibrary(hades)
        viewModel.addToLibrary(hollow)
        advanceUntilIdle()

        viewModel.onLibraryQueryChanged("hol")
        assertEquals(listOf("Hollow Knight"), viewModel.uiState.value.visibleLibraryGames.map { it.game.title })

        viewModel.onLibraryQueryChanged("")
        assertEquals(2, viewModel.uiState.value.visibleLibraryGames.size)
    }

    @Test
    fun `the library list is newest first by default, and can be sorted by name`() = runTest {
        val alpha = game(1, "Alpha")
        val zulu = game(2, "Zulu")
        games.fake.cachedGames = mapOf(1 to alpha, 2 to zulu)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.addToLibrary(alpha)
        advanceUntilIdle()
        viewModel.addToLibrary(zulu)
        advanceUntilIdle()

        assertEquals(listOf("Zulu", "Alpha"), viewModel.uiState.value.visibleLibraryGames.map { it.game.title })

        viewModel.setLibrarySort(LibrarySort.NAME_A_Z)
        assertEquals(listOf("Alpha", "Zulu"), viewModel.uiState.value.visibleLibraryGames.map { it.game.title })

        viewModel.setLibrarySort(LibrarySort.NAME_Z_A)
        assertEquals(listOf("Zulu", "Alpha"), viewModel.uiState.value.visibleLibraryGames.map { it.game.title })
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
