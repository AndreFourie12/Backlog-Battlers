package com.backlogbattlers.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.backlogbattlers.app.domain.model.AppLanguage
import com.backlogbattlers.app.domain.model.HomeBackdrop
import com.backlogbattlers.app.domain.model.ThemeMode
import com.backlogbattlers.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "user_settings")

//------------------------------
// interface providing operations for observing and updating UserSettings
interface SettingsRepository {

    // observes application UserSettings as a Flow
    fun observeSettings(): Flow<UserSettings>

    // reads UserSettings once, blocking the caller.
    // only for the night mode lookup MainApplication has to make before the
    // first activity is themed, everything else observes the Flow instead
    fun getSettingsBlocking(): UserSettings

    // updates the application language setting
    suspend fun setLanguage(language: AppLanguage)

    // updates the light/dark appearance preference
    suspend fun setThemeMode(mode: ThemeMode)

    // updates the artwork shown behind the home screen header
    suspend fun setHomeBackdrop(backdrop: HomeBackdrop)

    // updates the biometric login preference
    suspend fun setBiometricLoginEnabled(enabled: Boolean)

    // updates the achievement notification preference
    suspend fun setAchievementNotificationsEnabled(enabled: Boolean)

    // updates the rank change notification preference
    suspend fun setRankChangeNotificationsEnabled(enabled: Boolean)

    // updates the season reset notification preference
    suspend fun setSeasonResetNotificationsEnabled(enabled: Boolean)

    // updates the friend activity notification preference
    suspend fun setFriendActivityNotificationsEnabled(enabled: Boolean)
}

//------------------------------
// implementation of SettingsRepository using DataStore Preferences
class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HOME_BACKDROP = stringPreferencesKey("home_backdrop")
        val BIOMETRIC_LOGIN_ENABLED = booleanPreferencesKey("biometric_login_enabled")
        val NOTIF_ACHIEVEMENT = booleanPreferencesKey("notif_achievement")
        val NOTIF_RANK_CHANGE = booleanPreferencesKey("notif_rank_change")
        val NOTIF_SEASON_RESET = booleanPreferencesKey("notif_season_reset")
        val NOTIF_FRIEND_ACTIVITY = booleanPreferencesKey("notif_friend_activity")
    }

    //------------------------------
    // observes UserSettings from DataStore with fallback defaults
    override fun observeSettings(): Flow<UserSettings> {
        return context.dataStore.data.map { prefs ->
            val languageString = prefs[Keys.LANGUAGE]
            val language = languageString?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.ENGLISH

            val themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK

            val homeBackdrop = prefs[Keys.HOME_BACKDROP]?.let {
                runCatching { HomeBackdrop.valueOf(it) }.getOrNull()
            } ?: HomeBackdrop.HILLS

            UserSettings(
                language = language,
                themeMode = themeMode,
                homeBackdrop = homeBackdrop,
                biometricLoginEnabled = prefs[Keys.BIOMETRIC_LOGIN_ENABLED] ?: false,
                achievementNotificationsEnabled = prefs[Keys.NOTIF_ACHIEVEMENT] ?: true,
                rankChangeNotificationsEnabled = prefs[Keys.NOTIF_RANK_CHANGE] ?: true,
                seasonResetNotificationsEnabled = prefs[Keys.NOTIF_SEASON_RESET] ?: true,
                friendActivityNotificationsEnabled = prefs[Keys.NOTIF_FRIEND_ACTIVITY] ?: true,
            )
        }
    }

    //------------------------------
    // reads the stored settings once. MainApplication needs the theme before
    // the first activity inflates, which is earlier than a Flow can deliver
    override fun getSettingsBlocking(): UserSettings {
        return runCatching { runBlocking { observeSettings().first() } }
            .getOrDefault(UserSettings())
    }

    //------------------------------
    // saves selected AppLanguage preference to DataStore
    override suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    //------------------------------
    // saves the selected ThemeMode preference to DataStore
    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    //------------------------------
    // saves the selected HomeBackdrop preference to DataStore
    override suspend fun setHomeBackdrop(backdrop: HomeBackdrop) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOME_BACKDROP] = backdrop.name
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

    //------------------------------
    // saves friend activity notification preference to DataStore
    override suspend fun setFriendActivityNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_FRIEND_ACTIVITY] = enabled
        }
    }
}
