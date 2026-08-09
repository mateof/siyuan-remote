// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.update

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
import androidx.core.content.ContextCompat
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.ui.settings.SettingsActivity

/** Tells the user a new release exists, from outside the app. */
object UpdateNotifications {

    const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 5120

    @SuppressLint("MissingPermission")
    fun notifyAvailable(context: Context, release: GitHubRelease) {
        if (!canPost(context)) return
        createChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SCROLL_TO_UPDATES, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notification_title, release.version))
            .setContentText(context.getString(R.string.update_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(release.notes.take(NOTES_PREVIEW)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_updates_description)
            setShowBadge(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private const val NOTES_PREVIEW = 400
}
