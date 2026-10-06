package com.omeron.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R

class PostDividerItemDecoration(
    context: Context,
    orientation: Int = VERTICAL
) : DividerItemDecoration(context, orientation) {

    init {
        ContextCompat.getDrawable(context, R.drawable.post_divider)?.let {
            setDrawable(it)
        }
    }

    // The filmstrip grid separates tiles with its own gap, so no divider lines there.
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (parent.layoutManager is GridLayoutManager) return
        super.getItemOffsets(outRect, view, parent, state)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager is GridLayoutManager) return
        super.onDraw(canvas, parent, state)
    }
}
