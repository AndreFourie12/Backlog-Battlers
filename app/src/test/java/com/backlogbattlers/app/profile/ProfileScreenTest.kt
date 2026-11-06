package com.backlogbattlers.app.profile

import android.app.Activity
import com.backlogbattlers.app.R
import com.backlogbattlers.app.data.remote.ProfileApi
import com.backlogbattlers.app.data.remote.SessionExpiredException
import com.backlogbattlers.app.data.repository.AccessTokenSource
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.AuthResult
import com.backlogbattlers.app.data.repository.DeleteAccountResult
import com.backlogbattlers.app.data.repository.ProfileCustomizationRepository
import com.backlogbattlers.app.data.repository.ProfileRepository
import com.backlogbattlers.app.data.repository.ProfileRepositoryImpl
import com.backlogbattlers.app.domain.model.BadgeId
import com.backlogbattlers.app.domain.model.BadgeProgress
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.Profile
import com.backlogbattlers.app.domain.model.ProfileCustomization
import com.backlogbattlers.app.domain.model.User
import com.backlogbattlers.app.viewmodels.ProfileUiState
import com.backlogbattlers.app.viewmodels.ProfileViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// the profile screen: the repository that reads the profile from the API, and the view model behind the screen
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScreenTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    //------------------------------
    // the repository, against a fake of the server

    private val seen = mutableListOf<HttpRequestData>()
    private var body = """
        {"userId":"u1","displayName":"Dylan","avatarUrl":null,"xp":850,"level":4,"xpIntoLevel":250,"xpForNextLevel":400,
         "monthlyPoints":80,"monthRank":2,"gamesInLibrary":103,"gamesCompleted":4,"achievementsUnlocked":212,
         "badges":[
           {"id":"NEMESIS","progress":1,"target":3,"earned":false},
           {"id":"BADGE_FROM_THE_FUTURE","progress":1,"target":1,"earned":true},
           {"id":"FIRST_BLOOD","progress":1,"target":1,"earned":true}],
         "rarestAchievements":[{"achievementId":"A1","name":"Beat it","iconUrl":"https://icons.test/a1.jpg","rarityPercent":0.4,"gameId":7,"gameTitle":"Hades"}],
         "recentCompletions":[
           {"gameId":7,"gameTitle":"Hades","completionType":"COMPLETIONIST","points":120,"completedAt":1762340000000},
           {"gameId":8,"gameTitle":null,"completionType":"A_TYPE_FROM_THE_FUTURE","points":10,"completedAt":1762330000000}]}
    """.trimIndent()

    private val repository = ProfileRepositoryImpl(
        ProfileApi(
            httpClient = HttpClient(
                MockEngine { request ->
                    seen += request
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            baseUrl = "https://api.test",
            tokens = AccessTokenSource(fresh = { "token" }, renew = { "token" }, endSession = { }),
        ),
    )

    @Test
    fun `the profile is asked for with the callers token`() = runBlocking {
        repository.getProfile()

        assertEquals("/users/me/profile", seen.single().url.encodedPath)
        assertEquals("Bearer token", seen.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `the profile is mapped, with the level, stats and showcases`() = runBlocking {
        val profile = repository.getProfile()

        assertEquals("Dylan", profile.displayName)
        assertEquals(4, profile.level)
        assertEquals(250, profile.xpIntoLevel)
        assertEquals(400, profile.xpForNextLevel)
        assertEquals(103, profile.gamesInLibrary)
        assertEquals(4, profile.gamesCompleted)
        assertEquals(212, profile.achievementsUnlocked)
        assertEquals(2, profile.monthRank)
        assertEquals("Beat it", profile.rarestAchievements.single().name)
        assertEquals(0.4, profile.rarestAchievements.single().rarityPercent, 0.0)
        assertEquals("Hades", profile.recentCompletions.first().gameTitle)
        assertEquals(CompletionType.COMPLETIONIST, profile.recentCompletions.first().completionType)
    }

    @Test
    fun `a badge this version does not know is left out, and the rest keep the apps order`() = runBlocking {
        val profile = repository.getProfile()

        assertEquals(listOf(BadgeId.FIRST_BLOOD, BadgeId.NEMESIS), profile.badges.map { it.id })
        assertEquals(1, profile.badgesEarned)
    }

    @Test
    fun `a completion type this version does not know is shown as a story completion`() = runBlocking {
        val profile = repository.getProfile()

        assertEquals(CompletionType.MAIN_STORY, profile.recentCompletions.last().completionType)
        assertNull(profile.recentCompletions.last().gameTitle)
    }

    @Test
    fun `a user who has not scored this month has no rank`() = runBlocking {
        body = body.replace("\"monthRank\":2", "\"monthRank\":null")

        assertNull(repository.getProfile().monthRank)
    }

    //------------------------------
    // the view model, against fakes

    private class FakeAuthRepository : AuthRepository {
        var user: User? = User("u1", "Alex", "alex@example.com", null, 0, 1, 0, null)

        override fun observeCurrentUser(): Flow<User?> = MutableStateFlow(user)
        override suspend fun getCurrentUser(): User? = user
        override suspend fun signInWithGoogle(activity: Activity): AuthResult = AuthResult.Failure(R.string.sign_in_failed)
        override suspend fun updateDisplayName(displayName: String): Boolean = false
        override suspend fun signOut() = Unit
        override suspend fun freshAccessToken(): String? = null
        override suspend fun renewAccessToken(): String? = null
        override suspend fun endSession() = Unit
        override suspend fun deleteAccount(): DeleteAccountResult = DeleteAccountResult.FAILED
    }

    private class FakeCustomizationRepository : ProfileCustomizationRepository {
        val saved = MutableStateFlow(ProfileCustomization(pictureId = "pfp_03"))

        override fun observeCustomization(): Flow<ProfileCustomization> = saved
        override suspend fun save(customization: ProfileCustomization) {
            saved.value = customization
        }
        override suspend fun clear(userId: String) = Unit
    }

    private class FakeProfileRepository : ProfileRepository {
        var profile: Profile = sample(level = 1)
        var failure: Throwable? = null
        var loads = 0

        override suspend fun getProfile(): Profile {
            loads++
            failure?.let { throw it }
            return profile
        }
    }

    private companion object {
        fun sample(level: Int) = Profile(
            userId = "u1",
            displayName = "Dylan",
            avatarUrl = null,
            xp = 0,
            level = level,
            xpIntoLevel = 0,
            xpForNextLevel = 100,
            monthlyPoints = 0,
            monthRank = null,
            gamesInLibrary = 0,
            gamesCompleted = 0,
            achievementsUnlocked = 0,
            badges = listOf(BadgeProgress(BadgeId.FIRST_BLOOD, 0, 1, false)),
            rarestAchievements = emptyList(),
            recentCompletions = emptyList(),
        )
    }

    private val auth = FakeAuthRepository()
    private val customization = FakeCustomizationRepository()
    private val fake = FakeProfileRepository()
    private var now = 1_000_000L

    private fun TestScope.viewModel(): ProfileViewModel {
        val vm = ProfileViewModel(auth, customization, fake, clock = { now })
        // the state is only kept while something collects it
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        return vm
    }

    private fun ProfileViewModel.state(): ProfileUiState = uiState.value

    @Test
    fun `the profile loads on opening, next to the name and picture`() = runTest {
        fake.profile = sample(level = 3)

        val state = viewModel().state()

        assertEquals(3, state.profile?.level)
        assertEquals("Alex", state.user?.displayName)
        assertEquals("pfp_03", state.customization.pictureId)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `a profile that cannot load shows the error, and trying again loads it`() = runTest {
        fake.failure = java.io.IOException("offline")
        val vm = viewModel()
        assertEquals(R.string.profile_error_load, vm.state().errorMessage)
        assertNull(vm.state().profile)
        assertFalse(vm.state().isLoading)

        fake.failure = null
        vm.refresh()

        assertNull(vm.state().errorMessage)
        assertEquals(1, vm.state().profile?.level)
    }

    @Test
    fun `a session that ended shows no error, the app is already going back to login`() = runTest {
        fake.failure = SessionExpiredException()

        val state = viewModel().state()

        assertNull(state.errorMessage)
        assertNull(state.message)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a reload that fails keeps the profile on screen and says so once`() = runTest {
        val vm = viewModel()

        fake.failure = java.io.IOException("offline")
        vm.refresh(pulled = true)

        assertEquals(1, vm.state().profile?.level)
        assertNull(vm.state().errorMessage)
        assertEquals(R.string.profile_error_load, vm.state().message)
        assertFalse(vm.state().isRefreshing)
        vm.messageShown()
        assertNull(vm.state().message)
    }

    @Test
    fun `pulling to refresh replaces the profile with the newest`() = runTest {
        val vm = viewModel()

        fake.profile = sample(level = 5)
        vm.refresh(pulled = true)

        assertEquals(5, vm.state().profile?.level)
        assertFalse(vm.state().isRefreshing)
    }

    @Test
    fun `coming back soon reloads nothing, and coming back later does`() = runTest {
        val vm = viewModel()
        assertEquals(1, fake.loads)

        now += 10_000
        vm.refreshIfStale()
        assertEquals(1, fake.loads)

        now += 25_000
        fake.profile = sample(level = 2)
        vm.refreshIfStale()
        assertEquals(2, fake.loads)
        assertEquals(2, vm.state().profile?.level)
    }

    @Test
    fun `coming back to a profile that never loaded tries again at once`() = runTest {
        fake.failure = java.io.IOException("offline")
        val vm = viewModel()
        assertTrue(vm.state().profile == null)

        fake.failure = null
        vm.refreshIfStale()

        assertEquals(2, fake.loads)
        assertEquals(1, vm.state().profile?.level)
    }
}
//------------------------------EOF------------------------------\\
