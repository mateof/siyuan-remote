// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateof.siyuanremote.BuildConfig
import io.github.mateof.siyuanremote.R

/**
 * Shown once when the app opens and a newer release is waiting. Dismissable,
 * and "Skip this version" makes it stay quiet until the next one.
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = state.available ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title, release.version)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_installed_version, state.installedVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (release.notes.isNotBlank()) {
                    Text(text = release.notes.trim(), style = MaterialTheme.typography.bodySmall)
                }
                UpdateProgress(state)
                if (BuildConfig.DEBUG) {
                    Text(
                        text = stringResource(R.string.update_debug_signature_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when {
                state.downloaded != null -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_install))
                }

                else -> TextButton(onClick = onDownload, enabled = !state.isDownloading) {
                    Text(stringResource(R.string.update_download))
                }
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.update_skip)) }
            }
        },
    )
}

/** The same information, permanently reachable from Settings. */
@Composable
fun UpdateSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val release = state.available
        when {
            release != null -> {
                Text(
                    text = stringResource(R.string.update_available_title, release.version),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (release.notes.isNotBlank()) {
                    Text(
                        text = release.notes.trim().take(NOTES_PREVIEW),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.upToDate -> Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        UpdateProgress(state)

        when {
            state.downloaded != null -> OutlinedButton(onClick = onInstall) {
                Text(stringResource(R.string.update_install))
            }

            release != null -> OutlinedButton(onClick = onDownload, enabled = !state.isDownloading) {
                Text(stringResource(R.string.update_download))
            }

            else -> OutlinedButton(onClick = onCheck, enabled = !state.isChecking) {
                if (state.isChecking) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.update_check_now))
                }
            }
        }
    }
}

@Composable
private fun UpdateProgress(state: UpdateUiState) {
    if (state.isDownloading) {
        val fraction = state.progress
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.update_downloading, (fraction * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (state.downloadFailed) {
        Text(
            text = stringResource(R.string.update_download_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private const val NOTES_PREVIEW = 500
