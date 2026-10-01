package com.example.petfeeding

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import java.util.Calendar

/**
 * A simple month calendar that highlights the days a specific pet was fed. Users can
 * page between months. Tapping a highlighted day shows that day's feeding times and
 * lets the user delete (cancel) a logged feeding.
 */
class CalendarActivity : AppCompatActivity() {

    private var petIndex = 0
    private val month = Calendar.getInstance()
    private lateinit var monthLabel: TextView
    private lateinit var grid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        petIndex = intent.getIntExtra(EXTRA_PET_INDEX, 0)
            .coerceIn(0, FeedingStore.PET_COUNT - 1)
        month.set(Calendar.DAY_OF_MONTH, 1)
        setContentView(buildRoot())
        renderMonth()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@CalendarActivity, R.color.bg))
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }

        val pet = FeedingStore.loadPets(this)[petIndex]
        root.addView(TextView(this).apply {
            text = "${pet.icon}  ${pet.name} — Feeding Calendar"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CalendarActivity, R.color.pink_dark))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        })

        // Month navigation row.
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prev = Button(this).apply {
            text = "‹"
            setOnClickListener { month.add(Calendar.MONTH, -1); renderMonth() }
        }
        monthLabel = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CalendarActivity, R.color.text))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val next = Button(this).apply {
            text = "›"
            setOnClickListener { month.add(Calendar.MONTH, 1); renderMonth() }
        }
        nav.addView(prev)
        nav.addView(monthLabel)
        nav.addView(next)
        root.addView(nav)

        // Weekday header.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
        }
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
            header.addView(TextView(this).apply {
                text = d
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@CalendarActivity, R.color.text_soft))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        root.addView(header)

        grid = GridLayout(this).apply {
            columnCount = 7
        }
        root.addView(grid)

        val legend = TextView(this).apply {
            text = "🟢 = fed that day · tap a green day to view/cancel"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@CalendarActivity, R.color.text_soft))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(legend)

        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        root.addView(close)

        return root
    }

    private fun renderMonth() {
        val pet = FeedingStore.loadPets(this)[petIndex]
        val fedDays = FeedingStore.fedDayStarts(pet)

        monthLabel.text = android.text.format.DateFormat.format("MMMM yyyy", month)
        grid.removeAllViews()

        val first = month.clone() as Calendar
        first.set(Calendar.DAY_OF_MONTH, 1)
        val leadingBlanks = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)

        val cell = (resources.displayMetrics.widthPixels - dp(44)) / 7

        // Leading empty cells.
        for (i in 0 until leadingBlanks) grid.addView(emptyCell(cell))

        val today = Calendar.getInstance()
        for (day in 1..daysInMonth) {
            val cal = month.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayStart = FeedingStore.startOfDay(cal.timeInMillis)
            val fed = fedDays.contains(dayStart)
            val isToday = FeedingStore.isSameDay(cal.timeInMillis, today.timeInMillis)

            grid.addView(dayCell(day, fed, isToday, cell, dayStart))
        }
    }

    private fun emptyCell(size: Int): View = View(this).apply {
        layoutParams = GridLayout.LayoutParams().apply {
            width = size; height = size
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun dayCell(day: Int, fed: Boolean, isToday: Boolean, size: Int, dayStart: Long): View {
        return TextView(this).apply {
            text = day.toString()
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(
                if (fed) Color.WHITE
                else ContextCompat.getColor(this@CalendarActivity, R.color.text)
            )
            setBackgroundResource(
                when {
                    fed -> R.drawable.calendar_day_fed
                    isToday -> R.drawable.calendar_day_today
                    else -> R.drawable.calendar_day
                }
            )
            if (isToday) setTypeface(typeface, Typeface.BOLD)
            layoutParams = GridLayout.LayoutParams().apply {
                width = size; height = size
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            if (fed) setOnClickListener { showDay(dayStart) }
        }
    }

    private fun showDay(dayStart: Long) {
        val pet = FeedingStore.loadPets(this)[petIndex]
        val entries = pet.history
            .filter { FeedingStore.startOfDay(it) == dayStart }
            .sortedDescending()
        if (entries.isEmpty()) return

        val labels = entries.map { FeedingStore.formatFull(it) }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Feedings on ${android.text.format.DateFormat.format("yyyy.MM.dd", dayStart)}")
            .setItems(labels) { _, which ->
                confirmDelete(entries[which])
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmDelete(ts: Long) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cancel this feeding?")
            .setMessage(FeedingStore.formatFull(ts))
            .setPositiveButton("Delete") { _, _ ->
                FeedingStore.removeFeeding(this, petIndex, ts)
                PetFeedingWidget.refreshAll(this)
                ReminderScheduler.scheduleNext(this)
                renderMonth()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    companion object {
        const val EXTRA_PET_INDEX = "pet_index"
    }
}
