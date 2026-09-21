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

// the four notification switches, split between your own progress and the
// monthly competition
class NotificationSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    //------------------------------
    // inflates the notification settings layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_notification_settings, container, false)
    }

    //------------------------------
    // binds each switch to its stored preference
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barNotifications)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_notifications)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.headerProgress).bindSettingsHeader(R.string.notifications_section_progress)
        view.findViewById<View>(R.id.headerCompetition).bindSettingsHeader(R.string.notifications_section_competition)

        val achievements = view.findViewById<View>(R.id.toggleAchievements)
        val season = view.findViewById<View>(R.id.toggleSeason)
        val rank = view.findViewById<View>(R.id.toggleRank)
        val friends = view.findViewById<View>(R.id.toggleFriends)

        val current = viewModel.settings.value

        achievements.bindSettingsToggle(
            icon = R.drawable.ic_trophy,
            title = R.string.notif_achievements,
            body = R.string.notif_achievements_body,
            checked = current.achievementNotificationsEnabled,
            onCheckedChange = viewModel::setAchievementNotifications,
        )
        season.bindSettingsToggle(
            icon = R.drawable.ic_clock,
            title = R.string.notif_season,
            body = R.string.notif_season_body,
            checked = current.seasonResetNotificationsEnabled,
            onCheckedChange = viewModel::setSeasonResetNotifications,
        )
        rank.bindSettingsToggle(
            icon = R.drawable.ic_trending_up,
            title = R.string.notif_rank,
            body = R.string.notif_rank_body,
            checked = current.rankChangeNotificationsEnabled,
            onCheckedChange = viewModel::setRankChangeNotifications,
        )
        friends.bindSettingsToggle(
            icon = R.drawable.ic_users,
            title = R.string.notif_friends,
            body = R.string.notif_friends_body,
            checked = current.friendActivityNotificationsEnabled,
            onCheckedChange = viewModel::setFriendActivityNotifications,
        )

        // keeps the switches right if the stored values change elsewhere
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    achievements.setSettingsToggleChecked(settings.achievementNotificationsEnabled)
                    season.setSettingsToggleChecked(settings.seasonResetNotificationsEnabled)
                    rank.setSettingsToggleChecked(settings.rankChangeNotificationsEnabled)
                    friends.setSettingsToggleChecked(settings.friendActivityNotificationsEnabled)
                }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
