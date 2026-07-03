package com.omeron.util

object RedditUtil {

    fun joinSubredditList(subreddits: List<String>): String {
        return subreddits.joinToString("+")
    }
}
