package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.backlogbattlers.app.R
import com.backlogbattlers.app.adapters.BadgeAdapter
import com.backlogbattlers.app.adapters.ShowcaseAdapter
import com.backlogbattlers.app.domain.model.BadgeProgress
import com.backlogbattlers.app.domain.model.CompletionType
import com.backlogbattlers.app.domain.model.Profile
import com.backlogbattlers.app.domain.model.ShowcaseAchievement
import com.backlogbattlers.app.util.badgeInfo
import com.backlogbattlers.app.util.bindProfileAvatar
import com.backlogbattlers.app.util.formatPoints
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.ProfileViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

// the profile screen: the signed in user's name and picture, their level and XP, stats, badges,
// rarest achievements and recent completions. the backend works the numbers out (see
// ProfileViewModel); this shows them, with a spinner while they load and a retry if they cannot.
// pull down to reload. the pencil opens edit profile and the gear opens settings
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    //------------------------------
    // inflates the profile fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    //------------------------------
    // reloads a profile that is getting old each time the user comes back to this screen
    override fun onStart() {
        super.onStart()
        viewModel.refreshIfStale()
    }

    //------------------------------
    // wires the buttons, the two grids and pull to refresh, then shows each state of the screen
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_edit_profile).setOnClickListener {
            Log.d(TAG, "Opening edit profile from the profile header")
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            Log.d(TAG, "Opening settings from the profile header")
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }
        view.findViewById<View>(R.id.btn_add_game).setOnClickListener {
            Log.d(TAG, "Opening search to add a first game from the profile")
            findNavController().navigate(R.id.action_profileFragment_to_searchFragment)
        }
        view.findViewById<View>(R.id.btn_profile_retry).setOnClickListener { viewModel.refresh() }

        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipe.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface))
        swipe.setOnRefreshListener { viewModel.refresh(pulled = true) }

        val badgeAdapter = BadgeAdapter(onClick = ::showBadge)
        view.findViewById<RecyclerView>(R.id.rv_badges).apply {
            layoutManager = GridLayoutManager(requireContext(), BADGE_COLUMNS)
            adapter = badgeAdapter
        }
        val showcaseAdapter = ShowcaseAdapter(onClick = ::showAchievement)
        view.findViewById<RecyclerView>(R.id.rv_showcase).apply {
            layoutManager = GridLayoutManager(requireContext(), ShowcaseAdapter.SLOTS)
            adapter = showcaseAdapter
        }

        val avatar = view.findViewById<View>(R.id.avatarProfile)
        val name = view.findViewById<TextView>(R.id.tvProfileName)
        val progress = view.findViewById<ProgressBar>(R.id.progress_profile)
        val errorCard = view.findViewById<View>(R.id.card_profile_error)
        val errorText = view.findViewById<TextView>(R.id.tv_profile_error)
        val content = view.findViewById<View>(R.id.content_profile)
        val xpGroup = view.findViewById<View>(R.id.group_xp)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    swipe.isRefreshing = state.isRefreshing

                    // the name comes from the account at once; before anyone is known the header stays blank
                    val displayName = state.user?.displayName ?: state.profile?.displayName.orEmpty()
                    name.text = displayName
                    avatar.bindProfileAvatar(state.customization.pictureId, displayName)

                    progress.setVisible(state.isLoading)
                    errorCard.setVisible(state.errorMessage != null)
                    state.errorMessage?.let { errorText.setText(it) }

                    val profile = state.profile
                    content.setVisible(profile != null)
                    xpGroup.setVisible(profile != null)
                    if (profile != null) {
                        bindProfile(view, profile, badgeAdapter, showcaseAdapter)
                    }

                    // this if statement shows a failed reload as a toast, so the profile already on screen stays
                    state.message?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.messageShown()
                    }
                }
            }
        }
    }

    //------------------------------
    // fills every part of the screen that the profile decides
    private fun bindProfile(view: View, profile: Profile, badgeAdapter: BadgeAdapter, showcaseAdapter: ShowcaseAdapter) {
        view.findViewById<TextView>(R.id.tv_level).text = getString(R.string.profile_level, profile.level)
        view.findViewById<ProgressBar>(R.id.progress_xp).progress =
            if (profile.xpForNextLevel > 0) profile.xpIntoLevel * 100 / profile.xpForNextLevel else 0
        view.findViewById<TextView>(R.id.tv_xp_progress).text =
            getString(R.string.profile_xp_progress, formatPoints(profile.xpIntoLevel), formatPoints(profile.xpForNextLevel))

        view.findViewById<TextView>(R.id.tv_stat_games).text = formatPoints(profile.gamesInLibrary)
        view.findViewById<TextView>(R.id.tv_stat_completed).text = formatPoints(profile.gamesCompleted)
        view.findViewById<TextView>(R.id.tv_stat_achievements).text = formatPoints(profile.achievementsUnlocked)
        view.findViewById<TextView>(R.id.tv_stat_rank).text =
            profile.monthRank?.let { getString(R.string.profile_rank_value, it) } ?: getString(R.string.profile_rank_none)

        // earned badges first, then the ones closest to being earned
        view.findViewById<TextView>(R.id.tv_badges_earned).text =
            getString(R.string.profile_badges_earned, profile.badgesEarned, profile.badges.size)
        badgeAdapter.submitList(
            profile.badges.sortedWith(
                compareByDescending<BadgeProgress> { it.earned }
                    .thenByDescending { it.progress.toFloat() / it.target }
                    .thenBy { it.id.ordinal },
            ),
        )

        showcaseAdapter.achievements = profile.rarestAchievements
        val rarest = profile.rarestAchievements.firstOrNull()
        view.findViewById<TextView>(R.id.tv_showcase_note).text =
            if (rarest == null) getString(R.string.profile_showcase_empty)
            else getString(R.string.profile_showcase_rarest, rarest.name, percentText(rarest.rarityPercent))

        bindRecentCompletions(view, profile)
    }

    //------------------------------
    // one row for each of the latest completions, or the empty row with its add a game link
    private fun bindRecentCompletions(view: View, profile: Profile) {
        val list = view.findViewById<android.widget.LinearLayout>(R.id.list_recent)
        list.removeAllViews()
        list.setVisible(profile.recentCompletions.isNotEmpty())
        view.findViewById<View>(R.id.row_recent_empty).setVisible(profile.recentCompletions.isEmpty())

        val inflater = LayoutInflater.from(requireContext())
        profile.recentCompletions.forEachIndexed { index, done ->
            if (index > 0) list.addView(inflater.inflate(R.layout.partial_divider, list, false))
            val row = inflater.inflate(R.layout.item_recent_completion, list, false)
            row.findViewById<TextView>(R.id.tv_recent_title).text = done.gameTitle ?: getString(R.string.activity_unknown_game)
            row.findViewById<TextView>(R.id.tv_recent_subtitle).text = getString(
                R.string.profile_recent_row,
                getString(completionLabel(done.completionType)),
                formatPoints(done.points),
                DateUtils.formatDateTime(requireContext(), done.completedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
            )
            list.addView(row)
        }
    }

    //------------------------------
    // what a tapped badge is, what earns it and how far along the user is
    private fun showBadge(badge: BadgeProgress) {
        val info = badgeInfo(badge.id)
        val progress = if (badge.earned) getString(R.string.profile_badge_earned)
        else getString(R.string.profile_badge_progress, badge.progress, badge.target)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(info.name)
            .setMessage(getString(info.description) + "\n\n" + progress)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    //------------------------------
    // a tapped showcase achievement: its name, the game and how rare it is
    private fun showAchievement(achievement: ShowcaseAchievement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(achievement.name)
            .setMessage(getString(R.string.profile_showcase_detail, achievement.gameTitle ?: getString(R.string.activity_unknown_game), percentText(achievement.rarityPercent)))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // 0.4 is "0.4", and 12.0 is "12", so a whole percentage has no trailing ".0"
    private fun percentText(percent: Double): String =
        if (percent % 1.0 == 0.0) percent.toInt().toString() else String.format(Locale.getDefault(), "%.1f", percent)

    private fun completionLabel(type: CompletionType): Int = when (type) {
        CompletionType.MAIN_STORY -> R.string.profile_completion_story
        CompletionType.MAIN_EXTRA -> R.string.profile_completion_extra
        CompletionType.COMPLETIONIST -> R.string.profile_completion_full
    }

    private companion object {
        const val TAG = "ProfileFragment"
        const val BADGE_COLUMNS = 4
    }
}
//------------------------------EOF------------------------------\
