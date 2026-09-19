package com.backlogbattlers.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.backlogbattlers.app.domain.model.AppLanguage
import com.backlogbattlers.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

//------------------------------
// interface providing operations for observing and updating UserSettings
interface SettingsRepository {

    // observes application UserSettings as a Flow
    fun observeSettings(): Flow<UserSettings>

    // updates the application language setting
    suspend fun setLanguage(language: AppLanguage)

    // updates the biometric login preference
    suspend fun setBiometricLoginEnabled(enabled: Boolean)

    // updates the achievement notification preference
    suspend fun setAchievementNotificationsEnabled(enabled: Boolean)

    // updates the rank change notification preference
    suspend fun setRankChangeNotificationsEnabled(enabled: Boolean)

    // updates the season reset notification preference
    suspend fun setSeasonResetNotificationsEnabled(enabled: Boolean)
}

//------------------------------
// implementation of SettingsRepository using DataStore Preferences
class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val BIOMETRIC_LOGIN_ENABLED = booleanPreferencesKey("biometric_login_enabled")
        val NOTIF_ACHIEVEMENT = booleanPreferencesKey("notif_achievement")
        val NOTIF_RANK_CHANGE = booleanPreferencesKey("notif_rank_change")
        val NOTIF_SEASON_RESET = booleanPreferencesKey("notif_season_reset")
    }

    //------------------------------
    // observes UserSettings from DataStore with fallback defaults
    override fun observeSettings(): Flow<UserSettings> {
        return context.dataStore.data.map { prefs ->
            val languageString = prefs[Keys.LANGUAGE]
            val language = languageString?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.ENGLISH

            UserSettings(
                language = language,
                biometricLoginEnabled = prefs[Keys.BIOMETRIC_LOGIN_ENABLED] ?: false,
                achievementNotificationsEnabled = prefs[Keys.NOTIF_ACHIEVEMENT] ?: true,
                rankChangeNotificationsEnabled = prefs[Keys.NOTIF_RANK_CHANGE] ?: true,
                seasonResetNotificationsEnabled = prefs[Keys.NOTIF_SEASON_RESET] ?: true,
            )
        }
    }

    //------------------------------
    // saves selected AppLanguage preference to DataStore
    override suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    //------------------------------
    // saves biometric login boolean preference to DataStore
    override suspend fun setBiometricLoginEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOGIN_ENABLED] = enabled
        }
    }

    //------------------------------
    // saves achievement notification preference to DataStore
    override suspend fun setAchievementNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_ACHIEVEMENT] = enabled
        }
    }

    //------------------------------
    // saves rank change notification preference to DataStore
    override suspend fun setRankChangeNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_RANK_CHANGE] = enabled
        }
    }

    //------------------------------
    // saves season reset notification preference to DataStore
    override suspend fun setSeasonResetNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_SEASON_RESET] = enabled
        }
    }
}
