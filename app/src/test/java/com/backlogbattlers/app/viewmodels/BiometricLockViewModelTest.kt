package com.backlogbattlers.app.viewmodels

import android.app.Activity
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.AuthResult
import com.backlogbattlers.app.data.repository.SettingsRepository
import com.backlogbattlers.app.domain.model.AppLanguage
import com.backlogbattlers.app.domain.model.HomeBackdrop
import com.backlogbattlers.app.domain.model.ThemeMode
import com.backlogbattlers.app.domain.model.User
import com.backlogbattlers.app.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// unit tests for BiometricLockViewModel state transitions and repositories interactions
class BiometricLockViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    //------------------------------
    // sets up test main dispatcher before each test
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    //------------------------------
    // resets main dispatcher after each test
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    //------------------------------
    // verifies initial ui state is Idle
    fun `initial uiState is Idle`() = runTest {
        val viewModel = BiometricLockViewModel(FakeAuthRepository(), FakeSettingsRepository())
        assertEquals(BiometricLockUiState.Idle, viewModel.uiState.value)
    }

    @Test
    //------------------------------
    // verifies onAuthenticationStarted updates uiState to Authenticating
    fun `onAuthenticationStarted updates state to Authenticating`() = runTest {
        val viewModel = BiometricLockViewModel(FakeAuthRepository(), FakeSettingsRepository())
        viewModel.onAuthenticationStarted()
        assertEquals(BiometricLockUiState.Authenticating, viewModel.uiState.value)
    }

    @Test
    //------------------------------
    // verifies onAuthenticationSucceeded updates uiState to Success
    fun `onAuthenticationSucceeded updates state to Success`() = runTest {
        val viewModel = BiometricLockViewModel(FakeAuthRepository(), FakeSettingsRepository())
        viewModel.onAuthenticationSucceeded()
        assertEquals(BiometricLockUiState.Success, viewModel.uiState.value)
    }

    @Test
    //------------------------------
    // verifies onAuthenticationTerminated signs out disables biometric and sets state to SignedOut
    fun `onAuthenticationTerminated signs out disables biometric and sets state to SignedOut`() = runTest {
        val fakeAuth = FakeAuthRepository()
        val fakeSettings = FakeSettingsRepository()
        val viewModel = BiometricLockViewModel(fakeAuth, fakeSettings)

        viewModel.onAuthenticationTerminated()

        assertTrue(fakeAuth.signOutCalled)
        assertEquals(false, fakeSettings.lastBiometricLoginEnabled)
        assertEquals(BiometricLockUiState.SignedOut, viewModel.uiState.value)
    }

    // fake AuthRepository for testing
    private class FakeAuthRepository : AuthRepository {
        var signOutCalled = false

        override fun observeCurrentUser(): Flow<User?> = flowOf(null)
        override suspend fun getCurrentUser(): User? = null
        override suspend fun signInWithGoogle(activity: Activity): AuthResult = AuthResult.Failure("not implemented")
        override suspend fun updateDisplayName(displayName: String): Boolean = false
        override suspend fun signOut() {
            signOutCalled = true
        }
    }

    // fake SettingsRepository for testing
    private class FakeSettingsRepository : SettingsRepository {
        var lastBiometricLoginEnabled: Boolean? = null

        override fun observeSettings(): Flow<UserSettings> = flowOf(UserSettings())
        override fun getSettingsBlocking(): UserSettings = UserSettings()
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setHomeBackdrop(backdrop: HomeBackdrop) {}
        override suspend fun setAchievementNotificationsEnabled(enabled: Boolean) {}
        override suspend fun setRankChangeNotificationsEnabled(enabled: Boolean) {}
        override suspend fun setSeasonResetNotificationsEnabled(enabled: Boolean) {}
        override suspend fun setFriendActivityNotificationsEnabled(enabled: Boolean) {}
        override suspend fun setBiometricLoginEnabled(enabled: Boolean) {
            lastBiometricLoginEnabled = enabled
        }
    }
}
//------------------------------EOF------------------------------\\