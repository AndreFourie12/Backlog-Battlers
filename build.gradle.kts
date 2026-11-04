// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // reads app/google-services.json into the build; only applied when that file exists (see app/build.gradle.kts)
    id("com.google.gms.google-services") version "4.5.0" apply false
}