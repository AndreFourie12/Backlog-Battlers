package com.backlogbattlers.app.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt

// whether the device can currently unlock the app and why not if it cant
sealed class BiometricAvailability {

    // a fingerprint, face or screen lock is ready to unlock with
    data object Available : BiometricAvailability()

    // device lacks biometric hardware or it is unavailable
    data object NoHardware : BiometricAvailability()

    // nothing is set up yet: no fingerprint or face, and no screen lock to fall back on
    data object NotEnrolled : BiometricAvailability()

    // biometric authentication is currently unavailable
    data object Unavailable : BiometricAvailability()
}

//------------------------------
// what can unlock the app: a fingerprint or face, with the phone's own PIN, pattern or password
// as the fallback when the scan fails or the user would rather type. android 9 and 10 can't pair
// a strong biometric with the screen lock, so there the weak class is used alongside it
val UNLOCK_AUTHENTICATORS: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

// checks whether this device can currently unlock the app
fun interface BiometricAvailabilityChecker {
    fun check(): BiometricAvailability
}

// implemented with androidx.biometric.BiometricManager
class AndroidBiometricAvailabilityChecker(
    private val context: Context,
) : BiometricAvailabilityChecker {

    //------------------------------
    // asks BiometricManager whether the unlock (biometric or screen lock) is currently possible and maps its result to a BiometricAvailability
    override fun check(): BiometricAvailability {
        val result = BiometricManager.from(context).canAuthenticate(UNLOCK_AUTHENTICATORS)

        // this when picks the specific reason a check would fail, so callers can explain it
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

//------------------------------
// whether to ask "unlock with your fingerprint next time?" right after signing in: only once per
// phone, only if it isn't on already, and only on a phone that can actually do it
fun shouldOfferBiometricUnlock(
    biometricEnabled: Boolean,
    alreadyOffered: Boolean,
    availability: BiometricAvailability,
): Boolean {
    return !biometricEnabled && !alreadyOffered && availability == BiometricAvailability.Available
}

//------------------------------
// why the lock screen is waiting rather than unlocked, so it can say what to do next
enum class BiometricLockReason {
    // the user closed the prompt: they can try again, or sign in again instead
    CANCELLED,

    // too many failed attempts: the phone has locked the sensor for a while
    LOCKED_OUT,

    // the sensor or screen lock stopped being available, e.g. the fingerprint was removed
    UNAVAILABLE,
}

// maps the prompt's error code onto a reason the lock screen can explain
fun lockReasonFor(errorCode: Int): BiometricLockReason = when (errorCode) {
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricLockReason.LOCKED_OUT
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> BiometricLockReason.UNAVAILABLE
    else -> BiometricLockReason.CANCELLED
}
//------------------------------EOF------------------------------\\
