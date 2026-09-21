package com.backlogbattlers.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// securely stores authentication tokens and expiration timestamps using EncryptedSharedPreferences
class TokenStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    //------------------------------
    // saves access token, refresh token, user ID, and calculates absolute expiration timestamp in millis
    fun saveTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long, userId: String? = null) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        val editor = preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
        if (userId != null) {
            editor.putString(KEY_USER_ID, userId)
        }
        editor.apply()
    }

    //------------------------------
    // retrieves the stored access token string, or null if none exists
    fun getAccessToken(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN, null)
    }

    //------------------------------
    // retrieves the stored refresh token string, or null if none exists
    fun getRefreshToken(): String? {
        return preferences.getString(KEY_REFRESH_TOKEN, null)
    }

    //------------------------------
    // retrieves the stored current user ID string, or null if none exists
    fun getUserId(): String? {
        return preferences.getString(KEY_USER_ID, null)
    }

    //------------------------------
    // returns true if current epoch time is equal to or past the stored token expiration timestamp
    fun isAccessTokenExpired(): Boolean {
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        return System.currentTimeMillis() >= expiresAt
    }

    //------------------------------
    // removes all stored access, refresh tokens, and user ID from encrypted storage
    fun clear() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_ID)
            .apply()
    }

    companion object {
        private const val PREFS_FILENAME = "secure_token_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "access_token_expires_at"
        private const val KEY_USER_ID = "user_id"
    }
}
//------------------------------EOF------------------------------\\