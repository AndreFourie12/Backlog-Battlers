package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.ui.login.LoginUiState
import com.backlogbattlers.app.ui.login.LoginViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch


// fragment handling user authentication with sso
class LoginFragment : Fragment() {

    private val viewModel: LoginViewModel by viewModels()

    //------------------------------
    // inflates the login fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    //------------------------------
    // binds ui views and observes LoginViewModel ui state.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSignIn = view.findViewById<Button>(R.id.btn_google_sign_in)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar_loading)

        btnSignIn.setOnClickListener {
            viewModel.onSignInClicked(requireActivity())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LoginUiState.Idle -> {
                            btnSignIn.isEnabled = true
                            progressBar.visibility = View.GONE
                        }
                        is LoginUiState.Loading -> {
                            btnSignIn.isEnabled = false
                            progressBar.visibility = View.VISIBLE
                        }
                        is LoginUiState.Success -> {
                            btnSignIn.isEnabled = true
                            progressBar.visibility = View.GONE
                            offerBiometricUnlockThenGoHome()
                        }
                        is LoginUiState.Error -> {
                            btnSignIn.isEnabled = true
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), state.messageRes, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    //------------------------------
    // right after the first sign-in on this phone, asks whether to unlock with a fingerprint
    // next time. the answer is kept, so the question only comes once per phone; it can be
    // changed later under Settings > Privacy & Data
    private fun offerBiometricUnlockThenGoHome() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!viewModel.shouldAskAboutBiometricUnlock()) {
                goHome()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.biometric_offer_title)
                .setMessage(R.string.biometric_offer_body)
                .setCancelable(false)
                .setNegativeButton(R.string.biometric_offer_decline) { _, _ -> answerOffer(accepted = false) }
                .setPositiveButton(R.string.biometric_offer_accept) { _, _ -> answerOffer(accepted = true) }
                .show()
        }
    }

    private fun answerOffer(accepted: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.answerBiometricOffer(accepted)
            if (accepted) Toast.makeText(requireContext(), R.string.biometric_offer_enabled, Toast.LENGTH_SHORT).show()
            goHome()
        }
    }

    private fun goHome() {
        if (findNavController().currentDestination?.id == R.id.loginFragment) {
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
        }
    }
}
//------------------------------EOF------------------------------\\