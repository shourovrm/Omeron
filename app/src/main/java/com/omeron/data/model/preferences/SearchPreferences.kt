package com.omeron.data.model.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

object SearchPreferences {
    object PreferencesKeys {
        // Queries joined by a newline: the search field is a single line, so a query never has one.
        val RECENT_QUERIES = stringPreferencesKey("recent_search_queries")
    }
}
