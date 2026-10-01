package com.example.petfeeding

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Calendar

/** Supplies the day cells (as rendered bitmaps) for the current month calendar widget. */
class CalendarWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CalendarFactory(applicationContext)
}

private class CalendarFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private data class Cell(val day: Int?, val colors: List<Int>, val today: Boolean)

    private var cells: List<Cell> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val pets = FeedingStore.loadPets(context)
        val colorsByDay = FeedingStore.colorsByDay(pets)

        val month = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val leadingBlanks = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()

        val list = ArrayList<Cell>(leadingBlanks + daysInMonth)
        repeat(leadingBlanks) { list.add(Cell(null, emptyList(), false)) }
        for (day in 1..daysInMonth) {
            val cal = month.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayStart = FeedingStore.startOfDay(cal.timeInMillis)
            list.add(
                Cell(
                    day = day,
                    colors = colorsByDay[dayStart] ?: emptyList(),
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
        val bmp = CalendarCellRenderer.render(cell.day, cell.colors, cell.today)
        rv.setImageViewBitmap(R.id.wc_cell, bmp)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
