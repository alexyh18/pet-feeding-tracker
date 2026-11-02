package com.example.petfeeding

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Two swipeable pages via ViewPager2:
 *   Page 0 "Pets": a variable-length grid of pet cards + an "Add a pet" button.
 *   Page 1 "Calendar": a combined month calendar across all pets.
 *
 * The app starts with a single pet; the user adds or removes pets as needed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var pageHint: TextView
    private var petAdapter: PetCardAdapter? = null
    private var calendarView: MonthCalendarView? = null

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Which pet a just-launched photo pick is for. */
    private var photoTargetIndex: Int = -1

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val index = photoTargetIndex
        photoTargetIndex = -1
        if (uri == null || index !in 0 until FeedingStore.petCount(this)) return@registerForActivityResult
        val path = PhotoUtil.importPhoto(this, uri, index.toString())
        if (path == null) {
            android.widget.Toast.makeText(this, "Couldn't load that photo", android.widget.Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        FeedingStore.setPhoto(this, index, path)
        syncAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ReminderReceiver.ensureChannel(this)
        maybeRequestNotificationPermission()

        pager = findViewById(R.id.pager)
        pageHint = findViewById(R.id.page_hint)
        pager.adapter = PagerAdapter()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateHint(position)
                if (position == 1) calendarView?.render()
            }
        })
        updateHint(0)

        ReminderScheduler.scheduleNext(this)
    }

    override fun onResume() {
        super.onResume()
        petAdapter?.submit(FeedingStore.loadPets(this))
        calendarView?.render()
    }

    private fun launchPhotoPicker(index: Int) {
        photoTargetIndex = index
        pickPhoto.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    private fun updateHint(position: Int) {
        pageHint.text = if (position == 0) "● Pets    ○ Calendar  ·  swipe →"
        else "○ Pets    ● Calendar  ·  ← swipe"
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPets() {
        petAdapter?.submit(FeedingStore.loadPets(this))
    }

    // ---- Pet actions (delegated from the card adapter) ----

    private val cardCallbacks = object : PetCardAdapter.Callbacks {
        override fun onFeedToggle(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            val pet = FeedingStore.loadPets(this@MainActivity).getOrNull(index) ?: return
            if (pet.fedToday()) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Undo today's feeding?")
                    .setMessage("${pet.name} is marked as fed today. Remove today's feeding?")
                    .setPositiveButton("Undo") { _, _ -> applyToggle(index) }
                    .setNegativeButton("Keep", null)
                    .show()
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Confirm feeding")
                    .setMessage("Did you just feed ${pet.name}?")
                    .setPositiveButton("Yes") { _, _ -> applyToggle(index) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun onPickIcon(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            val pet = FeedingStore.loadPets(this@MainActivity).getOrNull(index) ?: return

            // Species emoji choices, plus "use a photo" and (if set) "remove photo".
            val options = ArrayList<String>()
            options.addAll(FeedingStore.ICON_LABELS)
            val photoOptionIndex = options.size
            options.add("📷  Use a photo…")
            val removeOptionIndex = if (pet.hasPhoto()) {
                options.add("🧹  Remove photo (use emoji)")
                options.size - 1
            } else -1

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Choose icon or photo")
                .setItems(options.toTypedArray()) { _, which ->
                    when (which) {
                        photoOptionIndex -> launchPhotoPicker(index)
                        removeOptionIndex -> {
                            FeedingStore.setPhoto(this@MainActivity, index, null)
                            syncAll()
                        }
                        else -> {
                            FeedingStore.setIcon(this@MainActivity, index, FeedingStore.ICON_CHOICES[which])
                            // Picking a species emoji clears any photo so it actually shows.
                            FeedingStore.setPhoto(this@MainActivity, index, null)
                            syncAll()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        override fun onEditName(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            val pet = FeedingStore.loadPets(this@MainActivity).getOrNull(index) ?: return
            val input = EditText(this@MainActivity).apply {
                setText(pet.name)
                hint = "Pet name"
                setSingleLine()
                setSelection(text.length)
                setPadding(48, 32, 48, 32)
                filters = arrayOf(android.text.InputFilter.LengthFilter(18))
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Edit name")
                .setView(input)
                // Explicit "Done" (완료) button to save the name.
                .setPositiveButton("완료") { _, _ ->
                    FeedingStore.renamePet(this@MainActivity, index, input.text.toString())
                    syncAll()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        override fun onHistory(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            val pet = FeedingStore.loadPets(this@MainActivity).getOrNull(index) ?: return
            val sorted = pet.history.sortedDescending()
            val content = if (sorted.isEmpty()) "No feedings logged yet."
                else sorted.joinToString("\n") { "•  " + FeedingStore.formatFull(it) }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("${pet.icon} ${pet.name}'s History")
                .setMessage(content)
                .setPositiveButton("Close", null)
                .show()
        }

        override fun onCalendar(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            startActivity(Intent(this@MainActivity, CalendarActivity::class.java).apply {
                putExtra(CalendarActivity.EXTRA_PET_INDEX, index)
            })
        }

        override fun onWeight(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            startActivity(Intent(this@MainActivity, WeightActivity::class.java).apply {
                putExtra(WeightActivity.EXTRA_PET_INDEX, index)
            })
        }

        override fun onSettings(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            val pet = FeedingStore.loadPets(this@MainActivity).getOrNull(index) ?: return

            // Holds the time the user picks; starts from the pet's current reminder time.
            val pickedHour = intArrayOf(pet.reminderHour)
            val pickedMinute = intArrayOf(pet.reminderMinute)

            val pad = (16 * resources.displayMetrics.density).toInt()
            val container = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad * 2, pad, pad * 2, 0)
            }

            val input = EditText(this@MainActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "Days between feedings (0 = off)"
                setText(if (pet.intervalDays > 0) pet.intervalDays.toString() else "")
            }
            container.addView(input)

            fun timeLabel(h: Int, m: Int) =
                "⏰ Reminder time: %02d:%02d  (tap to change)".format(h, m)

            val timeButton = Button(this@MainActivity).apply {
                text = timeLabel(pickedHour[0], pickedMinute[0])
                setOnClickListener {
                    android.app.TimePickerDialog(
                        this@MainActivity,
                        { _, h, m ->
                            pickedHour[0] = h
                            pickedMinute[0] = m
                            text = timeLabel(h, m)
                        },
                        pickedHour[0], pickedMinute[0], true
                    ).show()
                }
            }
            container.addView(timeButton)

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Reminder for ${pet.name}")
                .setMessage("Days between feedings (0 = off), and what time to be reminded.")
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val days = input.text.toString().toIntOrNull() ?: 0
                    FeedingStore.setInterval(this@MainActivity, index, days)
                    FeedingStore.setReminderTime(
                        this@MainActivity, index, pickedHour[0], pickedMinute[0]
                    )
                    ReminderScheduler.scheduleNext(this@MainActivity)
                    refreshPets()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        override fun onDelete(index: Int) {
            if (index == RecyclerView.NO_POSITION) return
            val pet = FeedingStore.loadPets(this@MainActivity).getOrNull(index) ?: return
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Remove ${pet.name}?")
                .setMessage("This deletes the pet and its feeding history.")
                .setPositiveButton("Remove") { _, _ ->
                    FeedingStore.removePet(this@MainActivity, index)
                    syncAll()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        override fun canDelete(): Boolean = FeedingStore.petCount(this@MainActivity) > 1
    }

    private fun applyToggle(index: Int) {
        FeedingStore.toggleTodayFeeding(this, index)
        syncAll()
        ReminderScheduler.scheduleNext(this)
    }

    /** Refresh cards, calendar page, and both widgets after any data change. */
    private fun syncAll() {
        // Make any pending name-edit focus-loss fire deterministically before we rebind.
        currentFocus?.clearFocus()
        refreshPets()
        calendarView?.render()
        PetFeedingWidget.refreshAll(this)
        CalendarWidget.refreshAll(this)
    }

    // ---- ViewPager2 adapter with two static pages ----

    private inner class PagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = 2
        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.page_pets, parent, false)
                bindPetsPage(v)
                SimpleVH(v)
            } else {
                val calendar = MonthCalendarView(parent.context).apply {
                    petIndex = MonthCalendarView.ALL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                calendarView = calendar
                calendar.render()
                SimpleVH(calendar)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) { }
    }

    private inner class SimpleVH(v: View) : RecyclerView.ViewHolder(v)

    private fun bindPetsPage(page: View) {
        val list = page.findViewById<RecyclerView>(R.id.pets_list)
        list.layoutManager = GridLayoutManager(this, 2)
        val adapter = PetCardAdapter(FeedingStore.loadPets(this), cardCallbacks)
        petAdapter = adapter
        list.adapter = adapter

        page.findViewById<Button>(R.id.add_pet).setOnClickListener {
            val newIndex = FeedingStore.addPet(this)
            if (newIndex < 0) {
                AlertDialog.Builder(this)
                    .setTitle("Limit reached")
                    .setMessage("You can track up to ${FeedingStore.MAX_PETS} pets.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                syncAll()
                list.scrollToPosition(newIndex)
            }
        }
    }
}
