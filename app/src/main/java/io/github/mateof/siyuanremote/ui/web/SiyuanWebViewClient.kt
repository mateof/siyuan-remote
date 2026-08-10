// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.mateof.siyuanremote.util.isSameOrigin

/**
 * Keeps navigation inside the app when it belongs to our kernel, hands
 * everything else to the system, and turns the kernel's `/check-auth` redirect
 * into a silent re-login instead of a login form the user has to fill in again.
 */
class SiyuanWebViewClient(
    private val baseUrl: () -> String,
    /** Invoked with the path the kernel wanted us to reach after authenticating. */
    private val onSessionExpired: (targetPath: String) -> Unit,
    /** Returns true when the app took the URL over and the WebView must not load it. */
    private val onExternalUrl: (Uri) -> Boolean,
    private val onStateChanged: (WebPageState) -> Unit,
    /** Lets the clipboard bridge know which page it is serving. */
    private val onPageUrlChanged: (String) -> Unit = {},
) : WebViewClient() {

    private var lastAuthAttemptAt = 0L
    private var consecutiveAuthAttempts = 0

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        val base = baseUrl()

        if (base.isNotEmpty() && isSameOrigin(url, base)) {
            return handleCheckAuth(url)
        }

        // Non-http schemes (mailto:, tel:, intent:, geo:...) and links to other
        // hosts leave the app, unless the user asked to keep browsing in place.
        return onExternalUrl(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        onPageUrlChanged(url)
        onStateChanged(WebPageState.Loading)
        // A server-side 302 to /check-auth can reach the main frame without
        // passing through shouldOverrideUrlLoading on some WebView versions.
        handleCheckAuth(Uri.parse(url))
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        onPageUrlChanged(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageUrlChanged(url)
        if (!Uri.parse(url).path.orEmpty().startsWith(CHECK_AUTH_PATH)) {
            consecutiveAuthAttempts = 0
            onStateChanged(WebPageState.Ready)
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        Log.w(TAG, "Main frame error ${error.errorCode} for ${request.url}")
        onStateChanged(WebPageState.Failed(error.description?.toString().orEmpty()))
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!request.isForMainFrame) return
        // 401 is expected while the cookie is being renewed, so it is not an error.
        if (errorResponse.statusCode == HTTP_UNAUTHORIZED) return
        onStateChanged(WebPageState.Failed("HTTP ${errorResponse.statusCode}"))
    }

    /** Returns true when the navigation was swallowed by the re-login flow. */
    private fun handleCheckAuth(url: Uri): Boolean {
        if (url.path.orEmpty() != CHECK_AUTH_PATH) return false

        val now = SystemClock.elapsedRealtime()
        if (now - lastAuthAttemptAt < AUTH_DEBOUNCE_MS) return true
        lastAuthAttemptAt = now

        consecutiveAuthAttempts++
        if (consecutiveAuthAttempts > MAX_AUTH_ATTEMPTS) {
            onStateChanged(WebPageState.SessionRejected)
            return true
        }

        onSessionExpired(url.getQueryParameter("to") ?: "/")
        return true
    }

    private companion object {
        const val TAG = "SiyuanWebViewClient"
        const val CHECK_AUTH_PATH = "/check-auth"
        const val HTTP_UNAUTHORIZED = 401
        const val AUTH_DEBOUNCE_MS = 1_500L

        /** Stops a redirect loop when the stored code is no longer accepted. */
        const val MAX_AUTH_ATTEMPTS = 3
    }
}

sealed interface WebPageState {
    data object Loading : WebPageState
    data object Ready : WebPageState
    data class Failed(val reason: String) : WebPageState

    /** The stored access code no longer works: the user must re-enter it. */
    data object SessionRejected : WebPageState
}
