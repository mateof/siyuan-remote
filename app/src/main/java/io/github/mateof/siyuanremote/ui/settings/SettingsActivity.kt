// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.siyuanremote.BuildConfig
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.notification.CaptureNotifications
import io.github.mateof.siyuanremote.ui.lock.AppLock
import io.github.mateof.siyuanremote.ui.theme.SiyuanRemoteTheme
import io.github.mateof.siyuanremote.update.UpdateCheckWorker
import io.github.mateof.siyuanremote.update.UpdateDialog
import io.github.mateof.siyuanremote.update.UpdateViewModel
import io.github.mateof.siyuanremote.util.WebViewInfo

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    /** Which setting asked for POST_NOTIFICATIONS, so the answer lands there. */
    private enum class PermissionReason { CAPTURE, UPDATES }

    private var permissionReason: PermissionReason? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val reason = permissionReason
            permissionReason = null
            when (reason) {
                PermissionReason.CAPTURE ->
                    if (granted) CaptureNotifications.show(applicationContext)
                    else viewModel.setCaptureNotification(false)

                // Update checks still work without the permission; only the
                // notification is lost, so the setting stays on.
                PermissionReason.UPDATES, null -> Unit
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Arriving from the update notification: check straight away and show
        // the release notes on top, rather than making the user hunt for the
        // section at the bottom of a long settings list.
        val fromUpdateNotification = intent.getBooleanExtra(EXTRA_SCROLL_TO_UPDATES, false)
        if (fromUpdateNotification) updateViewModel.check(force = true)

        setContent {
            SiyuanRemoteTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
                val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
                val updateState by updateViewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(signedOut) { if (signedOut) finish() }

                if (fromUpdateNotification && updateState.available != null && !updateState.dismissed) {
                    UpdateDialog(
                        state = updateState,
                        onDownload = updateViewModel::download,
                        onInstall = ::installUpdate,
                        onSkip = updateViewModel::skipCurrent,
                        onDismiss = updateViewModel::dismiss,
                    )
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    val current = settings
                    if (current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        SettingsScreen(
                            settings = current,
                            notebooks = notebooks,
                            appVersion = BuildConfig.VERSION_NAME,
                            webViewInfo = remember { WebViewInfo.of(this@SettingsActivity) },
                            biometricsAvailable = remember { AppLock.isAvailable(this@SettingsActivity) },
                            updateState = updateState,
                            callbacks = SettingsCallbacks(
                                onBack = { finish() },
                                onServerChanged = viewModel::setServer,
                                onSignOut = viewModel::signOut,
                                onAppLockChanged = viewModel::setAppLock,
                                onAppLockGraceChanged = viewModel::setAppLockGrace,
                                onQuickButtonChanged = viewModel::setQuickButton,
                                onForceDesktopChanged = viewModel::setForceDesktopUi,
                                onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                                onTextZoomChanged = viewModel::setTextZoom,
                                onExternalLinksChanged = viewModel::setOpenExternalLinks,
                                onSyncOnResumeChanged = viewModel::setSyncOnResume,
                                onMixedContentChanged = viewModel::setAllowMixedContent,
                                onNativeClipboardChanged = viewModel::setNativeClipboard,
                                onCaptureAsTaskChanged = viewModel::setCaptureAsTask,
                                onCaptureTimestampChanged = viewModel::setCaptureTimestamp,
                                onCaptureNotificationChanged = ::onCaptureNotificationChanged,
                                onNotebookSelected = viewModel::setCaptureNotebook,
                                onLoadNotebooks = viewModel::loadNotebooks,
                                onUpdateChecksChanged = ::onUpdateChecksChanged,
                                onCheckUpdates = { updateViewModel.check(force = true) },
                                onDownloadUpdate = updateViewModel::download,
                                onInstallUpdate = ::installUpdate,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun onUpdateChecksChanged(enabled: Boolean) {
        viewModel.setUpdateChecks(enabled)
        if (enabled) {
            UpdateCheckWorker.schedule(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionReason = PermissionReason.UPDATES
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            UpdateCheckWorker.cancel(applicationContext)
        }
    }

    private fun installUpdate() {
        val apk = updateViewModel.state.value.downloaded ?: return
        if (!updateViewModel.canInstall()) {
            Toast.makeText(this, getString(R.string.update_allow_unknown_sources), Toast.LENGTH_LONG).show()
            runCatching { startActivity(updateViewModel.unknownSourcesIntent()) }
            return
        }
        runCatching { startActivity(updateViewModel.installIntent(apk)) }
            .onFailure {
                Toast.makeText(this, getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
            }
    }

    private fun onCaptureNotificationChanged(enabled: Boolean) {
        viewModel.setCaptureNotification(enabled)
        if (!enabled) {
            CaptureNotifications.hide(applicationContext)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionReason = PermissionReason.CAPTURE
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            CaptureNotifications.show(applicationContext)
        }
    }

    companion object {
        /** Set by the update notification so the release notes open on top. */
        const val EXTRA_SCROLL_TO_UPDATES = "io.github.mateof.siyuanremote.extra.SCROLL_TO_UPDATES"
    }
}
