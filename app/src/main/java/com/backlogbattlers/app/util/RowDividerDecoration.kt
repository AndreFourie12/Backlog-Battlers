package com.backlogbattlers.app.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

// draws a faint line between the rows of a vertical list, but not above the first row or below the last.
// gap is the space kept clear above and below each line
class RowDividerDecoration(
    color: Int,
    private val thickness: Int,
    private val gap: Int,
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private val bounds = Rect()

    //------------------------------
    // reserves room under every row that has another row after it, for the gap above the line, the line and the gap below it
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (hasRowBelow(view, parent, state)) {
            outRect.bottom = gap + thickness + gap
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()

        for (index in 0 until parent.childCount) {
            val row = parent.getChildAt(index)
            if (!hasRowBelow(row, parent, state)) continue

            // the decorated bounds end below the room reserved above, so the line sits one gap up from there
            parent.getDecoratedBoundsWithMargins(row, bounds)
            val lineBottom = bounds.bottom + row.translationY.roundToInt() - gap
            canvas.drawRect(left, (lineBottom - thickness).toFloat(), right, lineBottom.toFloat(), paint)
        }
    }

    private fun hasRowBelow(row: View, parent: RecyclerView, state: RecyclerView.State): Boolean {
        val position = parent.getChildAdapterPosition(row)
        return position != RecyclerView.NO_POSITION && position < state.itemCount - 1
    }
}
//------------------------------EOF------------------------------\\