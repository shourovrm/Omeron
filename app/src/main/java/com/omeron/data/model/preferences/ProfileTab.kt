package com.omeron.data.model.preferences

/**
 * A tab of the profile screen that has its own layout choice. Each tab offers two layouts out of
 * [PostLayout]: CARD is the full-size look and COMPACT the dense one. The declaration order is the
 * order of the tabs in the pager, so a pager position maps to a tab by ordinal.
 *
 * [storageName] is part of the DataStore key and must never change once released.
 */
enum class ProfileTab(val storageName: String, val defaultLayout: PostLayout) {
    SAVED_POSTS("saved_posts", PostLayout.CARD),
    SAVED_COMMENTS("saved_comments", PostLayout.CARD),
    // History started as compact rows, so that stays what a fresh install shows.
    HISTORY("history", PostLayout.COMPACT);

    /** The layout stored for this tab, or its default when nothing (or a layout it lacks) is stored. */
    fun layoutFromStoredValue(storedValue: Int?): PostLayout {
        val stored = storedValue?.let { PostLayout.fromValue(it) }
        return if (stored == PostLayout.CARD || stored == PostLayout.COMPACT) stored else defaultLayout
    }

    fun toggled(layout: PostLayout): PostLayout {
        return if (layout == PostLayout.COMPACT) PostLayout.CARD else PostLayout.COMPACT
    }

    companion object {
        fun fromPagerPosition(position: Int): ProfileTab = values()[position]
    }
}
