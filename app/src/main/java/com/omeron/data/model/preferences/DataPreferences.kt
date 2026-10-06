package com.omeron.data.model.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

data class DataPreferences(
    val redditSource: Int,
    val redditSourceInstance: String,
    val enablePrivacyEnhancer: Boolean
) {
    object PreferencesKeys {
        val REDDIT_SOURCE = intPreferencesKey("reddit_source")
        val REDDIT_SOURCE_INSTANCE = stringPreferencesKey("reddit_source_instance")
        val PRIVACY_ENHANCER = booleanPreferencesKey("privacy_enhancer")
        val POST_LAYOUT = intPreferencesKey("post_layout")
        val REDDIT_COOKIES = stringPreferencesKey("reddit_cookies")
    }

    enum class RedditSource(val value: Int) {
        REDDIT(0), TEDDIT(1), REDDIT_SCRAP(2);

        companion object {
            fun fromValue(value: Int): RedditSource = values().find { it.value == value } ?: REDDIT
        }
    }
}

enum class PostLayout(val value: Int) {
    CARD(0), GALLERY(1), COMPACT(2), FILMSTRIP(3);

    // Toggle cycles CARD -> GALLERY -> COMPACT -> FILMSTRIP -> CARD. The persisted values stay
    // fixed (they are stored in DataStore), so the cycle order follows declaration order only.
    fun next(): PostLayout = values()[(ordinal + 1) % values().size]

    // Screens that show posts of every kind (search results, user posts) have no media-only
    // grid, so they show the closest tile layout instead and skip the grid when toggling.
    fun withoutFilmstrip(): PostLayout = if (this == FILMSTRIP) GALLERY else this

    fun nextWithoutFilmstrip(): PostLayout {
        val following = withoutFilmstrip().next()
        return if (following == FILMSTRIP) following.next() else following
    }

    companion object {
        fun fromValue(value: Int): PostLayout = values().find { it.value == value } ?: CARD

        // ponytail: per-subreddit override wins over the global default; both fall back to CARD.
        // Kept as a pure function so it's unit-testable without a DataStore.
        fun resolve(subredditOverride: Int?, globalValue: Int?): PostLayout =
            fromValue(subredditOverride ?: globalValue ?: CARD.value)
    }
}
