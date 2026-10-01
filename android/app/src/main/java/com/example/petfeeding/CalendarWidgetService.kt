package com.example.petfeeding

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Calendar

/** Supplies the day cells for the current month in the calendar widget. */
class CalendarWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CalendarFactory(applicationContext)
}

private class CalendarFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    // Each cell is either a blank (null) or a day number with a "fed" flag + today flag.
    private data class Cell(val day: Int?, val fed: Boolean, val today: Boolean)

    private var cells: List<Cell> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val pets = FeedingStore.loadPets(context)
        val fedDays = FeedingStore.allFedDayStarts(pets)

        val month = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val leadingBlanks = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()

        val list = ArrayList<Cell>(leadingBlanks + daysInMonth)
        repeat(leadingBlanks) { list.add(Cell(null, fed = false, today = false)) }
        for (day in 1..daysInMonth) {
            val cal = month.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayStart = FeedingStore.startOfDay(cal.timeInMillis)
            list.add(
                Cell(
                    day = day,
                    fed = fedDays.contains(dayStart),
                    today = FeedingStore.isSameDay(cal.timeInMillis, today.timeInMillis)
                )
            )
        }
        cells = list
    }

    override fun onDestroy() { cells = emptyList() }

    override fun getCount(): Int = cells.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_calendar_cell)
        val cell = cells.getOrNull(position) ?: return rv
        if (cell.day == null) {
            rv.setTextViewText(R.id.wc_cell, "")
            rv.setInt(R.id.wc_cell, "setBackgroundResource", android.R.color.transparent)
            return rv
        }
        rv.setTextViewText(R.id.wc_cell, cell.day.toString())
        rv.setInt(
            R.id.wc_cell, "setBackgroundResource",
            when {
                cell.fed -> R.drawable.calendar_day_fed
                cell.today -> R.drawable.calendar_day_today
                else -> R.drawable.calendar_day
            }
        )
        rv.setTextColor(R.id.wc_cell, if (cell.fed) Color.WHITE else Color.parseColor("#5A4A52"))
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
