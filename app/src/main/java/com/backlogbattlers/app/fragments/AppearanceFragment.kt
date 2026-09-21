package com.backlogbattlers.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.HomeBackdrop
import com.backlogbattlers.app.domain.model.ThemeMode
import com.backlogbattlers.app.util.applyThemeMode
import com.backlogbattlers.app.util.backdropArt
import com.backlogbattlers.app.util.backdropLabel
import com.backlogbattlers.app.util.bindSettingsHeader
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

// light or dark, and which artwork sits behind the home screen header
class AppearanceFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    // one tile per HomeBackdrop, kept so the tick can move without rebuilding
    private val backdropTiles = mutableMapOf<HomeBackdrop, View>()

    //------------------------------
    // inflates the appearance fragment layout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_appearance, container, false)
    }

    //------------------------------
    // builds both pickers then follows stored settings for the selection
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bar = view.findViewById<View>(R.id.barAppearance)
        bar.findViewById<TextView>(R.id.tvSettingsBarTitle).setText(R.string.title_appearance)
        bar.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.headerTheme).bindSettingsHeader(R.string.appearance_section_theme)
        view.findViewById<View>(R.id.headerBackdrop).bindSettingsHeader(R.string.appearance_section_backdrop)

        val darkOption = view.findViewById<View>(R.id.optionThemeDark)
        val lightOption = view.findViewById<View>(R.id.optionThemeLight)

        bindThemeOption(
            option = darkOption,
            icon = R.drawable.ic_moon,
            title = R.string.appearance_theme_dark,
            body = R.string.appearance_theme_dark_body,
            mode = ThemeMode.DARK,
        )
        bindThemeOption(
            option = lightOption,
            icon = R.drawable.ic_sun,
            title = R.string.appearance_theme_light,
            body = R.string.appearance_theme_light_body,
            mode = ThemeMode.LIGHT,
        )

        buildBackdropPicker(view.findViewById(R.id.containerBackdrops))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    markThemeSelection(darkOption, settings.themeMode == ThemeMode.DARK)
                    markThemeSelection(lightOption, settings.themeMode == ThemeMode.LIGHT)
                    backdropTiles.forEach { (backdrop, tile) ->
                        markBackdropSelection(tile, backdrop == settings.homeBackdrop)
                    }
                }
            }
        }
    }

    //------------------------------
    // the tiles belong to the view, not the fragment, so they go with it
    override fun onDestroyView() {
        backdropTiles.clear()
        super.onDestroyView()
    }

    //------------------------------
    // fills one of the two theme cards and wires its tap
    private fun bindThemeOption(
        option: View,
        icon: Int,
        title: Int,
        body: Int,
        mode: ThemeMode,
    ) {
        option.findViewById<ImageView>(R.id.ivThemeOptionIcon).setImageResource(icon)
        option.findViewById<TextView>(R.id.tvThemeOptionTitle).setText(title)
        option.findViewById<TextView>(R.id.tvThemeOptionBody).setText(body)
        option.setOnClickListener {
            viewModel.setThemeMode(mode)
            // recreates the activity, which is what makes the change visible
            applyThemeMode(mode)
        }
    }

    //------------------------------
    // accent ring and tick on the appearance that is applied
    private fun markThemeSelection(option: View, selected: Boolean) {
        option.setBackgroundResource(
            if (selected) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        option.findViewById<View>(R.id.ivThemeOptionCheck).setVisible(selected)
    }

    //------------------------------
    // inflates a tile for every backdrop the app ships with
    private fun buildBackdropPicker(container: LinearLayout) {
        container.removeAllViews()
        backdropTiles.clear()

        HomeBackdrop.entries.forEach { backdrop ->
            val tile = container.inflateChild(R.layout.item_backdrop_option)

            // rounds the artwork off against the frame's background shape
            tile.findViewById<View>(R.id.frameBackdropThumb).clipToOutline = true

            tile.findViewById<ImageView>(R.id.ivBackdropArt)
                .setImageResource(backdropArt(backdrop))
            tile.findViewById<TextView>(R.id.tvBackdropLabel)
                .setText(backdropLabel(backdrop))
            tile.findViewById<View>(R.id.viewBackdropOutline).setOnClickListener {
                viewModel.setHomeBackdrop(backdrop)
            }

            container.addView(tile)
            backdropTiles[backdrop] = tile
        }
    }

    //------------------------------
    // accent ring and tick on the backdrop the home screen is using
    private fun markBackdropSelection(tile: View, selected: Boolean) {
        tile.findViewById<View>(R.id.viewBackdropOutline).setBackgroundResource(
            if (selected) R.drawable.bg_thumb_selected else R.drawable.bg_thumb_outline
        )
        tile.findViewById<View>(R.id.ivBackdropCheck).setVisible(selected)
    }
}
//------------------------------EOF------------------------------\\
