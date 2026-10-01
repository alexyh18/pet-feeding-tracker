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

/**
 * Home-screen widget listing all pets (variable count) in a ListView. Each row shows
 * the pet's icon + name and a short "last fed" label, with a Feed button that toggles
 * today's feeding WITHOUT opening the app.
 *
 * Uses a collection (ListView) backed by PetListWidgetService. Feed taps arrive as a
 * fill-in intent merged into the template broadcast PendingIntent set here; onReceive
 * records/cancels the feeding and refreshes.
 */
class PetFeedingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FEED) {
            val index = intent.getIntExtra(EXTRA_PET_INDEX, -1)
            if (index >= 0 && index < FeedingStore.petCount(context)) {
                FeedingStore.toggleTodayFeeding(context, index)
                ReminderScheduler.scheduleNext(context)
                refreshAll(context)
            }
        }
    }

    companion object {
        const val ACTION_FEED = "com.example.petfeeding.ACTION_FEED"
        const val EXTRA_PET_INDEX = "pet_index"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PetFeedingWidget::class.java))
            if (ids.isEmpty()) return
            // Tell the ListView its data changed, then rebuild each instance.
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
            ids.forEach { id -> updateWidget(context, mgr, id) }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_pet_feeding)

            val serviceIntent = Intent(context, PetListWidgetService::class.java).apply {
                data = Uri.parse("petfeeding://list/$id")
            }
            views.setRemoteAdapter(R.id.w_list, serviceIntent)
            views.setEmptyView(R.id.w_list, R.id.w_empty)

            // Template PendingIntent for the rows' Feed buttons.
            val feedIntent = Intent(context, PetFeedingWidget::class.java).apply {
                action = ACTION_FEED
                data = Uri.parse("petfeeding://feed/$id")
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val template = PendingIntent.getBroadcast(context, id, feedIntent, flags)
            views.setPendingIntentTemplate(R.id.w_list, template)

            mgr.updateAppWidget(id, views)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.w_list)
        }
    }
}
