package com.omeron.data.model

import com.omeron.data.model.preferences.ContentPreferences

sealed class Data {

    data class Fetch(val query: String, val sorting: Sorting) : Data()

    data class FetchMultiple(val query: List<String>, val sorting: Sorting) : Data()

    data class User(
        val history: List<String>,
        val saved: List<String>,
        val contentPreferences: ContentPreferences,
        val savedComments: List<String>? = null
    ) : Data()
}
