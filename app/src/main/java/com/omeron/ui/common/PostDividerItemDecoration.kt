package com.omeron.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omeron.R
import com.omeron.ui.postlist.PostViewHolder

/**
 * Separates the rows of a vertical list. Posts are separated by empty space, with no line; any
 * other row (a comment in the user's comment list) keeps the 1dp divider line. Grid and staggered
 * lists place their own tiles, so they get nothing here.
 */
class PostDividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val postGap = context.resources.getDimensionPixelSize(R.dimen.space_16)

    private val divider: Drawable = checkNotNull(
        ContextCompat.getDrawable(context, R.drawable.post_divider)
    )

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (parent.layoutManager !is LinearLayoutManager) return
        outRect.bottom = if (isPostRow(view, parent)) postGap else divider.intrinsicHeight
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager !is LinearLayoutManager) return

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (isPostRow(child, parent)) continue

            val top = child.bottom + child.translationY.toInt()
            divider.setBounds(
                parent.paddingLeft,
                top,
                parent.width - parent.paddingRight,
                top + divider.intrinsicHeight
            )
            divider.draw(canvas)
        }
    }

    // Every holder the post lists create is PostViewHolder or one of its nested row holders.
    private fun isPostRow(view: View, parent: RecyclerView): Boolean {
        val holder = parent.getChildViewHolder(view)
        return holder is PostViewHolder || holder.javaClass.enclosingClass == PostViewHolder::class.java
    }
}
