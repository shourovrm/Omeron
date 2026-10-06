package com.omeron.ui.common

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R

// Puts a fixed gap between grid tiles but none at the screen edges. Each tile gives up a share of
// the gap by column so that every column ends up the same width.
class FilmstripGapItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val gap = context.resources.getDimensionPixelSize(R.dimen.filmstrip_tile_gap)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val layoutManager = parent.layoutManager as? GridLayoutManager ?: return
        val layoutParams = view.layoutParams as GridLayoutManager.LayoutParams
        val spanCount = layoutManager.spanCount
        val column = layoutParams.spanIndex
        val columnsSpanned = layoutParams.spanSize

        outRect.left = column * gap / spanCount
        outRect.right = gap - (column + columnsSpanned) * gap / spanCount
        outRect.bottom = gap
    }
}
