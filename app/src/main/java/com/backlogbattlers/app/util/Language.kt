package com.backlogbattlers.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.backlogbattlers.app.domain.model.AppLanguage
import java.util.Locale

// the one place the app's language is switched.
// it uses the per-app language that AndroidX provides: android 13+ keeps it in the system
// (and lists the app under Settings > Apps > Language), older phones get it stored by
// appcompat (autoStoreLocales in the manifest), so it survives the app being closed either way


// switches the whole app to the language. calling this while an activity is on screen
// recreates it, which is how the change shows up immediately
fun applyLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}

// the language the app is showing right now: the one picked in the app, or, if none has been
// picked, the phone's own (when it is one the app is translated into)
fun currentLanguage(): AppLanguage {
    val locale = AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()
    return AppLanguage.fromTag(locale.language)
}
//------------------------------EOF------------------------------\
