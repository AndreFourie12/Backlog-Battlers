package com.backlogbattlers.app

import android.app.Application
import com.backlogbattlers.app.core.ServiceLocator

// custom application class, initializes application singletons in ServiceLocator
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
//------------------------------EOF------------------------------\\