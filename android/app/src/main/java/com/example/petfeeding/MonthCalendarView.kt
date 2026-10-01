package com.example.petfeeding

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import java.util.Calendar

/**
 * A self-contained month calendar that highlights the days feedings happened.
 *
 * Two modes:
 *  - single pet: pass a petIndex; highlights that pet's fed days, tapping a day lets
 *    you view/cancel that pet's feedings.
 *  - combined (petIndex = ALL): highlights any day where at least one pet was fed and
 *    shows which pets' icons were fed when a day is tapped.
 *
 * Fully programmatic so it works both inside a ViewPager page and a standalone Activity.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var petIndex: Int = ALL
    var onChanged: (() -> Unit)? = null

    private val month = Calendar.getInstance()
    private val monthLabel: TextView
    private val grid: GridLayout
    private val legend: LinearLayout

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        month.set(Calendar.DAY_OF_MONTH, 1)

        // Month navigation.
        val nav = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nav.addView(navButton("‹") { month.add(Calendar.MONTH, -1); render() })
        monthLabel = TextView(context).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        nav.addView(monthLabel)
        nav.addView(navButton("›") { month.add(Calendar.MONTH, 1); render() })
        addView(nav)

        // Weekday header.
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
            header.addView(TextView(context).apply {
                text = d
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.text_soft))
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        addView(header)

        grid = GridLayout(context).apply { columnCount = 7 }
        addView(grid)

        // Color legend (one swatch per pet). Horizontally scrollable for many pets.
        legend = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val legendScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(10), 0, 0)
            addView(legend)
        }
        addView(legendScroll)
    }

    private fun navButton(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Re-read data and redraw. Call when feedings change or the page becomes visible. */
    fun render() {
        val pets = FeedingStore.loadPets(context)

        // For each day in the month, the list of pet-colors fed that day. In single-pet
        // mode we only keep that one pet's color.
        val colorsByDay: Map<Long, List<Int>> = if (petIndex == ALL) {
            FeedingStore.colorsByDay(pets)
        } else {
            val idx = petIndex.coerceIn(0, pets.size - 1)
            val pet = pets[idx]
            FeedingStore.fedDayStarts(pet).associateWith { listOf(pet.color) }
        }

        monthLabel.text = android.text.format.DateFormat.format("MMMM yyyy", month)
        grid.removeAllViews()

        val first = month.clone() as Calendar
        first.set(Calendar.DAY_OF_MONTH, 1)
        val leadingBlanks = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cell = (resources.displayMetrics.widthPixels - dp(44)) / 7

        for (i in 0 until leadingBlanks) grid.addView(emptyCell(cell))

        val today = Calendar.getInstance()
        for (day in 1..daysInMonth) {
            val cal = month.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayStart = FeedingStore.startOfDay(cal.timeInMillis)
            val dayColors = colorsByDay[dayStart] ?: emptyList()
            val isToday = FeedingStore.isSameDay(cal.timeInMillis, today.timeInMillis)
            grid.addView(dayCell(day, dayColors, isToday, cell, dayStart))
        }

        updateLegend(pets)
    }

    private fun updateLegend(pets: List<FeedingStore.Pet>) {
        legend.removeAllViews()
        legend.addView(TextView(context).apply {
            text = "Fed: "
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.text_soft))
        })
        val shown = if (petIndex == ALL) pets else listOf(pets[petIndex.coerceIn(0, pets.size - 1)])
        shown.forEach { p ->
            legend.addView(TextView(context).apply {
                text = " ●"
                textSize = 13f
                setTextColor(p.color)
            })
            legend.addView(TextView(context).apply {
                text = p.name + "  "
                textSize = 11f
                setTextColor(ContextCompat.getColor(context, R.color.text_soft))
            })
        }
    }

    private fun emptyCell(size: Int): View = View(context).apply {
        layoutParams = GridLayout.LayoutParams().apply {
            width = size; height = size
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun dayCell(day: Int, colors: List<Int>, isToday: Boolean, size: Int, dayStart: Long): View {
        return DayCellView(context).apply {
            this.day = day
            this.colors = colors
            this.isToday = isToday
            layoutParams = GridLayout.LayoutParams().apply {
                width = size; height = size
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            if (colors.isNotEmpty()) setOnClickListener { showDay(dayStart) }
        }
    }

    private fun showDay(dayStart: Long) {
        val pets = FeedingStore.loadPets(context)
        val dateLabel = android.text.format.DateFormat.format("yyyy.MM.dd", dayStart)

        if (petIndex == ALL) {
            // Combined view: list which pets were fed and at what times.
            val lines = mutableListOf<String>()
            pets.forEach { p ->
                val times = p.history.filter { FeedingStore.startOfDay(it) == dayStart }
                    .sortedDescending()
                if (times.isNotEmpty()) {
                    lines.add("${p.icon} ${p.name}: " +
                        times.joinToString(", ") { FeedingStore.formatTime(it) })
                }
            }
            AlertDialog.Builder(context)
                .setTitle("Feedings on $dateLabel")
                .setMessage(if (lines.isEmpty()) "No feedings." else lines.joinToString("\n"))
                .setPositiveButton("Close", null)
                .show()
            return
        }

        val idx = petIndex.coerceIn(0, pets.size - 1)
        val entries = pets[idx].history
            .filter { FeedingStore.startOfDay(it) == dayStart }
            .sortedDescending()
        if (entries.isEmpty()) return

        val labels = entries.map { FeedingStore.formatFull(it) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Feedings on $dateLabel")
            .setItems(labels) { _, which -> confirmDelete(idx, entries[which]) }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmDelete(idx: Int, ts: Long) {
        AlertDialog.Builder(context)
            .setTitle("Cancel this feeding?")
            .setMessage(FeedingStore.formatFull(ts))
            .setPositiveButton("Delete") { _, _ ->
                FeedingStore.removeFeeding(context, idx, ts)
                PetFeedingWidget.refreshAll(context)
                CalendarWidget.refreshAll(context)
                ReminderScheduler.scheduleNext(context)
                render()
                onChanged?.invoke()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    companion object {
        const val ALL = -1
    }
}
