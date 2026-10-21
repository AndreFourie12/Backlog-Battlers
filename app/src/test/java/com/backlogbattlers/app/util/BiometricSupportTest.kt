package com.backlogbattlers.app.util

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// unit tests for shouldRequireBiometricUnlock logic
class BiometricSupportTest {

    @Test
    //------------------------------
    // verifies biometric unlock is not required when setting is turned off
    fun `biometric unlock is false when setting is disabled regardless of availability`() {
        assertFalse(shouldRequireBiometricUnlock(biometricEnabled = false, availability = BiometricAvailability.Available))
    }

    @Test
    //------------------------------
    // verifies biometric unlock is required when setting is enabled and hardware is available
    fun `biometric unlock is true when setting is enabled and biometric is available`() {
        assertTrue(shouldRequireBiometricUnlock(biometricEnabled = true, availability = BiometricAvailability.Available))
    }

    @Test
    //------------------------------
    // verifies biometric unlock is false when hardware is missing
    fun `biometric unlock is false when device has no biometric hardware`() {
        assertFalse(shouldRequireBiometricUnlock(biometricEnabled = true, availability = BiometricAvailability.NoHardware))
    }

    @Test
    //------------------------------
    // verifies biometric unlock is false when no biometrics are enrolled
    fun `biometric unlock is false when device has no biometrics enrolled`() {
        assertFalse(shouldRequireBiometricUnlock(biometricEnabled = true, availability = BiometricAvailability.NotEnrolled))
    }

    @Test
    //------------------------------
    // verifies biometric unlock is false when biometric authentication is unavailable
    fun `biometric unlock is false when biometric authentication is unavailable`() {
        assertFalse(shouldRequireBiometricUnlock(biometricEnabled = true, availability = BiometricAvailability.Unavailable))
    }

    @Test
    //------------------------------
    // verifies the "unlock with your fingerprint?" question comes after a first sign-in on a capable phone
    fun `fingerprint unlock is offered once, on a phone that can do it`() {
        assertTrue(shouldOfferBiometricUnlock(biometricEnabled = false, alreadyOffered = false, availability = BiometricAvailability.Available))
    }

    @Test
    //------------------------------
    // verifies the question is never repeated, or asked when it is already on
    fun `fingerprint unlock is not offered again, or when it is already on`() {
        assertFalse(shouldOfferBiometricUnlock(biometricEnabled = false, alreadyOffered = true, availability = BiometricAvailability.Available))
        assertFalse(shouldOfferBiometricUnlock(biometricEnabled = true, alreadyOffered = false, availability = BiometricAvailability.Available))
    }

    @Test
    //------------------------------
    // verifies a phone with nothing to unlock with is never asked
    fun `fingerprint unlock is not offered on a phone that cannot do it`() {
        assertFalse(shouldOfferBiometricUnlock(biometricEnabled = false, alreadyOffered = false, availability = BiometricAvailability.NoHardware))
        assertFalse(shouldOfferBiometricUnlock(biometricEnabled = false, alreadyOffered = false, availability = BiometricAvailability.NotEnrolled))
    }

    @Test
    //------------------------------
    // verifies each way the prompt can close maps onto what the lock screen should say
    fun `prompt errors map onto the lock screen's reasons`() {
        assertEquals(BiometricLockReason.CANCELLED, lockReasonFor(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(BiometricLockReason.CANCELLED, lockReasonFor(BiometricPrompt.ERROR_CANCELED))
        assertEquals(BiometricLockReason.LOCKED_OUT, lockReasonFor(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(BiometricLockReason.LOCKED_OUT, lockReasonFor(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
        assertEquals(BiometricLockReason.UNAVAILABLE, lockReasonFor(BiometricPrompt.ERROR_NO_BIOMETRICS))
        assertEquals(BiometricLockReason.UNAVAILABLE, lockReasonFor(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
    }
}
//------------------------------EOF------------------------------\\