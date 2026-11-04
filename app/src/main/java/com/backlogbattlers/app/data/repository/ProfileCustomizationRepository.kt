package com.backlogbattlers.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.backlogbattlers.app.domain.model.ProfileCustomization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.profileCustomizationStore by preferencesDataStore(name = "profile_customization")

interface ProfileCustomizationRepository {

    // the signed-in account's profile setup, or the defaults when nothing is saved or nobody is signed in
    fun observeCustomization(): Flow<ProfileCustomization>

    // saves the whole setup for the signed-in account. does nothing when nobody is signed in
    suspend fun save(customization: ProfileCustomization)

    // forgets the setup of userId, when that account is deleted
    suspend fun clear(userId: String)
}

// DataStore backed implementation of ProfileCustomizationRepository. currentUserId gives the
// signed-in account's id (null when signed out); every value is kept under that id, so a second
// account on the same phone starts from the defaults instead of the first one's picture
class ProfileCustomizationRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val currentUserId: () -> String?,
) : ProfileCustomizationRepository {

    constructor(context: Context, currentUserId: () -> String?) :
        this(context.profileCustomizationStore, currentUserId)

    //------------------------------
    // read for whoever is signed in when the flow starts being collected
    override fun observeCustomization(): Flow<ProfileCustomization> = flow {
        val userId = currentUserId()
        if (userId == null) {
            emit(ProfileCustomization())
            return@flow
        }
        val keys = Keys(userId)
        val defaults = ProfileCustomization()
        emitAll(
            store.data.map { prefs ->
                ProfileCustomization(
                    pictureId = prefs[keys.picture],
                    showBadges = prefs[keys.showBadges] ?: defaults.showBadges,
                    showShowcase = prefs[keys.showShowcase] ?: defaults.showShowcase,
                )
            },
        )
    }

    override suspend fun save(customization: ProfileCustomization) {
        val userId = currentUserId()
        if (userId == null) {
            Log.w(TAG, "Not saving the profile setup: nobody is signed in")
            return
        }
        val keys = Keys(userId)
        store.edit { prefs ->
            if (customization.pictureId != null) prefs[keys.picture] = customization.pictureId else prefs.remove(keys.picture)
            prefs[keys.showBadges] = customization.showBadges
            prefs[keys.showShowcase] = customization.showShowcase
        }
        Log.d(TAG, "Saved the profile setup: $customization")
    }

    override suspend fun clear(userId: String) {
        val keys = Keys(userId)
        store.edit { prefs -> keys.all.forEach { prefs.remove(it) } }
        Log.d(TAG, "Cleared the profile setup of a deleted account")
    }

    // the keys of one account's values
    private class Keys(userId: String) {
        val picture = stringPreferencesKey("picture_$userId")
        val showBadges = booleanPreferencesKey("show_badges_$userId")
        val showShowcase = booleanPreferencesKey("show_showcase_$userId")
        val all: List<Preferences.Key<*>> = listOf(picture, showBadges, showShowcase)
    }

    private companion object {
        const val TAG = "ProfileCustomization"
    }
}
