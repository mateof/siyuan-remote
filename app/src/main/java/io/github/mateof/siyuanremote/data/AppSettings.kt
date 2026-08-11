// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.data

/**
 * Everything the user can configure, in one immutable snapshot.
 *
 * The access authorization code is deliberately absent: it lives encrypted in
 * [SecretStore] and never travels through DataStore in plain text.
 */
data class AppSettings(
    /** Preferred base URL, e.g. `http://192.168.1.50:6806`. Never ends with `/`. */
    val primaryUrl: String = "",
    /** Optional second base URL tried when [primaryUrl] is unreachable (VPN, reverse proxy...). */
    val fallbackUrl: String = "",
    /** Base URL that answered last time, so the next cold start goes straight to it. */
    val lastGoodUrl: String = "",

    val appLockEnabled: Boolean = true,
    /**
     * How long the app may sit in the background before the lock re-arms.
     * Zero locks on every return; [AppSettings.GRACE_ONLY_ON_START] locks only
     * when the process starts, which is the loosest setting on offer.
     */
    val appLockGraceMinutes: Int = 30,
    val keepScreenOn: Boolean = false,
    /** Floating in-app button that opens settings, reload, capture and sync. */
    val showQuickButton: Boolean = true,
    /**
     * Where the floating button sits, as a fraction of the free space on each
     * axis. Fractions rather than pixels so it lands in the same relative spot
     * after a rotation or on a different screen. 1,1 is the bottom end corner.
     */
    val quickButtonX: Float = 1f,
    val quickButtonY: Float = 1f,
    val openExternalLinksInBrowser: Boolean = true,
    val forceDesktopUi: Boolean = false,
    val allowMixedContent: Boolean = false,
    /**
     * Supply `navigator.clipboard.read` from the Android clipboard, since a
     * WebView cannot grant the permission the real one needs.
     */
    val nativeClipboard: Boolean = true,
    val textZoom: Int = 100,

    val captureNotificationEnabled: Boolean = false,
    val syncOnResume: Boolean = false,

    /** Look for newer releases on GitHub and notify about them. */
    val updateChecksEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    /** Version the user chose to be left alone about. */
    val skippedUpdateVersion: String = "",

    /** Notebook that quick capture appends to. Empty means "ask every time". */
    val captureNotebookId: String = "",
    val captureNotebookName: String = "",
    /** Prepend a `- [ ] ` task marker to captured text. */
    val captureAsTask: Boolean = false,
    /** Append a `(HH:mm)` stamp to every captured block. */
    val captureTimestamp: Boolean = true,
) {
    val isConfigured: Boolean get() = primaryUrl.isNotBlank()

    /** Grace period in milliseconds, or null when the lock should never re-arm. */
    val appLockGraceMillis: Long?
        get() = if (appLockGraceMinutes == GRACE_ONLY_ON_START) {
            null
        } else {
            appLockGraceMinutes.coerceAtLeast(0) * 60_000L
        }

    companion object {
        const val GRACE_ONLY_ON_START = -1
        val GRACE_CHOICES = listOf(0, 1, 5, 15, 30, 60, GRACE_ONLY_ON_START)
    }

    /** Candidate base URLs in the order they should be probed. */
    val candidateUrls: List<String>
        get() = listOf(lastGoodUrl, primaryUrl, fallbackUrl)
            .filter { it.isNotBlank() }
            .distinct()
}
