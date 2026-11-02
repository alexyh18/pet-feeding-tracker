package com.example.petfeeding

import android.app.DatePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Per-pet weight screen: a line graph of daily weights plus the list of entries with
 * add / edit / delete. Weights are stored in grams (shown in g / kg).
 */
class WeightActivity : AppCompatActivity() {

    private var petIndex = 0
    private lateinit var graph: WeightGraphView
    private lateinit var listContainer: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        petIndex = intent.getIntExtra(EXTRA_PET_INDEX, 0)
            .coerceIn(0, (FeedingStore.petCount(this) - 1).coerceAtLeast(0))

        val pet = FeedingStore.loadPets(this)[petIndex]

        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@WeightActivity, R.color.bg))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "${pet.icon}  ${pet.name} — Weight"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WeightActivity, R.color.pink_dark))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        summary = TextView(this).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@WeightActivity, R.color.text_soft))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(summary)

        graph = WeightGraphView(this).apply { lineColor = pet.color }
        root.addView(graph, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
        ))

        root.addView(Button(this).apply {
            text = "➕  Add / update today's weight"
            setOnClickListener { addWeight(System.currentTimeMillis()) }
        })

        root.addView(TextView(this).apply {
            text = "Entries (tap to edit, long-press to delete)"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@WeightActivity, R.color.text_soft))
            setPadding(0, dp(12), 0, dp(4))
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        root.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        })

        setContentView(scroll)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (petIndex >= FeedingStore.petCount(this)) { finish(); return }
        refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun refresh() {
        val pet = FeedingStore.loadPets(this).getOrNull(petIndex) ?: return
        val series = pet.weightSeries()
        graph.series = series

        val latest = pet.latestWeight()
        summary.text = if (latest != null) "Latest: ${fmt(latest)}  ·  ${series.size} entries"
            else "No weight entries yet"

        listContainer.removeAllViews()
        series.sortedByDescending { it.first }.forEach { (day, grams) ->
            val label = android.text.format.DateFormat.format("yyyy.MM.dd", day).toString()
            val row = TextView(this).apply {
                text = "$label    ${fmt(grams)}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@WeightActivity, R.color.text))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener { addWeight(day) }
                setOnLongClickListener { confirmDelete(day, label); true }
            }
            listContainer.addView(row)
        }
    }

    private fun addWeight(dayStart: Long) {
        val pet = FeedingStore.loadPets(this).getOrNull(petIndex) ?: return
        val dayKey = FeedingStore.startOfDay(dayStart)
        val existing = pet.weights[dayKey]

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Weight in grams"
            if (existing != null) setText(trimNum(existing))
            setPadding(48, 32, 48, 32)
        }

        // Let the user pick which day this weight is for (defaults to the row/today).
        val cal = Calendar.getInstance().apply { timeInMillis = dayKey }
        val chosenDay = longArrayOf(dayKey)
        val dateBtnLabelHolder = arrayOfNulls<Button>(1)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val dateButton = Button(this).apply {
            text = "📅 " + android.text.format.DateFormat.format("yyyy.MM.dd", chosenDay[0])
            setOnClickListener {
                val c = Calendar.getInstance().apply { timeInMillis = chosenDay[0] }
                DatePickerDialog(
                    this@WeightActivity,
                    { _, y, mo, d ->
                        val picked = Calendar.getInstance().apply {
                            set(y, mo, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                        }
                        chosenDay[0] = FeedingStore.startOfDay(picked.timeInMillis)
                        dateBtnLabelHolder[0]?.text =
                            "📅 " + android.text.format.DateFormat.format("yyyy.MM.dd", chosenDay[0])
                    },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        dateBtnLabelHolder[0] = dateButton
        container.addView(dateButton)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit weight" else "Add weight")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val grams = input.text.toString().toDoubleOrNull()
                if (grams != null && grams > 0) {
                    FeedingStore.setWeight(this, petIndex, chosenDay[0], grams)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(dayStart: Long, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete weight?")
            .setMessage(label)
            .setPositiveButton("Delete") { _, _ ->
                FeedingStore.removeWeight(this, petIndex, dayStart)
                refresh()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun fmt(grams: Double): String =
        if (grams >= 1000) "%.2f kg".format(grams / 1000.0) else "${trimNum(grams)} g"

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    companion object {
        const val EXTRA_PET_INDEX = "pet_index"
    }
}
