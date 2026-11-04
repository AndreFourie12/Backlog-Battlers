package com.backlogbattlers.app

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.util.applyThemeMode
import kotlinx.coroutines.launch

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Ktor logs through slf4j, which on first use scans the whole APK for a logging provider. that scan
        // froze the main thread for 0.4 - 0.8 s on every launch (StrictMode DiskReadViolation, when the
        // splash screen first builds the HTTP client). the app ships no provider, so slf4j always ended up
        // on its no-op logger anyway; naming it here skips the scan and changes nothing else.
        // must run before anything touches slf4j. https://www.slf4j.org/manual.html#explicitProvider
        System.setProperty("slf4j.provider", "org.slf4j.helpers.NOP_FallbackServiceProvider")
        if (BuildConfig.DEBUG) enableStrictMode()
        ServiceLocator.init(this)

        applyThemeMode(ServiceLocator.settingsRepository.getSettingsBlocking().themeMode)

        // builds the HTTP client in the background while the first screen is being set up. Ktor's JSON support
        // scans the APK for plugins the first time (about 0.3 s of disk reads), so doing it here, early and off
        // the main thread, means it is usually ready before the splash screen asks for it
        ServiceLocator.applicationScope.launch {
            ServiceLocator.httpClient
            Log.d(TAG, "HTTP client ready")
        }
    }

    //------------------------------
    // debug builds only: logs (tag "StrictMode") whenever the main thread reads or writes the disk or
    // uses the network, and any leaked activity or unclosed resource, so work that makes screens
    // stutter shows up in logcat while developing. it only logs, it never crashes the app
    // https://developer.android.com/reference/android/os/StrictMode
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }

    private companion object {
        const val TAG = "MainApplication"
    }
}
//------------------------------EOF------------------------------\\
