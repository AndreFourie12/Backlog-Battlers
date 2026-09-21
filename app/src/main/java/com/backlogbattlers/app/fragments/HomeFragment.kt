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
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.RecommendationAdapter
import com.backlogbattlers.app.util.addRowDividers
import com.backlogbattlers.app.util.applyTopSystemBarPadding
import com.backlogbattlers.app.util.backdropArt
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.HomeUiState
import com.backlogbattlers.app.viewmodels.HomeViewModel
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

// the home screen.
// wires the header (gear opens settings, hero artwork follows the backdrop
// chosen under Settings > Appearance) and the recommendation rows below it.
class HomeFragment : Fragment() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val viewModel: HomeViewModel by viewModels()

    // inflates the home fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    // wires the header buttons, follows the stored backdrop choice, and
    // loads/displays the recommendation rows
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.home_top_bar).applyTopSystemBarPadding()

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

        val list = view.findViewById<RecyclerView>(R.id.container_recommendations)
        val progress = view.findViewById<View>(R.id.progress_recommendations)
        val errorState = view.findViewById<View>(R.id.state_recommendations_error)
        val adapter = RecommendationAdapter()
        list.adapter = adapter
        list.addRowDividers()

        view.findViewById<View>(R.id.btn_retry_recommendations).setOnClickListener {
            viewModel.loadStarterGames()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progress.setVisible(state is HomeUiState.Loading)
                    errorState.setVisible(state is HomeUiState.Error)
                    list.setVisible(state is HomeUiState.Loaded)
                    if (state is HomeUiState.Loaded) {
                        adapter.submitList(state.recommendations)
                    }
                }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
