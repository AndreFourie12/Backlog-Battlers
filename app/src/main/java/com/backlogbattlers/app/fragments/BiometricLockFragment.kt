package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.core.ServiceLocator
import com.backlogbattlers.app.util.BiometricLockReason
import com.backlogbattlers.app.util.UNLOCK_AUTHENTICATORS
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.BiometricLockUiState
import com.backlogbattlers.app.viewmodels.BiometricLockViewModel
import kotlinx.coroutines.launch

// fingerprint lock screen shown after a cold launch when biometric login is on.
// the prompt accepts a fingerprint or face, or the phone's PIN, pattern or password. if it is
// closed without unlocking, the screen stays locked and offers to try again or sign in again
class BiometricLockFragment : Fragment() {

    private val viewModel: BiometricLockViewModel by viewModels()

    //------------------------------
    // inflates the biometric lock layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_biometric_lock, container, false)
    }

    //------------------------------
    // observes ui state for navigation, then starts the scan
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val body = view.findViewById<TextView>(R.id.tvBiometricLockBody)
        val retry = view.findViewById<View>(R.id.btnBiometricRetry)
        val signInAgain = view.findViewById<View>(R.id.btnBiometricSignInAgain)
        retry.setOnClickListener { showBiometricPrompt() }
        signInAgain.setOnClickListener { viewModel.onSignInAgain() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val locked = state as? BiometricLockUiState.Locked
                    // retrying only makes sense while the sensor or screen lock is still there
                    retry.setVisible(locked != null && locked.reason != BiometricLockReason.UNAVAILABLE)
                    signInAgain.setVisible(locked != null)
                    body.setText(if (locked != null) lockMessage(locked.reason) else R.string.biometric_lock_body)

                    when (state) {
                        is BiometricLockUiState.Success -> {
                            // the settings sync SplashFragment holds back until the user is in
                            ServiceLocator.applicationScope.launch { ServiceLocator.settingsSync.sync() }
                            findNavController().navigate(R.id.action_biometricLockFragment_to_homeFragment)
                        }
                        is BiometricLockUiState.SignedOut -> {
                            findNavController().navigate(R.id.action_biometricLockFragment_to_loginFragment)
                        }
                        BiometricLockUiState.Idle,
                        BiometricLockUiState.Authenticating,
                        is BiometricLockUiState.Locked -> Unit
                    }
                }
            }
        }

        // a recreated screen (e.g. after rotating) that was already showing its buttons waits for a tap
        if (viewModel.uiState.value !is BiometricLockUiState.Locked) showBiometricPrompt()
    }

    private fun lockMessage(reason: BiometricLockReason): Int = when (reason) {
        BiometricLockReason.CANCELLED -> R.string.biometric_lock_cancelled
        BiometricLockReason.LOCKED_OUT -> R.string.biometric_lock_locked_out
        BiometricLockReason.UNAVAILABLE -> R.string.biometric_lock_unavailable
    }

    //------------------------------
    // builds and immediately shows the system unlock popup. with the screen lock allowed as a
    // fallback, the system adds its own "Use PIN" option, so the prompt has no cancel button of ours
    private fun showBiometricPrompt() {
        viewModel.onAuthenticationStarted()

        val executor = ContextCompat.getMainExecutor(requireContext())
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onAuthenticationSucceeded()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.onAuthenticationError(errorCode)
            }
        }

        val prompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(UNLOCK_AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }
}
//------------------------------EOF------------------------------\
