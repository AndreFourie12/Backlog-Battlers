package com.backlogbattlers.app.profile

import android.app.Activity
import com.backlogbattlers.app.R
import com.backlogbattlers.app.data.repository.AuthRepository
import com.backlogbattlers.app.data.repository.AuthResult
import com.backlogbattlers.app.data.repository.DeleteAccountResult
import com.backlogbattlers.app.data.repository.ProfileCustomizationRepository
import com.backlogbattlers.app.domain.model.ProfileCustomization
import com.backlogbattlers.app.domain.model.User
import com.backlogbattlers.app.viewmodels.EditProfileEvent
import com.backlogbattlers.app.viewmodels.EditProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EditProfileViewModelTest {

    private val auth = FakeAuthRepository()
    private val customization = FakeProfileCustomizationRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = EditProfileViewModel(auth, customization)

    @Test
    fun `the draft starts from the saved name and setup, with nothing to save`() = runTest {
        customization.saved.value = ProfileCustomization(pictureId = "knight", showBadges = false)

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals("Alex", state.name)
        assertEquals("knight", state.customization.pictureId)
        assertFalse(state.customization.showBadges)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `changing anything makes the draft unsaved, and changing it back does not`() = runTest {
        val vm = viewModel()

        vm.setShowShowcase(false)
        assertTrue(vm.uiState.value.hasChanges)
        vm.setShowShowcase(true)
        assertFalse(vm.uiState.value.hasChanges)

        vm.onNameChanged("Alex  ")
        assertFalse("surrounding spaces are not a change", vm.uiState.value.hasChanges)
        vm.onNameChanged("Sam")
        assertTrue(vm.uiState.value.hasChanges)
    }

    @Test
    fun `nothing is stored until save`() = runTest {
        val vm = viewModel()

        vm.choosePicture("wizard")
        vm.setShowBadges(false)
        vm.onNameChanged("Sam")

        assertEquals(ProfileCustomization(), customization.saved.value)
        assertEquals("Alex", auth.displayName)
    }

    @Test
    fun `save stores the setup and the trimmed new name, then reports it`() = runTest {
        val vm = viewModel()
        val events = mutableListOf<EditProfileEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.choosePicture("wizard")
        vm.setShowShowcase(false)
        vm.onNameChanged("  Sam ")
        vm.save()

        assertEquals(ProfileCustomization(pictureId = "wizard", showShowcase = false), customization.saved.value)
        assertEquals("Sam", auth.displayName)
        assertEquals(listOf<EditProfileEvent>(EditProfileEvent.Saved), events)
        assertFalse(vm.uiState.value.hasChanges)
    }

    @Test
    fun `an unchanged name is not written again`() = runTest {
        val vm = viewModel()

        vm.setShowBadges(false)
        vm.save()

        assertEquals(0, auth.nameWrites)
        assertFalse(customization.saved.value.showBadges)
    }

    @Test
    fun `removing the picture goes back to the initial`() = runTest {
        customization.saved.value = ProfileCustomization(pictureId = "knight")
        val vm = viewModel()

        vm.removePicture()
        vm.save()

        assertNull(customization.saved.value.pictureId)
    }

    @Test
    fun `an empty or too long name is refused and nothing is stored`() = runTest {
        val vm = viewModel()
        val events = mutableListOf<EditProfileEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.choosePicture("wizard")
        vm.onNameChanged("   ")
        vm.save()
        vm.onNameChanged("x".repeat(21))
        vm.save()

        assertEquals(
            listOf(
                EditProfileEvent.Rejected(R.string.edit_profile_name_empty),
                EditProfileEvent.Rejected(R.string.edit_profile_name_too_long),
            ),
            events,
        )
        assertEquals(ProfileCustomization(), customization.saved.value)
        assertEquals("Alex", auth.displayName)
    }

    @Test
    fun `signed out, saving is refused`() = runTest {
        auth.user = null
        val vm = viewModel()
        val events = mutableListOf<EditProfileEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.onNameChanged("Sam")
        vm.save()

        assertEquals(listOf<EditProfileEvent>(EditProfileEvent.Rejected(R.string.edit_profile_signed_out)), events)
        assertEquals(ProfileCustomization(), customization.observeCustomization().first())
    }

    private class FakeAuthRepository : AuthRepository {
        var user: User? = User("u1", "Alex", "alex@example.com", null, 0, 1, 0, null)
        var displayName = "Alex"
        var nameWrites = 0

        override fun observeCurrentUser(): Flow<User?> = MutableStateFlow(user)
        override suspend fun getCurrentUser(): User? = user
        override suspend fun signInWithGoogle(activity: Activity): AuthResult = AuthResult.Failure(R.string.sign_in_failed)
        override suspend fun updateDisplayName(displayName: String): Boolean {
            if (user == null) return false
            nameWrites++
            this.displayName = displayName
            return true
        }
        override suspend fun signOut() = Unit
        override suspend fun freshAccessToken(): String? = null
        override suspend fun renewAccessToken(): String? = null
        override suspend fun endSession() = Unit
        override suspend fun deleteAccount(): DeleteAccountResult = DeleteAccountResult.FAILED
    }

    private class FakeProfileCustomizationRepository : ProfileCustomizationRepository {
        val saved = MutableStateFlow(ProfileCustomization())

        override fun observeCustomization(): Flow<ProfileCustomization> = saved
        override suspend fun save(customization: ProfileCustomization) {
            saved.value = customization
        }
        override suspend fun clear(userId: String) {
            saved.value = ProfileCustomization()
        }
    }
}
