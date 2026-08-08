package io.github.mateof.siyuanremote.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.capture.CaptureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives text typed into the quick capture notification and files it away. */
@AndroidEntryPoint
class CaptureReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var captureRepository: CaptureRepository

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(CaptureNotifications.REMOTE_INPUT_KEY)
            ?.toString()
            .orEmpty()

        val appContext = context.applicationContext
        if (text.isBlank()) {
            CaptureNotifications.show(appContext)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                captureRepository.capture(text)
                    .onSuccess {
                        CaptureNotifications.toast(
                            appContext,
                            appContext.getString(R.string.capture_sent),
                        )
                    }
                    .onFailure { error ->
                        CaptureNotifications.toast(
                            appContext,
                            error.message ?: appContext.getString(R.string.capture_error_generic),
                        )
                    }
            } finally {
                // Re-post so the reply field is available again.
                CaptureNotifications.show(appContext)
                pendingResult.finish()
            }
        }
    }
}
