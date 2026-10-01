package com.example.petfeeding

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * Home-screen widget showing all 4 pets. Each row has:
 *   - the pet's name
 *   - a short "last fed" label (e.g. "Today 14:20")
 *   - a Feed button that records a feeding WITHOUT opening the app
 *
 * The Feed buttons use broadcast PendingIntents back to this provider; onReceive
 * records the feeding via FeedingStore and refreshes every widget instance.
 */
class PetFeedingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FEED) {
            val index = intent.getIntExtra(EXTRA_PET_INDEX, -1)
            if (index in 0 until FeedingStore.PET_COUNT) {
                FeedingStore.recordFeeding(context, index)
                refreshAll(context)
            }
        }
    }

    companion object {
        const val ACTION_FEED = "com.example.petfeeding.ACTION_FEED"
        const val EXTRA_PET_INDEX = "pet_index"

        private val NAME_IDS = intArrayOf(
            R.id.w_name_0, R.id.w_name_1, R.id.w_name_2, R.id.w_name_3
        )
        private val STATUS_IDS = intArrayOf(
            R.id.w_status_0, R.id.w_status_1, R.id.w_status_2, R.id.w_status_3
        )
        private val BUTTON_IDS = intArrayOf(
            R.id.w_feed_0, R.id.w_feed_1, R.id.w_feed_2, R.id.w_feed_3
        )
        private val ROW_IDS = intArrayOf(
            R.id.w_row_0, R.id.w_row_1, R.id.w_row_2, R.id.w_row_3
        )

        /** Refresh every instance of this widget on the home screen. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, PetFeedingWidget::class.java)
            )
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pet_feeding)
            val pets = FeedingStore.loadPets(context)

            for (i in 0 until FeedingStore.PET_COUNT) {
                val pet = pets[i]
                views.setTextViewText(NAME_IDS[i], pet.name)
                views.setTextViewText(STATUS_IDS[i], FeedingStore.formatShort(pet.lastFed()))

                // Highlight rows fed today with a soft green background.
                views.setInt(
                    ROW_IDS[i],
                    "setBackgroundResource",
                    if (pet.fedToday()) R.drawable.widget_row_fed else R.drawable.widget_row_bg
                )

                views.setOnClickPendingIntent(BUTTON_IDS[i], feedPendingIntent(context, i))
            }
            return views
        }

        private fun feedPendingIntent(context: Context, index: Int): PendingIntent {
            val intent = Intent(context, PetFeedingWidget::class.java).apply {
                action = ACTION_FEED
                putExtra(EXTRA_PET_INDEX, index)
                // Unique data so each pet's PendingIntent is distinct.
                data = android.net.Uri.parse("petfeeding://feed/$index")
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return PendingIntent.getBroadcast(context, index, intent, flags)
        }
    }
}
