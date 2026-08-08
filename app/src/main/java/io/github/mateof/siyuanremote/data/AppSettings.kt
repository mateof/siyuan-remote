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
    val keepScreenOn: Boolean = false,
    /** Floating in-app button that opens settings, reload, capture and sync. */
    val showQuickButton: Boolean = true,
    val openExternalLinksInBrowser: Boolean = true,
    val forceDesktopUi: Boolean = false,
    val allowMixedContent: Boolean = false,
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

    /** Candidate base URLs in the order they should be probed. */
    val candidateUrls: List<String>
        get() = listOf(lastGoodUrl, primaryUrl, fallbackUrl)
            .filter { it.isNotBlank() }
            .distinct()
}
