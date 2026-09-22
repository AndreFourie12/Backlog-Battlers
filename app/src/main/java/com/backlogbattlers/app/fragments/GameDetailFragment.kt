package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.AchievementAdapter
import com.backlogbattlers.app.adapters.AchievementStatusAdapter
import com.backlogbattlers.app.adapters.GameDetailHeaderAdapter
import com.backlogbattlers.app.viewmodels.GameDetailUiState
import com.backlogbattlers.app.viewmodels.GameDetailViewModel
import kotlinx.coroutines.launch

private const val ARG_GAME_ID = "gameId"

// the game detail screen: a game's header stats and its full achievement list. Every achievement
// is tappable, unlocking it live earns its points and moves the completion bar.
//
// The whole page is one RecyclerView (see fragment_game_detail.xml): the header, the loading/
// error row and the achievement rows are three adapters joined with ConcatAdapter, rather than a
// header ScrollView wrapping a nested achievements RecyclerView, which does not reliably grow
// past its first measured height once achievements arrive asynchronously
class GameDetailFragment : Fragment() {

    private val viewModel: GameDetailViewModel by viewModels()

    private lateinit var headerAdapter: GameDetailHeaderAdapter
    private lateinit var statusAdapter: AchievementStatusAdapter
    private lateinit var achievementAdapter: AchievementAdapter

    private var isFavourite = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_game_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerAdapter = GameDetailHeaderAdapter(
            onBack = { findNavController().navigateUp() },
            onFavouriteToggle = ::toggleFavourite,
        )
        statusAdapter = AchievementStatusAdapter(onRetry = viewModel::retryAchievements)
        achievementAdapter = AchievementAdapter(onToggle = viewModel::toggleAchievement)

        view.findViewById<RecyclerView>(R.id.list_detail_content).adapter =
            ConcatAdapter(headerAdapter, statusAdapter, achievementAdapter)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        val gameId = arguments?.getString(ARG_GAME_ID)?.toIntOrNull()
        if (gameId != null) viewModel.load(gameId)
    }

    private fun render(state: GameDetailUiState) {
        headerAdapter.submitState(state)

        statusAdapter.setMode(
            when {
                state.achievementsLoading -> AchievementStatusAdapter.Mode.LOADING
                state.achievementsFailed -> AchievementStatusAdapter.Mode.ERROR
                else -> AchievementStatusAdapter.Mode.NONE
            },
        )
        achievementAdapter.submitList(state.achievements)
    }

    private fun toggleFavourite() {
        isFavourite = !isFavourite
        headerAdapter.setFavourite(isFavourite)
    }
}
//------------------------------EOF------------------------------\\
