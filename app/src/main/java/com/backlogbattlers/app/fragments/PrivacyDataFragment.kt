package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.bindSettingsHeader
import com.backlogbattlers.app.util.bindSettingsToggle
import com.backlogbattlers.app.util.setSettingsToggleChecked
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

// biometric unlock and what the app stores
class PrivacyDataFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    //------------------------------
    // inflates the privacy and data layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_privacy_data, container, false)
    }

    //------------------------------
    // binds the biometric switch
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barPrivacy)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_privacy)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.headerSecurity).bindSettingsHeader(R.string.privacy_section_security)
        view.findViewById<View>(R.id.headerData).bindSettingsHeader(R.string.privacy_section_data)

        val biometric = view.findViewById<View>(R.id.toggleBiometric)
        biometric.bindSettingsToggle(
            icon = R.drawable.ic_lock,
            title = R.string.privacy_biometric,
            body = R.string.privacy_biometric_body,
            checked = viewModel.settings.value.biometricLoginEnabled,
            onCheckedChange = viewModel::setBiometricLogin,
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    biometric.setSettingsToggleChecked(settings.biometricLoginEnabled)
                }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
