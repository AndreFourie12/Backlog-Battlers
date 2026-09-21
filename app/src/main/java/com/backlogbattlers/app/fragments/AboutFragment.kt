package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.BuildConfig
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.bindSettingsHeader
import com.backlogbattlers.app.util.bindSettingsRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// version details and the legal text, all read from the build rather than
// hard coded so it cannot drift
class AboutFragment : Fragment() {

    //------------------------------
    // inflates the about layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    //------------------------------
    // fills the version rows and opens the legal text in a dialog
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barAbout)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_about)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.headerApp).bindSettingsHeader(R.string.about_section_app)
        view.findViewById<View>(R.id.headerLegal).bindSettingsHeader(R.string.about_section_legal)

        view.findViewById<View>(R.id.rowVersion).bindSettingsRow(
            icon = R.drawable.ic_info,
            title = R.string.about_version,
            value = getString(R.string.settings_version_value, BuildConfig.VERSION_NAME),
            showChevron = false,
        )

        val buildLabel = if (BuildConfig.DEBUG) R.string.about_build_debug else R.string.about_build_release
        view.findViewById<View>(R.id.rowBuild).bindSettingsRow(
            icon = R.drawable.ic_gear,
            title = R.string.about_build,
            value = getString(buildLabel),
            showChevron = false,
        )

        view.findViewById<View>(R.id.rowAboutTerms).bindSettingsRow(
            icon = R.drawable.ic_file_text,
            title = R.string.privacy_terms,
        ) {
            showDocument(R.string.privacy_terms, R.string.about_terms_body)
        }

        view.findViewById<View>(R.id.rowAboutPrivacy).bindSettingsRow(
            icon = R.drawable.ic_shield,
            title = R.string.privacy_policy,
        ) {
            showDocument(R.string.privacy_policy, R.string.about_privacy_body)
        }

        view.findViewById<View>(R.id.rowLicences).bindSettingsRow(
            icon = R.drawable.ic_file_text,
            title = R.string.about_open_source,
        ) {
            showDocument(R.string.about_open_source, R.string.about_licences_body)
        }
    }

    //------------------------------
    // the legal text lives in strings rather than on a server, the app has no
    // web presence yet
    private fun showDocument(@StringRes title: Int, @StringRes body: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
//------------------------------EOF------------------------------\\
