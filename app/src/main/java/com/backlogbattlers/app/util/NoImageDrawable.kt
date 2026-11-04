package com.backlogbattlers.app.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.backlogbattlers.app.R
import kotlin.math.max
import kotlin.math.min

// what a wide game tile shows when none of the game's landscape pictures could be loaded: a crossed out
// camera above "No Image Found" (in the app's language), in the theme's tile colours. it sizes itself to
// whatever tile it fills, from the 62dp search suggestion up to the detail screen's backdrop, and leaves
// the label out when the tile is too short for it. drawn in code, rather than as a picture, so the label
// can be translated
class NoImageDrawable(context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val label = context.getString(R.string.no_image_found)
    private val ink = ContextCompat.getColor(context, R.color.text_tertiary)

    private val backgroundPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.surface_tile) }
    private val icon: Drawable = AppCompatResources.getDrawable(context, R.drawable.ic_no_image)!!.mutate().apply { setTint(ink) }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val tile = bounds
        if (tile.isEmpty) return
        canvas.drawRect(tile, backgroundPaint)

        val width = tile.width().toFloat()
        val height = tile.height().toFloat()

        // the camera is about a third of the tile's height, and the label about a third of the camera,
        // but never smaller than MIN_LABEL_DP and never wider than most of the tile
        val iconSize = min(height * 0.38f, width * 0.3f)
        textPaint.textSize = max(iconSize * 0.36f, MIN_LABEL_DP * density)
        val widest = width * 0.9f
        val labelWidth = textPaint.measureText(label)
        if (labelWidth > widest) textPaint.textSize *= widest / labelWidth

        val gap = iconSize * 0.18f
        val metrics = textPaint.fontMetrics
        val labelHeight = metrics.descent - metrics.ascent
        val showLabel = height >= iconSize + gap + labelHeight + LABEL_ROOM_DP * density
        val blockHeight = if (showLabel) iconSize + gap + labelHeight else iconSize

        val top = tile.top + (height - blockHeight) / 2f
        val left = tile.left + (width - iconSize) / 2f
        icon.setBounds(left.toInt(), top.toInt(), (left + iconSize).toInt(), (top + iconSize).toInt())
        icon.draw(canvas)
        if (showLabel) canvas.drawText(label, tile.exactCenterX(), top + iconSize + gap - metrics.ascent, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        textPaint.alpha = alpha
        icon.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        icon.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        // smallest the label is drawn, and the space it needs to be shown at all
        const val MIN_LABEL_DP = 9f
        const val LABEL_ROOM_DP = 8f
    }
}
//------------------------------EOF------------------------------\\
