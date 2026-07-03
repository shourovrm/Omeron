package com.omeron.ui.common

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
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
}
