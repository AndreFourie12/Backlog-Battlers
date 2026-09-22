package com.backlogbattlers.app.util

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// spaces a GridLayoutManager's columns and rows evenly, keeping the outer edge flush so the
// RecyclerView's own padding (or its parent's, as the games tab's lists use) sets the page margin
class GridSpacingDecoration(
    private val spanCount: Int,
    private val gap: Int,
) : RecyclerView.ItemDecoration() {

    //------------------------------
    // splits the gap across each column so every column stays the same width: the left half of the
    // gap goes on a cell's start side and the right half on its end side, so adjoining cells add up
    // to exactly one gap between them. Rows after the first also get the gap above them
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % spanCount
        outRect.left = column * gap / spanCount
        outRect.right = gap - (column + 1) * gap / spanCount
        if (position >= spanCount) {
            outRect.top = gap
        }
    }
}
//------------------------------EOF------------------------------\\
