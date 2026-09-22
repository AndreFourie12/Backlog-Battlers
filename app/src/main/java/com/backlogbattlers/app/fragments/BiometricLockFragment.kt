package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.viewmodels.BiometricLockUiState
import com.backlogbattlers.app.viewmodels.BiometricLockViewModel
import kotlinx.coroutines.launch

// fingerprint lock screen shown after a cold launch when biometric login is on.
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->

                    when (state) {
                        is BiometricLockUiState.Success -> {
                            findNavController().navigate(R.id.action_biometricLockFragment_to_homeFragment)
                        }
                        is BiometricLockUiState.SignedOut -> {
                            Toast.makeText(requireContext(), R.string.biometric_signed_out_message, Toast.LENGTH_LONG).show()
                            findNavController().navigate(R.id.action_biometricLockFragment_to_loginFragment)
                        }
                        BiometricLockUiState.Idle, BiometricLockUiState.Authenticating -> Unit
                    }
                }
            }
        }

        showBiometricPrompt()
    }

    //------------------------------
    // builds and immediately shows the system fingerprint popup
    private fun showBiometricPrompt() {
        viewModel.onAuthenticationStarted()

        val executor = ContextCompat.getMainExecutor(requireContext())
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onAuthenticationSucceeded()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.onAuthenticationTerminated()
            }
        }

        val prompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_negative_button))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo)
    }
}
//------------------------------EOF------------------------------\\