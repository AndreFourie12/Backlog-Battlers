package com.backlogbattlers.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit

//------------------------------
// android 13+ needs the user's go-ahead before the app may show notifications. the app explains
// why first (MainActivity), and only once per install: "Not now" is respected, and the phone's
// own settings can still turn them on later. reads and writes a small file, so call off the main thread
object NotificationPermission {

    private const val PREFS = "notification_permission"
    private const val KEY_ASKED = "asked"

    //------------------------------
    // whether to show the explanation and then the system prompt
    fun shouldAsk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return !granted && !prefs(context).getBoolean(KEY_ASKED, false)
    }

    //------------------------------
    // remembers the question was asked, whatever the answer
    fun markAsked(context: Context) {
        prefs(context).edit { putBoolean(KEY_ASKED, true) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
//------------------------------EOF------------------------------\\
