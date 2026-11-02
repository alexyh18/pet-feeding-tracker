package com.example.petfeeding

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Standalone single-pet feeding calendar, opened from a pet card. The month grid and
 * day interaction live in the reusable MonthCalendarView.
 */
class CalendarActivity : AppCompatActivity() {

    private var petIndex = 0
    private var calendar: MonthCalendarView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        petIndex = intent.getIntExtra(EXTRA_PET_INDEX, 0)
            .coerceIn(0, (FeedingStore.petCount(this) - 1).coerceAtLeast(0))

        val pet = FeedingStore.loadPets(this)[petIndex]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@CalendarActivity, R.color.bg))
            setPadding(dp(12), dp(20), dp(12), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "${pet.icon}  ${pet.name} — Feeding Calendar"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CalendarActivity, R.color.pink_dark))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        })

        val cal = MonthCalendarView(this).apply { this.petIndex = this@CalendarActivity.petIndex }
        calendar = cal
        root.addView(cal, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        })

        setContentView(root)
        cal.render()
    }

    override fun onResume() {
        super.onResume()
        // If the pet was removed elsewhere (e.g. via a widget), don't show a different
        // pet's data under the original title — just close.
        if (petIndex >= FeedingStore.petCount(this)) {
            finish()
            return
        }
        calendar?.render()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PET_INDEX = "pet_index"
    }
}
