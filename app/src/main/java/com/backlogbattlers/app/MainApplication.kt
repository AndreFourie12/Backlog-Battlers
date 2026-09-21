package com.backlogbattlers.app

import android.app.Application
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.util.applyThemeMode

// custom application class, initializes application singletons in ServiceLocator
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // the appearance has to be set before the first activity is themed,
        // otherwise the app opens dark and flips to light a frame later
        applyThemeMode(ServiceLocator.settingsRepository.getSettingsBlocking().themeMode)
    }
}
//------------------------------EOF------------------------------\\
