package com.backlogbattlers.app.fragments

import android.os.Bundle
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
import com.backlogbattlers.app.BuildConfig
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.ThemeMode
import com.backlogbattlers.app.domain.model.UserSettings
import com.backlogbattlers.app.util.bindSettingsHeader
import com.backlogbattlers.app.util.bindSettingsRow
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

// the settings list, reached from the gear on the home screen.
// every row here opens one of the screens in this package, except Connected
// Accounts which waits on account linking
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    //------------------------------
    // inflates the settings fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    //------------------------------
    // binds every row, then keeps the Appearance value in step with settings
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barSettings)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_settings)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.headerAccount).bindSettingsHeader(R.string.settings_section_account)
        view.findViewById<View>(R.id.headerPreferences).bindSettingsHeader(R.string.settings_section_preferences)
        view.findViewById<View>(R.id.headerSupport).bindSettingsHeader(R.string.settings_section_support)

        view.findViewById<View>(R.id.rowEditProfile).bindSettingsRow(
            icon = R.drawable.ic_person,
            title = R.string.settings_edit_profile,
        ) {
            findNavController().navigate(R.id.action_settingsFragment_to_editProfileFragment)
        }

        // account linking is not built yet, so this row shows its state and
        // says so rather than opening an empty screen
        view.findViewById<View>(R.id.rowConnectedAccounts).bindSettingsRow(
            icon = R.drawable.ic_link,
            title = R.string.settings_connected_accounts,
            value = getString(R.string.settings_connected_accounts_pending),
            showChevron = false,
        ) {
            Toast.makeText(
                requireContext(),
                R.string.settings_connected_accounts_pending,
                Toast.LENGTH_SHORT,
            ).show()
        }

        view.findViewById<View>(R.id.rowPrivacy).bindSettingsRow(
            icon = R.drawable.ic_shield,
            title = R.string.settings_privacy,
        ) {
            findNavController().navigate(R.id.action_settingsFragment_to_privacyDataFragment)
        }

        view.findViewById<View>(R.id.rowHelp).bindSettingsRow(
            icon = R.drawable.ic_help_circle,
            title = R.string.settings_help,
        ) {
            findNavController().navigate(R.id.action_settingsFragment_to_helpSupportFragment)
        }

        view.findViewById<View>(R.id.rowAbout).bindSettingsRow(
            icon = R.drawable.ic_info,
            title = R.string.settings_about,
            value = getString(R.string.settings_version_value, BuildConfig.VERSION_NAME),
        ) {
            findNavController().navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        view.findViewById<View>(R.id.btnLogOut).setOnClickListener { confirmLogOut() }

        // these two rows carry the current value on the right, so they are
        // rebound whenever the stored settings change
        val notificationsRow = view.findViewById<View>(R.id.rowNotifications)
        val appearanceRow = view.findViewById<View>(R.id.rowAppearance)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    notificationsRow.bindSettingsRow(
                        icon = R.drawable.ic_bell,
                        title = R.string.settings_notifications,
                        value = notificationSummary(settings),
                    ) {
                        findNavController().navigate(R.id.action_settingsFragment_to_notificationSettingsFragment)
                    }

                    appearanceRow.bindSettingsRow(
                        icon = R.drawable.ic_sun,
                        title = R.string.settings_appearance,
                        value = getString(themeLabel(settings.themeMode)),
                    ) {
                        findNavController().navigate(R.id.action_settingsFragment_to_appearanceFragment)
                    }
                }
            }
        }
    }

    //------------------------------
    // the word shown on the right of the Appearance row
    private fun themeLabel(mode: ThemeMode): Int = when (mode) {
        ThemeMode.DARK -> R.string.appearance_theme_dark
        ThemeMode.LIGHT -> R.string.appearance_theme_light
    }

    //------------------------------
    // how many of the notification switches are on, shown on the row so the
    // state is readable without opening the screen
    private fun notificationSummary(settings: UserSettings): String {
        val switches = listOf(
            settings.achievementNotificationsEnabled,
            settings.rankChangeNotificationsEnabled,
            settings.seasonResetNotificationsEnabled,
            settings.friendActivityNotificationsEnabled,
        )
        val on = switches.count { it }

        return when (on) {
            switches.size -> getString(R.string.settings_notifications_all_on)
            0 -> getString(R.string.settings_notifications_all_off)
            else -> getString(R.string.settings_notifications_some_on, on, switches.size)
        }
    }

    //------------------------------
    // signing out drops the session, so it asks first
    private fun confirmLogOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.log_out_confirm_title)
            .setMessage(R.string.log_out_confirm_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.log_out_confirm_action) { _, _ ->
                viewModel.logOut {
                    if (isAdded) {
                        findNavController().navigate(R.id.action_settingsFragment_to_loginFragment)
                    }
                }
            }
            .show()
    }
}
//------------------------------EOF------------------------------\\
