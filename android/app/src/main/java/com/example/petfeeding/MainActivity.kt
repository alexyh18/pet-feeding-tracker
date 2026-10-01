package com.example.petfeeding

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Main screen: a 2x2 grid of pet cards. Each card supports:
 *  - a customizable icon (tap to pick, includes lizard/snake),
 *  - an editable name,
 *  - a large Feed button that toggles today's feeding (tap again to undo),
 *  - last-fed time + "Fed Today" state,
 *  - History list, a Calendar view, and reminder-interval settings.
 * Shares all data with the home-screen widget through FeedingStore and schedules
 * per-pet feeding reminders via ReminderScheduler.
 */
class MainActivity : AppCompatActivity() {

    private val cardWrapIds = intArrayOf(R.id.wrap_0, R.id.wrap_1, R.id.wrap_2, R.id.wrap_3)
    private val cards = arrayOfNulls<View>(FeedingStore.PET_COUNT)

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ReminderReceiver.ensureChannel(this)
        maybeRequestNotificationPermission()
        setupCards()
        ReminderScheduler.scheduleNext(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupCards() {
        for (i in 0 until FeedingStore.PET_COUNT) {
            val index = i
            val card = findViewById<View>(cardWrapIds[i])
            cards[i] = card

            card.findViewById<EditText>(R.id.name).setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    FeedingStore.renamePet(this, index, (v as EditText).text.toString())
                    PetFeedingWidget.refreshAll(this)
                }
            }

            card.findViewById<TextView>(R.id.icon).setOnClickListener { pickIcon(index) }
            card.findViewById<Button>(R.id.feed).setOnClickListener { confirmToggleFeed(index) }
            card.findViewById<Button>(R.id.hist).setOnClickListener { showHistory(index) }
            card.findViewById<Button>(R.id.calendar).setOnClickListener { openCalendar(index) }
            card.findViewById<Button>(R.id.settings).setOnClickListener { setInterval(index) }
        }
    }

    private fun confirmToggleFeed(index: Int) {
        val pet = FeedingStore.loadPets(this)[index]
        if (pet.fedToday()) {
            // Already fed today → offer to undo.
            AlertDialog.Builder(this)
                .setTitle("Undo today's feeding?")
                .setMessage("${pet.name} is marked as fed today. Remove today's feeding?")
                .setPositiveButton("Undo") { _, _ -> applyToggle(index) }
                .setNegativeButton("Keep", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Confirm feeding")
                .setMessage("Did you just feed ${pet.name}?")
                .setPositiveButton("Yes") { _, _ -> applyToggle(index) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun applyToggle(index: Int) {
        FeedingStore.toggleTodayFeeding(this, index)
        PetFeedingWidget.refreshAll(this)
        ReminderScheduler.scheduleNext(this)
        refresh()
    }

    private fun pickIcon(index: Int) {
        val choices = FeedingStore.ICON_CHOICES.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose an icon")
            .setItems(choices) { _, which ->
                FeedingStore.setIcon(this, index, choices[which])
                PetFeedingWidget.refreshAll(this)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setInterval(index: Int) {
        val pet = FeedingStore.loadPets(this)[index]
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Days between feedings (0 = off)"
            setText(if (pet.intervalDays > 0) pet.intervalDays.toString() else "")
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Reminder for ${pet.name}")
            .setMessage("How many days between feedings? You'll get a notification when it's due. Enter 0 to turn reminders off.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val days = input.text.toString().toIntOrNull() ?: 0
                FeedingStore.setInterval(this, index, days)
                ReminderScheduler.scheduleNext(this)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHistory(index: Int) {
        val pet = FeedingStore.loadPets(this)[index]
        val sorted = pet.history.sortedDescending()
        val content = if (sorted.isEmpty()) {
            "No feedings logged yet."
        } else {
            sorted.joinToString("\n") { "•  " + FeedingStore.formatFull(it) }
        }
        AlertDialog.Builder(this)
            .setTitle("${pet.icon} ${pet.name}'s History")
            .setMessage(content)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun openCalendar(index: Int) {
        startActivity(Intent(this, CalendarActivity::class.java).apply {
            putExtra(CalendarActivity.EXTRA_PET_INDEX, index)
        })
    }

    private fun refresh() {
        val pets = FeedingStore.loadPets(this)
        for (i in 0 until FeedingStore.PET_COUNT) {
            val pet = pets[i]
            val card = cards[i] ?: continue

            card.findViewById<TextView>(R.id.icon).text = pet.icon

            val nameField = card.findViewById<EditText>(R.id.name)
            if (!nameField.hasFocus() && nameField.text.toString() != pet.name) {
                nameField.setText(pet.name)
            }

            val last = pet.lastFed()
            card.findViewById<TextView>(R.id.last).text =
                if (last != null) "Last fed:\n${FeedingStore.formatFull(last)}" else "Not fed yet"

            val fedToday = pet.fedToday()
            card.setBackgroundResource(if (fedToday) R.drawable.card_fed else R.drawable.card_bg)
            card.findViewById<View>(R.id.badge).visibility =
                if (fedToday) View.VISIBLE else View.GONE

            card.findViewById<Button>(R.id.feed).text =
                if (fedToday) "✓ Fed — tap to undo" else getString(R.string.feed)

            card.findViewById<Button>(R.id.hist).text = "History (${pet.history.size})"

            card.findViewById<TextView>(R.id.interval).text =
                if (pet.intervalDays > 0) "Reminder: every ${pet.intervalDays}d" else "Reminder: off"
        }
    }
}
