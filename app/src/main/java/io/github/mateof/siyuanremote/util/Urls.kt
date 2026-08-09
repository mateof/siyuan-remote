// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.util

import android.net.Uri

/**
 * True when [candidate] points at the same kernel as [baseUrl]. Anything else
 * is an outbound link and must not stay inside the app WebView.
 */
fun isSameOrigin(candidate: Uri, baseUrl: String): Boolean {
    val base = Uri.parse(baseUrl)
    return candidate.scheme.equals(base.scheme, ignoreCase = true) &&
        candidate.host.equals(base.host, ignoreCase = true) &&
        candidate.effectivePort() == base.effectivePort()
}

private fun Uri.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    else -> 80
}

/** Resolves a possibly relative SiYuan path against the active base URL. */
fun absoluteUrl(baseUrl: String, pathOrUrl: String): String = when {
    pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") -> pathOrUrl
    pathOrUrl.startsWith("/") -> baseUrl + pathOrUrl
    else -> "$baseUrl/$pathOrUrl"
}

/** `siyuan://blocks/<id>`, the URI the SiYuan frontend understands natively. */
fun blockUri(blockId: String): String = "siyuan://blocks/$blockId"

fun blockIdFromSiyuanUri(uri: Uri): String? {
    if (!uri.scheme.equals("siyuan", ignoreCase = true)) return null
    if (uri.host != "blocks") return null
    return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
}
