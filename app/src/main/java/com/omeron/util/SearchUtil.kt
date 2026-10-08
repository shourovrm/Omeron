package com.omeron.util

object SearchUtil {
    const val QUERY_MIN_LENGTH = 3
    private const val QUERY_MAX_LENGTH = 20

    const val RECENT_QUERIES_LIMIT = 10

    fun isQueryValid(query: String): Boolean {
        return query.length >= QUERY_MIN_LENGTH
    }

    /**
     * The recent queries after [query] was searched: newest first, without an earlier copy of the
     * same query (compared ignoring case, so "Kotlin" replaces "kotlin"), at most
     * [RECENT_QUERIES_LIMIT] long.
     */
    fun withRecentQuery(recentQueries: List<String>, query: String): List<String> {
        val trimmedQuery = query.trim()
        val earlierQueries = recentQueries.filterNot { it.equals(trimmedQuery, ignoreCase = true) }
        return (listOf(trimmedQuery) + earlierQueries).take(RECENT_QUERIES_LIMIT)
    }

    /** The communities whose name contains [typedText], ignoring case, in alphabetical order. */
    fun filterCommunityNames(communityNames: List<String>, typedText: String): List<String> {
        val searchedText = typedText.trim()
        return communityNames
            .filter { it.contains(searchedText, ignoreCase = true) }
            .sortedBy { it.lowercase() }
    }
}
