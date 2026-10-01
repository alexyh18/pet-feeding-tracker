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
 * Home-screen widget showing the current month as a grid, highlighting every day on
 * which any pet was fed (soft green) and today (outlined). Tapping the widget opens
 * the app's Calendar page. Backed by CalendarWidgetService.
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
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
            views.setTextViewText(R.id.wc_month, monthLabel)

            val serviceIntent = Intent(context, CalendarWidgetService::class.java).apply {
                data = Uri.parse("petfeeding://calendar/$id")
            }
            views.setRemoteAdapter(R.id.wc_grid, serviceIntent)

            // Tapping anywhere opens the app (Calendar page shown via ViewPager default + swipe).
            val openIntent = Intent(context, MainActivity::class.java)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            val openPi = PendingIntent.getActivity(context, 0, openIntent, flags)
            views.setOnClickPendingIntent(R.id.wc_title, openPi)

            mgr.updateAppWidget(id, views)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.wc_grid)
        }
    }
}
