// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.util

import android.content.Context
import androidx.webkit.WebViewCompat

/**
 * SiYuan only supports Chromium-based engines. Android System WebView *is*
 * Chromium, so the app is compatible by construction, but an outdated or
 * disabled WebView package still produces a broken editor. Checking the version
 * up front turns "the app is blank" into an actionable message.
 */
data class WebViewInfo(
    val packageName: String?,
    val versionName: String?,
) {
    val majorVersion: Int? = versionName
        ?.substringBefore('.')
        ?.toIntOrNull()

    val isMissing: Boolean get() = packageName == null

    val isOutdated: Boolean get() = majorVersion != null && majorVersion < MIN_RECOMMENDED_MAJOR

    companion object {
        /**
         * Chromium 108 (Dec 2022) is the floor at which the SiYuan frontend
         * renders correctly in practice. Below that the editor loads but
         * layout and clipboard behaviour degrade.
         */
        const val MIN_RECOMMENDED_MAJOR = 108

        fun of(context: Context): WebViewInfo {
            val pkg = WebViewCompat.getCurrentWebViewPackage(context)
            return WebViewInfo(packageName = pkg?.packageName, versionName = pkg?.versionName)
        }
    }
}
