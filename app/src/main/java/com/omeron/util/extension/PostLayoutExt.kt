package com.omeron.util.extension

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.omeron.R
import com.omeron.data.model.preferences.PostLayout

// Shared so the CARD/GALLERY/COMPACT mapping lives in one place instead of being
// duplicated (and needing an exhaustive `when`) in every post-list fragment.

fun PostLayout.iconRes(): Int = when (this) {
    PostLayout.CARD -> R.drawable.ic_layout_card
    PostLayout.GALLERY -> R.drawable.ic_layout_gallery
    PostLayout.COMPACT -> R.drawable.ic_layout_compact
}

fun PostLayout.layoutManager(context: Context): RecyclerView.LayoutManager = when (this) {
    PostLayout.GALLERY -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
    // Compact is a full-width row like card, just denser.
    PostLayout.CARD, PostLayout.COMPACT -> LinearLayoutManager(context)
}
