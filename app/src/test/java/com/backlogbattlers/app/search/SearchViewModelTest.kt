package com.backlogbattlers.app.search

import com.backlogbattlers.app.domain.model.BrowseCategory
import com.backlogbattlers.app.domain.model.SearchSort
import com.backlogbattlers.app.viewmodels.GameListState
import com.backlogbattlers.app.viewmodels.SearchMode
import com.backlogbattlers.app.viewmodels.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var games: FakeGameRepository
    private lateinit var library: FakeLibraryRepository
    private lateinit var history: FakeSearchHistoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        games = FakeGameRepository()
        library = FakeLibraryRepository()
        history = FakeSearchHistoryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SearchViewModel(games, library, history)

    @Test
    fun `an empty box rests on the shortcuts`() = runTest {
        games.browseResults = listOf(game(1, "Hades"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchMode.RESTING, state.mode)
        assertEquals(listOf("Hades"), state.trending.map { it.title })
        assertTrue(games.searches.isEmpty())
    }

    @Test
    fun `typing asks the API once the typing stops, not on every keystroke`() = runTest {
        games.searchResults = listOf(game(1, "Hollow Knight"), game(2, "Hollow Knight: Silksong"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onQueryChanged("hol")
        viewModel.onQueryChanged("holl")
        viewModel.onQueryChanged("hollow")
        advanceUntilIdle()

        assertEquals(listOf("hollow" to 10), games.searches)

        val state = viewModel.uiState.value
        assertEquals(SearchMode.TYPING, state.mode)
        assertEquals(
            listOf("Hollow Knight", "Hollow Knight: Silksong"),
            (state.suggestions as GameListState.Loaded).games.map { it.title },
        )
    }

    @Test
    fun `emptying the box goes back to the shortcuts`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onQueryChanged("hollow")
        advanceUntilIdle()
        viewModel.onQueryChanged("")
        advanceUntilIdle()

        assertEquals(SearchMode.RESTING, viewModel.uiState.value.mode)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun `running a search fetches a full page and remembers the query`() = runTest {
        games.searchResults = (1..30).map { game(it, "War Game $it") }
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("war")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchMode.RESULTS, state.mode)
        assertEquals("war" to 50, games.searches.last())
        assertEquals(30, state.visibleResults.size)
        assertEquals(listOf("war"), state.recentSearches)
    }

    @Test
    fun `a genre chip browses that category instead of searching for a word`() = runTest {
        games.browseResults = listOf(game(1, "Baldur's Gate 3", genres = listOf("Role-playing (RPG)")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.browse(BrowseCategory.RPG)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchMode.RESULTS, state.mode)
        assertEquals(BrowseCategory.RPG, state.browsedCategory)
        assertEquals("rpg" to 50, games.browses.last())
        assertEquals(listOf("Baldur's Gate 3"), state.visibleResults.map { it.title })
        assertTrue(state.recentSearches.isEmpty())
    }

    @Test
    fun `the platform and genre chips narrow the results, and only offer what is in them`() = runTest {
        games.searchResults = listOf(
            game(1, "God of War Ragnarok", platforms = listOf("PLAYSTATION"), genres = listOf("Adventure")),
            game(2, "Total War: Warhammer III", platforms = listOf("PC"), genres = listOf("Strategy")),
            game(3, "War Thunder", platforms = listOf("PC"), genres = listOf("Simulator")),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("war")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.visibleResults.size)

        assertEquals(listOf("PC", "PLAYSTATION"), viewModel.uiState.value.platformOptions)
        assertEquals(listOf("Adventure", "Simulator", "Strategy"), viewModel.uiState.value.genreOptions)

        viewModel.setPlatformFilter("PC")
        assertEquals(
            listOf("Total War: Warhammer III", "War Thunder"),
            viewModel.uiState.value.visibleResults.map { it.title },
        )
        assertTrue(viewModel.uiState.value.filters.isNarrowing)

        viewModel.setGenreFilter("Strategy")
        assertEquals(listOf("Total War: Warhammer III"), viewModel.uiState.value.visibleResults.map { it.title })

        viewModel.setPlatformFilter(null)
        viewModel.setGenreFilter(null)
        assertEquals(3, viewModel.uiState.value.visibleResults.size)
        assertFalse(viewModel.uiState.value.filters.isNarrowing)
    }

    @Test
    fun `the sort chip reorders the results and a new search drops the filters`() = runTest {
        games.searchResults = listOf(game(1, "Zelda"), game(2, "Alan Wake"), game(3, "Minecraft"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("game")
        advanceUntilIdle()

        assertEquals(listOf("Zelda", "Alan Wake", "Minecraft"), viewModel.uiState.value.visibleResults.map { it.title })

        viewModel.setSort(SearchSort.NAME_A_Z)
        assertEquals(listOf("Alan Wake", "Minecraft", "Zelda"), viewModel.uiState.value.visibleResults.map { it.title })

        viewModel.setSort(SearchSort.NAME_Z_A)
        assertEquals(listOf("Zelda", "Minecraft", "Alan Wake"), viewModel.uiState.value.visibleResults.map { it.title })

        viewModel.setPlatformFilter("PC")
        viewModel.runSearch("something else")
        advanceUntilIdle()

        assertEquals(SearchSort.RELEVANCE, viewModel.uiState.value.filters.sort)
        assertFalse(viewModel.uiState.value.filters.isNarrowing)
    }

    @Test
    fun `adding a game marks it owned, and adding it twice changes nothing`() = runTest {
        val hollow = game(1, "Hollow Knight")
        games.searchResults = listOf(hollow, game(2, "Silksong"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("hollow")
        advanceUntilIdle()
        assertEquals(emptySet<Int>(), viewModel.uiState.value.ownedGameIds)

        viewModel.addToLibrary(hollow)
        advanceUntilIdle()
        assertEquals(setOf(1), viewModel.uiState.value.ownedGameIds)

        viewModel.addToLibrary(hollow)
        advanceUntilIdle()
        assertEquals(setOf(1), viewModel.uiState.value.ownedGameIds)
    }

    @Test
    fun `a dropped connection shows the retry state, and retrying asks again`() = runTest {
        games.failing = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("hades")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.results is GameListState.Error)

        games.failing = false
        games.searchResults = listOf(game(1, "Hades"))
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf("Hades"), viewModel.uiState.value.visibleResults.map { it.title })
    }

    @Test
    fun `retrying a browse browses again rather than searching for an empty query`() = runTest {
        games.failing = true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.browse(BrowseCategory.PUZZLE)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.results is GameListState.Error)

        games.failing = false
        games.browseResults = listOf(game(1, "Portal 2"))
        val searchesBefore = games.searches.size
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf("Portal 2"), viewModel.uiState.value.visibleResults.map { it.title })
        assertEquals("puzzle" to 50, games.browses.last())
        assertEquals(searchesBefore, games.searches.size)
    }

    @Test
    fun `a recent search can be replayed and forgotten`() = runTest {
        games.searchResults = listOf(game(1, "Elden Ring"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("elden ring")
        advanceUntilIdle()
        assertEquals(listOf("elden ring"), viewModel.uiState.value.recentSearches)

        viewModel.removeRecentSearch("elden ring")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recentSearches.isEmpty())
    }

    @Test
    fun `clearing the box puts the screen and its filters back to resting`() = runTest {
        games.searchResults = listOf(game(1, "Hades", platforms = listOf("PC")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.runSearch("hades")
        advanceUntilIdle()
        viewModel.setPlatformFilter("PC")

        viewModel.clearQuery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchMode.RESTING, state.mode)
        assertEquals("", state.query)
        assertFalse(state.filters.isNarrowing)
    }

    @Test
    fun `trending failing does not take the rest of the screen down with it`() = runTest {
        games.failing = true
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(SearchMode.RESTING, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.trending.isEmpty())
    }
}
