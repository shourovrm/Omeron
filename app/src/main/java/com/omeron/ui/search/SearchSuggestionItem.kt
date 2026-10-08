package com.omeron.ui.search

import androidx.annotation.StringRes
import com.omeron.R

/** One row of the list shown on the search screen before a query is searched. */
sealed interface SearchSuggestionItem {

    data class SectionHeader(@StringRes val titleRes: Int) : SearchSuggestionItem

    data class RecentQuery(val query: String) : SearchSuggestionItem

    data class Community(val name: String) : SearchSuggestionItem
}

/** Recent queries, then the communities that match the typed text; an empty section is left out. */
fun buildSearchSuggestions(
    recentQueries: List<String>,
    communityNames: List<String>
): List<SearchSuggestionItem> {
    val items = mutableListOf<SearchSuggestionItem>()

    if (recentQueries.isNotEmpty()) {
        items.add(SearchSuggestionItem.SectionHeader(R.string.search_recent_section))
        recentQueries.mapTo(items) { SearchSuggestionItem.RecentQuery(it) }
    }

    if (communityNames.isNotEmpty()) {
        items.add(SearchSuggestionItem.SectionHeader(R.string.search_communities_section))
        communityNames.mapTo(items) { SearchSuggestionItem.Community(it) }
    }

    return items
}
