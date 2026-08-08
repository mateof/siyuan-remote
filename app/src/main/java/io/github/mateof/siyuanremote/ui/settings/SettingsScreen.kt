package io.github.mateof.siyuanremote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.data.AppSettings
import io.github.mateof.siyuanremote.network.Notebook
import io.github.mateof.siyuanremote.update.UpdateSection
import io.github.mateof.siyuanremote.update.UpdateUiState
import io.github.mateof.siyuanremote.util.WebViewInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    notebooks: NotebookListState,
    appVersion: String,
    webViewInfo: WebViewInfo,
    biometricsAvailable: Boolean,
    updateState: UpdateUiState,
    callbacks: SettingsCallbacks,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_server)) }
            item { ServerSection(settings, callbacks) }
            item {
                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            }
            item {
                Caption(stringResource(R.string.settings_session_explainer))
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_security)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = if (biometricsAvailable) {
                        stringResource(R.string.settings_app_lock_summary)
                    } else {
                        stringResource(R.string.settings_app_lock_unavailable)
                    },
                    checked = settings.appLockEnabled && biometricsAvailable,
                    enabled = biometricsAvailable,
                    onCheckedChange = callbacks.onAppLockChanged,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_capture)) }
            item { NotebookSelector(settings, notebooks, callbacks) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_capture_as_task),
                    subtitle = stringResource(R.string.settings_capture_as_task_summary),
                    checked = settings.captureAsTask,
                    onCheckedChange = callbacks.onCaptureAsTaskChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_capture_timestamp),
                    subtitle = stringResource(R.string.settings_capture_timestamp_summary),
                    checked = settings.captureTimestamp,
                    onCheckedChange = callbacks.onCaptureTimestampChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_capture_notification),
                    subtitle = stringResource(R.string.settings_capture_notification_summary),
                    checked = settings.captureNotificationEnabled,
                    onCheckedChange = callbacks.onCaptureNotificationChanged,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_display)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_quick_button),
                    subtitle = stringResource(R.string.settings_quick_button_summary),
                    checked = settings.showQuickButton,
                    onCheckedChange = callbacks.onQuickButtonChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_force_desktop),
                    subtitle = stringResource(R.string.settings_force_desktop_summary),
                    checked = settings.forceDesktopUi,
                    onCheckedChange = callbacks.onForceDesktopChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    subtitle = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = settings.keepScreenOn,
                    onCheckedChange = callbacks.onKeepScreenOnChanged,
                )
            }
            item {
                TextZoomRow(settings.textZoom, callbacks.onTextZoomChanged)
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_advanced)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_external_links),
                    subtitle = stringResource(R.string.settings_external_links_summary),
                    checked = settings.openExternalLinksInBrowser,
                    onCheckedChange = callbacks.onExternalLinksChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_sync_on_resume),
                    subtitle = stringResource(R.string.settings_sync_on_resume_summary),
                    checked = settings.syncOnResume,
                    onCheckedChange = callbacks.onSyncOnResumeChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_mixed_content),
                    subtitle = stringResource(R.string.settings_mixed_content_summary),
                    checked = settings.allowMixedContent,
                    onCheckedChange = callbacks.onMixedContentChanged,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_updates)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_update_checks),
                    subtitle = stringResource(R.string.settings_update_checks_summary),
                    checked = settings.updateChecksEnabled,
                    onCheckedChange = callbacks.onUpdateChecksChanged,
                )
                UpdateSection(
                    state = updateState,
                    onCheck = callbacks.onCheckUpdates,
                    onDownload = callbacks.onDownloadUpdate,
                    onInstall = callbacks.onInstallUpdate,
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                Caption(stringResource(R.string.settings_app_version, appVersion))
                Caption(
                    stringResource(
                        R.string.settings_webview_version,
                        webViewInfo.versionName ?: stringResource(R.string.settings_webview_unknown),
                    ),
                )
                if (webViewInfo.isOutdated) {
                    Caption(stringResource(R.string.settings_webview_outdated_hint))
                }
                Caption(stringResource(R.string.settings_disclaimer))
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    callbacks.onSignOut()
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerSection(settings: AppSettings, callbacks: SettingsCallbacks) {
    var primary by remember(settings.primaryUrl) { mutableStateOf(settings.primaryUrl) }
    var fallback by remember(settings.fallbackUrl) { mutableStateOf(settings.fallbackUrl) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = primary,
            onValueChange = { primary = it },
            label = { Text(stringResource(R.string.setup_primary_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fallback,
            onValueChange = { fallback = it },
            label = { Text(stringResource(R.string.setup_fallback_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { callbacks.onServerChanged(primary, fallback) },
            enabled = primary != settings.primaryUrl || fallback != settings.fallbackUrl,
        ) {
            Text(stringResource(R.string.settings_save_server))
        }
        if (settings.lastGoodUrl.isNotBlank()) {
            Caption(stringResource(R.string.settings_active_url, settings.lastGoodUrl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookSelector(
    settings: AppSettings,
    notebooks: NotebookListState,
    callbacks: SettingsCallbacks,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_capture_notebook),
            style = MaterialTheme.typography.bodyLarge,
        )
        Box {
            TextButton(onClick = {
                expanded = true
                callbacks.onLoadNotebooks()
            }) {
                Text(
                    text = settings.captureNotebookName.ifBlank {
                        stringResource(R.string.settings_capture_notebook_first)
                    },
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_capture_notebook_first)) },
                    onClick = {
                        expanded = false
                        callbacks.onNotebookSelected(null)
                    },
                )
                when (notebooks) {
                    NotebookListState.Idle, NotebookListState.Loading -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_loading)) },
                        onClick = { },
                    )

                    is NotebookListState.Failed -> DropdownMenuItem(
                        text = { Text(notebooks.message) },
                        onClick = { expanded = false },
                    )

                    is NotebookListState.Loaded -> notebooks.notebooks.forEach { notebook ->
                        DropdownMenuItem(
                            text = { Text(notebook.name) },
                            onClick = {
                                expanded = false
                                callbacks.onNotebookSelected(notebook)
                            },
                        )
                    }
                }
            }
        }
        Caption(stringResource(R.string.settings_capture_notebook_summary))
    }
}

@Composable
private fun TextZoomRow(zoom: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_text_zoom, zoom),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = zoom.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 70f..150f,
            steps = 7,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Grouped so the screen signature stays readable as options grow. */
data class SettingsCallbacks(
    val onBack: () -> Unit,
    val onServerChanged: (String, String) -> Unit,
    val onSignOut: () -> Unit,
    val onAppLockChanged: (Boolean) -> Unit,
    val onQuickButtonChanged: (Boolean) -> Unit,
    val onForceDesktopChanged: (Boolean) -> Unit,
    val onKeepScreenOnChanged: (Boolean) -> Unit,
    val onTextZoomChanged: (Int) -> Unit,
    val onExternalLinksChanged: (Boolean) -> Unit,
    val onSyncOnResumeChanged: (Boolean) -> Unit,
    val onMixedContentChanged: (Boolean) -> Unit,
    val onCaptureAsTaskChanged: (Boolean) -> Unit,
    val onCaptureTimestampChanged: (Boolean) -> Unit,
    val onCaptureNotificationChanged: (Boolean) -> Unit,
    val onNotebookSelected: (Notebook?) -> Unit,
    val onLoadNotebooks: () -> Unit,
    val onUpdateChecksChanged: (Boolean) -> Unit,
    val onCheckUpdates: () -> Unit,
    val onDownloadUpdate: () -> Unit,
    val onInstallUpdate: () -> Unit,
)
