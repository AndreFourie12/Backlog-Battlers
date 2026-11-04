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
import coil.dispose
import coil.load
import coil.network.HttpException
import com.backlogbattlers.app.R
import java.util.concurrent.ConcurrentHashMap



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

fun RecyclerView.addGridSpacing(spanCount: Int, gap: Int = resources.getDimensionPixelSize(R.dimen.list_gap)) {
    addItemDecoration(GridSpacingDecoration(spanCount, gap))
}

fun ImageView.loadArtwork(url: String?) {
    load(url) {
        crossfade(true)
    }
}

// the picture addresses the server answered with an error (mostly 404, a Steam picture the game doesn't
// have) this session, so a list scrolled back over goes straight to a picture that works. a connection
// failure is not remembered: that picture may well load once the phone is back online
private val missingArtwork: MutableSet<String> = ConcurrentHashMap.newKeySet()

//------------------------------
// fills a wide game tile with the first of urls that loads, trying the next whenever one fails, and
// shows NoImageDrawable when none of them load (or there are none). see Game.landscapeArtUrls
fun ImageView.loadLandscapeArt(urls: List<String>) {
    val index = urls.indexOfFirst { it !in missingArtwork }
    if (index == -1) {
        dispose()
        setImageDrawable(NoImageDrawable(context))
        return
    }
    val url = urls[index]
    load(url) {
        crossfade(true)
        // a rebound row cancels this request, which ends in onCancel, so this only runs for a real failure
        listener(onError = { _, result ->
            if (result.throwable is HttpException) missingArtwork += url
            loadLandscapeArt(urls.drop(index + 1))
        })
    }
}

//------------------------------End Of File------------------------------\\