package com.backlogbattlers.app.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.backlogbattlers.app.R



fun View.applySystemBarPadding(
    top: Boolean = false,
    bottom: Boolean = false,
    ime: Boolean = false,
) {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val bottomInset = if (ime) maxOf(bars.bottom, keyboard) else bars.bottom
        view.updatePadding(
            top = if (top) initialTop + bars.top else view.paddingTop,
            bottom = if (bottom) initialBottom + bottomInset else view.paddingBottom,
        )
        insets
    }
}

fun View.applyTopSystemBarPadding() = applySystemBarPadding(top = true)

fun View.applyBottomSystemBarPadding(ime: Boolean = false) =
    applySystemBarPadding(bottom = true, ime = ime)

fun ViewGroup.inflateChild(layoutId: Int): View =
    LayoutInflater.from(context).inflate(layoutId, this, false)

fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun RecyclerView.addRowDividers() {
    addItemDecoration(
        RowDividerDecoration(
            color = ContextCompat.getColor(context, R.color.border),
            thickness = resources.getDimensionPixelSize(R.dimen.list_divider_thickness),
            gap = resources.getDimensionPixelSize(R.dimen.list_divider_gap),
        ),
    )
}

fun ImageView.loadArtwork(url: String?) {
    load(url) {
        crossfade(true)
    }
}

//------------------------------End Of File------------------------------\\