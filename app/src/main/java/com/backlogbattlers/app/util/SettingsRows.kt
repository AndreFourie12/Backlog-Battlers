package com.backlogbattlers.app.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import com.google.android.material.materialswitch.MaterialSwitch


// the settings screens are built from three included row layouts rather than a
// RecyclerView, since the lists are short and fixed. these helpers fill one in


// a row that opens another screen. pass value to show the grey text on the
// right, pass showChevron = false for a row that is not navigable yet
fun View.bindSettingsRow(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    value: CharSequence? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    findViewById<ImageView>(R.id.ivSettingsRowIcon).setImageResource(icon)
    findViewById<TextView>(R.id.tvSettingsRowTitle).setText(title)

    val valueView = findViewById<TextView>(R.id.tvSettingsRowValue)
    valueView.text = value
    valueView.setVisible(!value.isNullOrEmpty())

    findViewById<ImageView>(R.id.ivSettingsRowChevron).setVisible(showChevron)

    if (onClick == null) {
        isClickable = false
    } else {
        setOnClickListener { onClick() }
    }
}
//------------------------------


// a two line row that performs an action rather than opening a screen
fun View.bindSettingsAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    onClick: () -> Unit,
) {
    findViewById<ImageView>(R.id.ivSettingsActionIcon).setImageResource(icon)
    findViewById<TextView>(R.id.tvSettingsActionTitle).setText(title)
    findViewById<TextView>(R.id.tvSettingsActionBody).setText(body)
    setOnClickListener { onClick() }
}
//------------------------------


// a row carrying a switch. the whole row toggles, the switch is decorative so
// there is only ever one listener firing
fun View.bindSettingsToggle(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    findViewById<ImageView>(R.id.ivSettingsToggleIcon).setImageResource(icon)
    findViewById<TextView>(R.id.tvSettingsToggleTitle).setText(title)
    findViewById<TextView>(R.id.tvSettingsToggleBody).setText(body)

    val switch = findViewById<MaterialSwitch>(R.id.swSettingsToggle)
    switch.isChecked = checked

    setOnClickListener {
        val next = !switch.isChecked
        switch.isChecked = next
        onCheckedChange(next)
    }
}
//------------------------------


// keeps a toggle row in step with stored settings without firing the listener
fun View.setSettingsToggleChecked(checked: Boolean) {
    findViewById<MaterialSwitch>(R.id.swSettingsToggle).isChecked = checked
}
//------------------------------


// the uppercase label above a group of rows.
// item_settings_header is a bare TextView, so an <include android:id> replaces
// tvSettingsHeader outright and the view handed in here is already the label
fun View.bindSettingsHeader(@StringRes title: Int) {
    val label = this as? TextView ?: findViewById(R.id.tvSettingsHeader)
    label?.setText(title)
}
//------------------------------

//------------------------------End Of File------------------------------\\
