package com.backlogbattlers.app.home

import com.backlogbattlers.app.domain.model.LibraryStatus
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.search.FakeGameRepository
import com.backlogbattlers.app.search.FakeLibraryRepository
import com.backlogbattlers.app.search.FakeSettingsRepository
import com.backlogbattlers.app.search.game
import com.backlogbattlers.app.viewmodels.HomeUiState
import com.backlogbattlers.app.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// the home screen's suggested games: the + on each one adds it to the backlog
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val games = FakeGameRepository()
    private val library = FakeLibraryRepository()
    private val settings = FakeSettingsRepository()

    private val hades = game(1113, "Hades", platforms = listOf("PC", "SWITCH", "PLAYSTATION"))
    private val sekiro = game(113112, "Sekiro: Shadows Die Twice", platforms = listOf("PLAYSTATION", "XBOX"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        games.browseResults = listOf(hades, sekiro)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(games, library, settings)

    @Test
    fun `the suggestions load from the starter games`() = runTest {
        val state = viewModel().uiState.value

        assertTrue(state is HomeUiState.Loaded)
        assertEquals(listOf(hades, sekiro), (state as HomeUiState.Loaded).recommendations.map { it.game })
    }

    @Test
    fun `adding a suggestion puts it in the backlog on the default platform`() = runTest {
        settings.setDefaultPlatform(Platform.SWITCH)

        viewModel().addToLibrary(hades)

        val entry = library.observeLibrary().first().single()
        assertEquals(hades.gameId, entry.gameId)
        assertEquals(Platform.SWITCH, entry.platform)
        assertEquals(LibraryStatus.BACKLOG, entry.status)
    }

    @Test
    fun `a game not on the default platform is filed under its own platform`() = runTest {
        settings.setDefaultPlatform(Platform.PC)

        viewModel().addToLibrary(sekiro)

        assertEquals(Platform.PLAYSTATION, library.observeLibrary().first().single().platform)
    }

    @Test
    fun `an added game shows as owned, so its plus turns into a tick`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.ownedGameIds.value.isEmpty())

        viewModel.addToLibrary(hades)

        assertEquals(setOf(hades.gameId), viewModel.ownedGameIds.value)
    }

    @Test
    fun `tapping the badge of an owned game does not add it twice`() = runTest {
        val viewModel = viewModel()

        viewModel.addToLibrary(hades)
        viewModel.addToLibrary(hades)

        assertEquals(1, library.observeLibrary().first().size)
    }

    @Test
    fun `adding a game announces its title for the added message`() = runTest {
        val viewModel = viewModel()
        val announced = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.addedGames.collect { announced += it }
        }

        viewModel.addToLibrary(hades)

        assertEquals(listOf("Hades"), announced)
    }
}
//------------------------------EOF------------------------------\\
