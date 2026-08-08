package io.github.mateof.siyuanremote.ui.web

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.github.mateof.siyuanremote.BuildConfig
import io.github.mateof.siyuanremote.data.AppSettings

/**
 * Turns a stock WebView into something the SiYuan frontend is happy running in.
 *
 * The kernel decides which UI to serve from the User-Agent
 * (`kernel/server/serve.go`), so the string must keep both the `Mozilla/`
 * prefix and the `Mobile` token. We only append an app marker after them, which
 * also makes our requests identifiable in the kernel log.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applySiyuanConfiguration(appSettings: AppSettings) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = false
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = false
        allowContentAccess = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        cacheMode = WebSettings.LOAD_DEFAULT
        textZoom = appSettings.textZoom
        mixedContentMode = if (appSettings.allowMixedContent) {
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        userAgentString = "$userAgentString SiYuanRemote/${BuildConfig.VERSION_NAME}"
    }

    // SiYuan ships its own light and dark themes. Letting the WebView darken the
    // page on top of them produces washed out colours, so it stays off.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
    }

    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applySiyuanConfiguration, true)
    }
}

/**
 * The kernel honours a `siyuan-desktop-mode` cookie on `/` before falling back
 * to User-Agent sniffing, which is how the app offers a "desktop UI" switch for
 * tablets and foldables.
 */
fun applyDesktopModeCookie(baseUrl: String, forceDesktop: Boolean) {
    CookieManager.getInstance().apply {
        setCookie(baseUrl, "siyuan-desktop-mode=$forceDesktop; Path=/")
        flush()
    }
}
