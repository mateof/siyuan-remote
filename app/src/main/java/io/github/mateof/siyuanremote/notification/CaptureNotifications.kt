package io.github.mateof.siyuanremote.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.capture.QuickCaptureActivity

/**
 * A permanent, silent notification with an inline reply field.
 *
 * Typing into a notification is the fastest path from "I just thought of
 * something" to "it is in my daily note": no app switch, no unlock of the
 * knowledge base, one tap from the shade.
 */
object CaptureNotifications {

    const val CHANNEL_ID = "quick_capture"
    const val NOTIFICATION_ID = 4711
    const val REMOTE_INPUT_KEY = "capture_text"

    @SuppressLint("MissingPermission")
    fun show(context: Context) {
        if (!canPost(context)) return

        createChannel(context)

        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(context.getString(R.string.notification_capture_hint))
            .build()

        val replyIntent = Intent(context, CaptureReplyReceiver::class.java)
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notification_capture_action),
            replyPendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, QuickCaptureActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_capture_title))
            .setContentText(context.getString(R.string.notification_capture_text))
            .setContentIntent(openIntent)
            .addAction(replyAction)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun toast(context: Context, message: String) {
        if (!canPost(context)) return

        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(message)
            .setTimeoutAfter(TOAST_TIMEOUT_MS)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(TOAST_NOTIFICATION_ID, notification)
        }
    }

    /**
     * From API 33 posting is a runtime permission. Returning early keeps the
     * feature silent rather than throwing when the user declined it.
     */
    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_capture),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notification_channel_capture_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private const val TOAST_NOTIFICATION_ID = 4712
    private const val TOAST_TIMEOUT_MS = 4_000L
}
