package io.github.mateof.siyuanremote.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes OkHttp and the WebView share one cookie store.
 *
 * This is what lets the app log in over HTTP (silently, in the background) and
 * have the WebView instantly consider itself authenticated, and vice versa.
 * Without it we would need two independent sessions against the same kernel.
 */
@Singleton
class WebViewCookieJar @Inject constructor() : CookieJar {

    private val cookieManager: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val target = url.toString()
        cookies.forEach { cookieManager.setCookie(target, it.toString()) }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    /** Drops every cookie for every host. Used on logout. */
    fun clear() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }
}
