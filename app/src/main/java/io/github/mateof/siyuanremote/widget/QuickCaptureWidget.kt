// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.mateof.siyuanremote.MainActivity
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.capture.QuickCaptureActivity

/**
 * Home screen shortcut: one tap into the capture box, one tap into the editor.
 * A bookmark to the web UI cannot offer either.
 */
class QuickCaptureWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
                setOnClickPendingIntent(R.id.widget_capture, activityIntent(context, QuickCaptureActivity::class.java, 0))
                setOnClickPendingIntent(R.id.widget_open, activityIntent(context, MainActivity::class.java, 1))
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun activityIntent(context: Context, target: Class<*>, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
