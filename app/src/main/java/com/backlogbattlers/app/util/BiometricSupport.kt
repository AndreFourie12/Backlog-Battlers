package com.backlogbattlers.app.util

import android.content.Context
import androidx.biometric.BiometricManager

// whether the device can currently do a strong biometric check and why not if it cant
sealed class BiometricAvailability {

    // biometric hardware and enrolment are ready
    data object Available : BiometricAvailability()

    // device lacks biometric hardware or it is unavailable
    data object NoHardware : BiometricAvailability()

    // biometric hardware is available but no biometrics are enrolled
    data object NotEnrolled : BiometricAvailability()

    // biometric authentication is currently unavailable
    data object Unavailable : BiometricAvailability()
}

// checks whether this device can currently authenticate with a strong biometric
fun interface BiometricAvailabilityChecker {
    fun check(): BiometricAvailability
}

// implemented with androidx.biometric.BiometricManager
class AndroidBiometricAvailabilityChecker(
    private val context: Context,
) : BiometricAvailabilityChecker {

    //------------------------------
    // asks BiometricManager whether a BIOMETRIC_STRONG check is currently possible and maps its result to a BiometricAvailability
    override fun check(): BiometricAvailability {
        val result = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        // this when picks the specific reason a check would fail, so callers can
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            else -> BiometricAvailability.Unavailable
        }
    }
}

//------------------------------
// decides whether the biometric lock screen should be shown at all, given the
fun shouldRequireBiometricUnlock(biometricEnabled: Boolean, availability: BiometricAvailability): Boolean {

    // this statement returns true only when biometric is enabled in settings and hardware is ready
    return biometricEnabled && availability == BiometricAvailability.Available
}
//------------------------------EOF------------------------------\\