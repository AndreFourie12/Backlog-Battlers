package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.backdropArt
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

// the home screen.
// only the header is wired here so far: the gear opens settings, and the hero
// artwork follows the backdrop chosen under Settings > Appearance. the welcome
// copy and the recommendation rows below it are still to come
class HomeFragment : Fragment() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    //------------------------------
    // inflates the home fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    //------------------------------
    // wires the header buttons and follows the stored backdrop choice
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
        }

        val backdrop = view.findViewById<ImageView>(R.id.iv_home_backdrop)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.settings.collect { settings ->
                    backdrop.setImageResource(backdropArt(settings.homeBackdrop))
                }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
