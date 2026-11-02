package com.example.petfeeding

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Home-screen widget showing the current month as a grid, filling the widget. Each day
 * is painted with the colors of the pets fed that day (split into N equal stripes when
 * several pets were fed), today is outlined. A 🔄 button refreshes the data on demand;
 * tapping the month label opens the app's Calendar page. Backed by CalendarWidgetService.
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.petfeeding.ACTION_CALENDAR_REFRESH"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isEmpty()) return
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.wc_grid)
            ids.forEach { id -> updateWidget(context, mgr, id) }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)

            val monthLabel = android.text.format.DateFormat
                .format("MMMM yyyy", Calendar.getInstance())
            views.setTextViewText(R.id.wc_month, "📅 $monthLabel")

            val serviceIntent = Intent(context, CalendarWidgetService::class.java).apply {
                data = Uri.parse("petfeeding://calendar/$id")
            }
            views.setRemoteAdapter(R.id.wc_grid, serviceIntent)

            var activityFlags = PendingIntent.FLAG_UPDATE_CURRENT
            var broadcastFlags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activityFlags = activityFlags or PendingIntent.FLAG_IMMUTABLE
                broadcastFlags = broadcastFlags or PendingIntent.FLAG_IMMUTABLE
            }

            // Tap month label → open the app.
            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(context, 0, openIntent, activityFlags)
            views.setOnClickPendingIntent(R.id.wc_month, openPi)

            // Tap 🔄 → refresh this widget's data.
            val refreshIntent = Intent(context, CalendarWidget::class.java).apply {
                action = ACTION_REFRESH
                data = Uri.parse("petfeeding://calrefresh/$id")
            }
            val refreshPi = PendingIntent.getBroadcast(context, id, refreshIntent, broadcastFlags)
            views.setOnClickPendingIntent(R.id.wc_refresh, refreshPi)

            mgr.updateAppWidget(id, views)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.wc_grid)
        }
    }
}
