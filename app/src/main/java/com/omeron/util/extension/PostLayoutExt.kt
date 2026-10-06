package com.omeron.util.extension

import android.content.Context
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.omeron.R
import com.omeron.data.model.db.PostEntity
import com.omeron.data.model.preferences.PostLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// Shared so the layout mapping lives in one place instead of being duplicated (and needing an
// exhaustive `when`) in every post-list fragment.

const val FILMSTRIP_SPAN_COUNT = 3

fun PostLayout.iconRes(): Int = when (this) {
    PostLayout.CARD -> R.drawable.ic_layout_card
    PostLayout.GALLERY -> R.drawable.ic_layout_gallery
    PostLayout.COMPACT -> R.drawable.ic_layout_compact
    PostLayout.FILMSTRIP -> R.drawable.ic_layout_filmstrip
}

// Names the layout that is showing now, because the toggle button's icon shows the same thing.
fun PostLayout.toggleDescriptionRes(): Int = when (this) {
    PostLayout.CARD -> R.string.layout_toggle_card
    PostLayout.GALLERY -> R.string.layout_toggle_gallery
    PostLayout.COMPACT -> R.string.layout_toggle_compact
    PostLayout.FILMSTRIP -> R.string.layout_toggle_filmstrip
}

// postCount is read lazily because the list keeps growing as pages load. The load-state footer
// sits right after the last post, so every position past the posts spans the full row; without
// that the retry/progress footer would be squeezed into a single square tile.
fun PostLayout.layoutManager(
    context: Context,
    postCount: () -> Int
): RecyclerView.LayoutManager = when (this) {
    PostLayout.GALLERY -> StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
    // Compact is a full-width row like card, just denser.
    PostLayout.CARD, PostLayout.COMPACT -> LinearLayoutManager(context)
    PostLayout.FILMSTRIP -> GridLayoutManager(context, FILMSTRIP_SPAN_COUNT).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position < postCount()) 1 else FILMSTRIP_SPAN_COUNT
            }
        }
    }
}

// The filter sits downstream of the cached paging flow, so switching layouts re-filters the
// pages already in memory instead of making the repository fetch them again.
fun Flow<PagingData<PostEntity>>.filteredForLayout(
    layout: Flow<PostLayout>
): Flow<PagingData<PostEntity>> = combine(this, layout) { posts, postLayout ->
    if (postLayout == PostLayout.FILMSTRIP) {
        posts.filter { it.hasFilmstripMedia() }
    } else {
        posts
    }
}
