package com.backlogbattlers.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import com.backlogbattlers.app.domain.model.CompleteOutcome
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.util.completionDialogText
import com.backlogbattlers.app.viewmodels.GameDetailUiState
import com.backlogbattlers.app.viewmodels.GameDetailViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            onComplete = ::showCompleteSheet,
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
        if (gameId != null) {
            viewModel.load(gameId)
        } else {
            Log.e(TAG, "Opened without a valid game id: ${arguments?.getString(ARG_GAME_ID)}")
        }
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

        state.message?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
        state.completionOutcome?.let {
            showOutcome(it)
            viewModel.outcomeShown()
        }
    }

    // the bottom sheet "Mark as complete" opens: the story, or 100%. one that is not possible is dimmed
    // and says what is missing. picking one asks to confirm first, since a completion cannot be undone
    private fun showCompleteSheet() {
        val state = viewModel.uiState.value
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.sheet_complete)
        val story = dialog.findViewById<View>(R.id.row_complete_story) ?: return
        val full = dialog.findViewById<View>(R.id.row_complete_full) ?: return

        dialog.findViewById<TextView>(R.id.tv_complete_story_hint)?.setText(
            if (state.canCompleteStory) R.string.complete_story_hint else R.string.complete_done_hint,
        )
        story.alpha = if (state.canCompleteStory) 1f else 0.5f
        story.isEnabled = state.canCompleteStory
        if (state.canCompleteStory) story.setOnClickListener { dialog.dismiss(); confirmComplete(CompletionType.MAIN_STORY) }

        dialog.findViewById<TextView>(R.id.tv_complete_full_hint)?.text = when {
            state.hundredPercentCompleted -> getString(R.string.complete_done_hint)
            state.totalCount == 0 -> getString(R.string.complete_hundred_none)
            !state.allUnlocked -> getString(R.string.complete_hundred_locked, state.totalCount, state.totalCount - state.unlockedCount)
            else -> getString(R.string.complete_hundred_ready)
        }
        full.alpha = if (state.canCompleteHundredPercent) 1f else 0.5f
        full.isEnabled = state.canCompleteHundredPercent
        if (state.canCompleteHundredPercent) full.setOnClickListener { dialog.dismiss(); confirmComplete(CompletionType.COMPLETIONIST) }

        // the sheet draws its own rounded background, so the dialog's square one is cleared
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private fun confirmComplete(type: CompletionType) {
        Log.d(TAG, "Asking to confirm completing the game as $type")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.complete_confirm_title)
            .setMessage(if (type == CompletionType.COMPLETIONIST) R.string.complete_confirm_hundred else R.string.complete_confirm_story)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.complete_confirm_yes) { _, _ -> viewModel.complete(type) }
            .show()
    }

    private fun showOutcome(outcome: CompleteOutcome) {
        val (title, message) = completionDialogText(requireContext(), outcome)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toggleFavourite() {
        isFavourite = !isFavourite
        headerAdapter.setFavourite(isFavourite)
        Log.d(TAG, "Favourite turned ${if (isFavourite) "on" else "off"}")
    }

    private companion object {
        const val TAG = "GameDetailFragment"
    }
}
//------------------------------EOF------------------------------\\
