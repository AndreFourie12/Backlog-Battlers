package com.backlogbattlers.app.util

import androidx.appcompat.app.AppCompatDelegate
import com.backlogbattlers.app.domain.model.ThemeMode


// the one place night mode is turned on or off.
// every colour in the app is a token with a values/ and a values-night/ twin,
// so flipping the delegate re-resolves the whole ui without touching a layout


// applies the stored appearance. calling this while an activity is on screen
// recreates it, which is how the change shows up immediately
fun applyThemeMode(mode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(
        when (mode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        }
    )
}
//------------------------------

//------------------------------End Of File------------------------------\\
