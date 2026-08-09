// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.capture

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.data.SettingsRepository
import io.github.mateof.siyuanremote.network.Notebook
import io.github.mateof.siyuanremote.network.SessionManager
import io.github.mateof.siyuanremote.network.SessionResult
import io.github.mateof.siyuanremote.network.SiyuanApi
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes into the knowledge base without opening the editor.
 *
 * Every entry point that is not the WebView (share sheet, launcher shortcut,
 * home screen widget, notification reply) goes through here, which is why it
 * establishes its own session instead of assuming one exists.
 */
@Singleton
class CaptureRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SiyuanApi,
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun notebooks(): Result<List<Notebook>> = runCatching {
        val baseUrl = requireSession()
        api.listNotebooks(baseUrl).filterNot { it.closed }
    }

    /**
     * Appends [text] (and any [attachments]) to today's daily note.
     *
     * @return the id of the created block, so the caller can offer to open it.
     */
    suspend fun capture(text: String, attachments: List<Uri> = emptyList()): Result<CaptureResult> =
        runCatching {
            val baseUrl = requireSession()
            val settings = settingsRepository.current()

            val notebookId = settings.captureNotebookId.ifBlank {
                api.listNotebooks(baseUrl).firstOrNull { !it.closed }?.id
                    ?: error(context.getString(R.string.capture_error_no_notebook))
            }

            val assetLinks = attachments.mapNotNull { uri ->
                val name = displayName(uri)
                api.uploadAsset(baseUrl, context.contentResolver, uri, name)?.let { path ->
                    "![$name]($path)"
                }
            }

            val markdown = buildMarkdown(
                text = text,
                assetLinks = assetLinks,
                asTask = settings.captureAsTask,
                withTimestamp = settings.captureTimestamp,
            )
            require(markdown.isNotBlank()) {
                context.getString(R.string.capture_error_empty)
            }

            val blockId = api.appendToDailyNote(baseUrl, notebookId, markdown)
            CaptureResult(blockId = blockId, notebookId = notebookId)
        }

    private suspend fun requireSession(): String = when (val result = sessionManager.prepare()) {
        is SessionResult.Ready -> result.baseUrl
        SessionResult.NotConfigured ->
            error(context.getString(R.string.capture_error_not_configured))

        is SessionResult.Unreachable ->
            error(context.getString(R.string.capture_error_unreachable))

        is SessionResult.AuthRequired ->
            error(context.getString(R.string.capture_error_auth))
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        return fromProvider ?: uri.lastPathSegment ?: "attachment"
    }

    private fun buildMarkdown(
        text: String,
        assetLinks: List<String>,
        asTask: Boolean,
        withTimestamp: Boolean,
    ): String {
        val trimmed = text.trim()
        val stamp = if (withTimestamp) " (${LocalTime.now().format(TIME_FORMAT)})" else ""
        val firstLine = when {
            trimmed.isEmpty() -> ""
            asTask -> "- [ ] $trimmed$stamp"
            else -> "$trimmed$stamp"
        }
        return (listOf(firstLine) + assetLinks)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class CaptureResult(val blockId: String?, val notebookId: String)
