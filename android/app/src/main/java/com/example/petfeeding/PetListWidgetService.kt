package com.example.petfeeding

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Supplies the per-pet rows for the feeding ListView widget. */
class PetListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PetListFactory(applicationContext)
}

private class PetListFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var pets: List<FeedingStore.Pet> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        pets = FeedingStore.loadPets(context)
    }

    override fun onDestroy() {
        pets = emptyList()
    }

    override fun getCount(): Int = pets.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_pet_row)
        if (position !in pets.indices) return row
        val pet = pets[position]

        row.setTextViewText(R.id.w_name, "${pet.icon} ${pet.name}")
        row.setTextViewText(R.id.w_status, FeedingStore.formatShort(pet.lastFed()))
        row.setInt(
            R.id.w_row, "setBackgroundResource",
            if (pet.fedToday()) R.drawable.widget_row_fed else R.drawable.widget_row_bg
        )

        // Fill-in intent carries the pet index; the collection's template PendingIntent
        // (set on the widget) turns this into the broadcast that records a feeding.
        val fillIn = Intent().apply {
            putExtra(PetFeedingWidget.EXTRA_PET_INDEX, position)
        }
        row.setOnClickFillInIntent(R.id.w_feed, fillIn)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
