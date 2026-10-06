package com.omeron.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.omeron.ui.common.FilmstripGapItemDecoration
import com.omeron.ui.common.PostDividerItemDecoration

class PostRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        addItemDecoration(PostDividerItemDecoration(context))
        addItemDecoration(FilmstripGapItemDecoration(context))
        isVerticalScrollBarEnabled = false
    }
}
