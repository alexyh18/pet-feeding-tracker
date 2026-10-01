package com.example.petfeeding

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Main screen: a 2x2 grid of pet cards. Each card has an editable name, a large
 * Feed button (with a confirmation dialog), the last-fed time, and a history button.
 * Shares all data with the home-screen widget through FeedingStore.
 */
class MainActivity : AppCompatActivity() {

    private val cardNameIds = intArrayOf(R.id.name_0, R.id.name_1, R.id.name_2, R.id.name_3)
    private val cardFeedIds = intArrayOf(R.id.feed_0, R.id.feed_1, R.id.feed_2, R.id.feed_3)
    private val cardLastIds = intArrayOf(R.id.last_0, R.id.last_1, R.id.last_2, R.id.last_3)
    private val cardHistIds = intArrayOf(R.id.hist_0, R.id.hist_1, R.id.hist_2, R.id.hist_3)
    private val cardRootIds = intArrayOf(R.id.card_0, R.id.card_1, R.id.card_2, R.id.card_3)
    private val cardBadgeIds = intArrayOf(R.id.badge_0, R.id.badge_1, R.id.badge_2, R.id.badge_3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupCards()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setupCards() {
        for (i in 0 until FeedingStore.PET_COUNT) {
            val index = i
            val nameField = findViewById<EditText>(cardNameIds[i])
            nameField.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    FeedingStore.renamePet(this, index, nameField.text.toString())
                    PetFeedingWidget.refreshAll(this)
                }
            }

            findViewById<Button>(cardFeedIds[i]).setOnClickListener {
                confirmFeed(index)
            }

            findViewById<Button>(cardHistIds[i]).setOnClickListener {
                showHistory(index)
            }
        }
    }

    private fun confirmFeed(index: Int) {
        val pet = FeedingStore.loadPets(this)[index]
        AlertDialog.Builder(this)
            .setTitle("Confirm feeding")
            .setMessage("Did you just feed ${pet.name}?")
            .setPositiveButton("Yes") { _, _ ->
                FeedingStore.recordFeeding(this, index)
                PetFeedingWidget.refreshAll(this)
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
            .setTitle("${pet.name}'s History")
            .setMessage(content)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun refresh() {
        val pets = FeedingStore.loadPets(this)
        for (i in 0 until FeedingStore.PET_COUNT) {
            val pet = pets[i]
            val nameField = findViewById<EditText>(cardNameIds[i])
            if (!nameField.hasFocus() && nameField.text.toString() != pet.name) {
                nameField.setText(pet.name)
            }

            val last = pet.lastFed()
            findViewById<TextView>(cardLastIds[i]).text =
                if (last != null) "Last fed:\n${FeedingStore.formatFull(last)}" else "Not fed yet"

            val fedToday = pet.fedToday()
            findViewById<View>(cardRootIds[i]).setBackgroundResource(
                if (fedToday) R.drawable.card_fed else R.drawable.card_bg
            )
            findViewById<View>(cardBadgeIds[i]).visibility =
                if (fedToday) View.VISIBLE else View.GONE

            findViewById<Button>(cardHistIds[i]).text =
                "View History (${pet.history.size})"
        }
    }
}
