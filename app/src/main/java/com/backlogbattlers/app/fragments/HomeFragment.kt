package com.backlogbattlers.app.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.RecommendationAdapter
import com.backlogbattlers.app.adapters.RecommendationRow
import com.backlogbattlers.app.util.addRowDividers
import com.backlogbattlers.app.util.applyTopSystemBarPadding
import com.backlogbattlers.app.util.backdropArt
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.HomeUiState
import com.backlogbattlers.app.viewmodels.HomeViewModel
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.flow.combine
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

        // the bell opens the notification settings, the app's only notifications screen
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_notificationSettingsFragment)
        }

        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
        }

        view.findViewById<View>(R.id.btn_add_game).setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }

        view.findViewById<View>(R.id.btn_view_leaderboard).setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_standingsFragment)
        }

        view.findViewById<View>(R.id.btn_invite_friends).setOnClickListener {
            Log.d(TAG, "Sharing a friend invite from home")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text))
            }
            startActivity(Intent.createChooser(shareIntent, null))
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
        val adapter = RecommendationAdapter(
            onClick = { game -> openGame(game.gameId) },
            onAdd = viewModel::addToLibrary,
        )
        list.adapter = adapter
        list.addRowDividers()

        view.findViewById<View>(R.id.btn_retry_recommendations).setOnClickListener {
            Log.d(TAG, "Retrying the suggestions")
            viewModel.loadStarterGames()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // the suggestions, each marked with whether it is already in the library
                launch {
                    combine(viewModel.uiState, viewModel.ownedGameIds, ::Pair).collect { (state, owned) ->
                        progress.setVisible(state is HomeUiState.Loading)
                        errorState.setVisible(state is HomeUiState.Error)
                        list.setVisible(state is HomeUiState.Loaded)
                        if (state is HomeUiState.Loaded) {
                            adapter.submitList(
                                state.recommendations.map { RecommendationRow(it, owned = it.game.gameId in owned) },
                            )
                        }
                    }
                }
                launch {
                    viewModel.addedGames.collect { title ->
                        Toast.makeText(requireContext(), getString(R.string.added_to_library, title), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    // opens the game detail screen for a suggestion
    private fun openGame(gameId: Int) {
        Log.d(TAG, "Opening game $gameId from the suggestions")
        findNavController().navigate(
            R.id.action_homeFragment_to_gameDetailFragment,
            Bundle().apply { putString("gameId", gameId.toString()) },
        )
    }

    private companion object {
        const val TAG = "HomeFragment"
    }
}
//------------------------------EOF------------------------------\\
