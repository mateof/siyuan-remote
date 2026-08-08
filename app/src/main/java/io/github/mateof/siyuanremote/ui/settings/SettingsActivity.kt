package io.github.mateof.siyuanremote.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import io.github.mateof.siyuanremote.notification.CaptureNotifications
import io.github.mateof.siyuanremote.ui.lock.AppLock
import io.github.mateof.siyuanremote.ui.theme.SiyuanRemoteTheme
import io.github.mateof.siyuanremote.util.WebViewInfo

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                CaptureNotifications.show(applicationContext)
            } else {
                viewModel.setCaptureNotification(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SiyuanRemoteTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
                val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()

                LaunchedEffect(signedOut) { if (signedOut) finish() }

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
                            callbacks = SettingsCallbacks(
                                onBack = { finish() },
                                onServerChanged = viewModel::setServer,
                                onSignOut = viewModel::signOut,
                                onAppLockChanged = viewModel::setAppLock,
                                onQuickButtonChanged = viewModel::setQuickButton,
                                onForceDesktopChanged = viewModel::setForceDesktopUi,
                                onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                                onTextZoomChanged = viewModel::setTextZoom,
                                onExternalLinksChanged = viewModel::setOpenExternalLinks,
                                onSyncOnResumeChanged = viewModel::setSyncOnResume,
                                onMixedContentChanged = viewModel::setAllowMixedContent,
                                onCaptureAsTaskChanged = viewModel::setCaptureAsTask,
                                onCaptureTimestampChanged = viewModel::setCaptureTimestamp,
                                onCaptureNotificationChanged = ::onCaptureNotificationChanged,
                                onNotebookSelected = viewModel::setCaptureNotebook,
                                onLoadNotebooks = viewModel::loadNotebooks,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun onCaptureNotificationChanged(enabled: Boolean) {
        viewModel.setCaptureNotification(enabled)
        if (!enabled) {
            CaptureNotifications.hide(applicationContext)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            CaptureNotifications.show(applicationContext)
        }
    }
}
