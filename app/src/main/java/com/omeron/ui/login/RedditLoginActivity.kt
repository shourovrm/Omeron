package com.omeron.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.omeron.data.repository.PreferencesRepository
import com.omeron.util.LinkUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lets the user log in to reddit inside a WebView, then captures the session cookie
 * (reddit_session is HttpOnly, but CookieManager still exposes it) for the scrapers.
 * No API, no OAuth: the app only reuses the same cookies a browser would send.
 */
@AndroidEntryPoint
class RedditLoginActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private lateinit var webView: WebView
    private var done = false
    private var verifying = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = LinkUtil.USER_AGENT
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = onPage(url)
            // Login is an XHR + client-side redirect, so also poll on subresource loads.
            override fun onLoadResource(view: WebView, url: String) = onPage(view.url ?: url)
        }
        webView.loadUrl(LOGIN_URL)
    }

    private fun onPage(url: String) {
        if (done) return
        val cookies = CookieManager.getInstance().getCookie(COOKIE_URL) ?: return
        if (!cookies.contains(SESSION_COOKIE)) return
        if (url.contains("/login")) {
            // Session cookie just appeared: let old.reddit itself confirm it before capturing,
            // so we store the finalized cookie set rather than a mid-login snapshot.
            if (!verifying) {
                verifying = true
                webView.loadUrl(VERIFY_URL)
            }
            return
        }
        if (!url.startsWith(COOKIE_URL)) return
        // Only reddit_session: sending the WebView's other cookies (stale anonymous token_v2,
        // loid, ...) alongside it makes reddit bounce back to the login wall.
        val session = cookies.split(';').map { it.trim() }.first { it.startsWith(SESSION_COOKIE) }
        done = true
        lifecycleScope.launch {
            preferencesRepository.setRedditCookies(session)
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_URL = "https://old.reddit.com/login/"
        private const val COOKIE_URL = "https://old.reddit.com/"
        private const val VERIFY_URL = "https://old.reddit.com/r/popular/"
        private const val SESSION_COOKIE = "reddit_session="

        fun logout(preferencesRepository: PreferencesRepository, scope: kotlinx.coroutines.CoroutineScope) {
            CookieManager.getInstance().removeAllCookies(null)
            scope.launch { preferencesRepository.setRedditCookies("") }
        }
    }
}
