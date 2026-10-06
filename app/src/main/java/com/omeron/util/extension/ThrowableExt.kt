package com.omeron.util.extension

import com.omeron.data.remote.api.reddit.RedditCookieInterceptor.LoginRequiredException

/**
 * True when this error, or any error in its cause chain, is the login wall signalled by
 * [com.omeron.data.remote.api.reddit.RedditCookieInterceptor]. Retrofit and the scrapers may
 * wrap the original exception, so only checking the top-level type would miss it.
 */
val Throwable.isLoginRequired: Boolean
    get() = generateSequence(this) { it.cause }.any { it is LoginRequiredException }
