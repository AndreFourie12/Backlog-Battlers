package com.backlogbattlers.app.util

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
}
//------------------------------EOF------------------------------\\