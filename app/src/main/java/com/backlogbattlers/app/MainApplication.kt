package com.backlogbattlers.app

import android.app.Application
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.util.applyThemeMode

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        applyThemeMode(ServiceLocator.settingsRepository.getSettingsBlocking().themeMode)
    }
}
//------------------------------EOF------------------------------\\
