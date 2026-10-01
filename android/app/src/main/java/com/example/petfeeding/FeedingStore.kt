package com.example.petfeeding

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared persistence layer used by the main app UI and the home-screen widgets.
 *
 * Pets are stored as a JSON array in a single SharedPreferences file, so the
 * Activity and the AppWidgetProviders always read/write the same source of truth.
 * The list is variable length: it starts with one pet and the user can add more
 * (up to MAX_PETS) or remove them (never below one). Data survives app close and
 * device reboot.
 */
object FeedingStore {

    const val MAX_PETS = 8
    private const val PREFS = "pet_feeding_prefs"
    private const val KEY_PETS = "pets_json"

    /**
     * Selectable pet icons. Only two species are supported:
     * crested gecko (🦎) and hognose snake (🐍).
     */
    val ICON_CHOICES = listOf("🦎", "🐍")

    /** Human-readable species labels, aligned with ICON_CHOICES. */
    val ICON_LABELS = listOf("🦎  Crested Gecko", "🐍  Hognose Snake")

    private val DEFAULT_ICONS = listOf("🦎", "🐍")

    /** Distinct, pastel-ish colors used to tell pets apart on the calendars. */
    val COLOR_PALETTE = listOf(
        0xFF7FD1A8.toInt(), // mint
        0xFFFF87A3.toInt(), // pink
        0xFFF2B705.toInt(), // amber
        0xFF6EC1E4.toInt(), // sky
        0xFFB084E8.toInt(), // purple
        0xFFF08A5D.toInt(), // coral
        0xFF5AC8C8.toInt(), // teal
        0xFFC0CA33.toInt()  // lime
    )

    fun defaultColor(index: Int): Int = COLOR_PALETTE[index % COLOR_PALETTE.size]

    data class Pet(
        val name: String,
        val history: List<Long>,
        val icon: String,
        /** Reminder interval in days. 0 = reminders off. */
        val intervalDays: Int,
        /** ARGB color used to represent this pet on the calendar. */
        val color: Int
    ) {
        fun lastFed(): Long? = history.maxOrNull()

        fun fedToday(): Boolean {
            val now = Calendar.getInstance()
            return history.any { isSameDay(it, now.timeInMillis) }
        }

        /**
         * When the next feeding is due, in millis, based on the last feeding and the
         * interval. null when reminders are off or there is no history yet.
         */
        fun nextDueMillis(): Long? {
            if (intervalDays <= 0) return null
            val last = lastFed() ?: return null
            return startOfDay(last) + intervalDays.toLong() * DAY_MS
        }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadPets(context: Context): MutableList<Pet> {
        val raw = prefs(context).getString(KEY_PETS, null)
        val pets = mutableListOf<Pet>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until minOf(arr.length(), MAX_PETS)) {
                    val o = arr.getJSONObject(i)
                    val name = (if (o.isNull("name")) "" else o.optString("name", ""))
                        .ifBlank { defaultName(i) }
                    val histArr = o.optJSONArray("history") ?: JSONArray()
                    val hist = ArrayList<Long>(histArr.length())
                    for (j in 0 until histArr.length()) hist.add(histArr.getLong(j))
                    val icon = (if (o.isNull("icon")) "" else o.optString("icon", ""))
                        .ifBlank { defaultIcon(i) }
                    val interval = o.optInt("intervalDays", 0).coerceAtLeast(0)
                    val color = if (o.has("color") && !o.isNull("color"))
                        o.optInt("color", defaultColor(i)) else defaultColor(i)
                    pets.add(Pet(name, hist, icon, interval, color))
                }
            } catch (_: Exception) {
                pets.clear()
            }
        }
        // Always keep at least one pet.
        if (pets.isEmpty())
            pets.add(Pet(defaultName(0), emptyList(), defaultIcon(0), 0, defaultColor(0)))
        return pets
    }

    fun petCount(context: Context): Int = loadPets(context).size

    private fun savePets(context: Context, pets: List<Pet>) {
        val arr = JSONArray()
        pets.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            val h = JSONArray()
            p.history.forEach { h.put(it) }
            o.put("history", h)
            o.put("icon", p.icon)
            o.put("intervalDays", p.intervalDays)
            o.put("color", p.color)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_PETS, arr.toString()).apply()
    }

    /** Add a new pet with sensible defaults. No-op at MAX_PETS. Returns new index or -1. */
    fun addPet(context: Context): Int {
        val pets = loadPets(context)
        if (pets.size >= MAX_PETS) return -1
        val i = pets.size
        pets.add(Pet(defaultName(i), emptyList(), defaultIcon(i), 0, defaultColor(i)))
        savePets(context, pets)
        return i
    }

    /** Remove a pet. Keeps at least one pet. */
    fun removePet(context: Context, index: Int) {
        val pets = loadPets(context)
        if (pets.size <= 1 || index !in pets.indices) return
        pets.removeAt(index)
        savePets(context, pets)
    }

    /** Append the current time to a pet's feeding history and persist. */
    fun recordFeeding(context: Context, index: Int, timeMillis: Long = System.currentTimeMillis()) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        val p = pets[index]
        pets[index] = p.copy(history = p.history + timeMillis)
        savePets(context, pets)
    }

    /**
     * Toggle today's feeding: if the pet was already fed today, remove ALL of today's
     * entries (cancel); otherwise record a feeding now.
     *
     * @return true if a feeding was recorded, false if today's feeding was cancelled.
     */
    fun toggleTodayFeeding(context: Context, index: Int): Boolean {
        val pets = loadPets(context)
        if (index !in pets.indices) return false
        val p = pets[index]
        val now = System.currentTimeMillis()
        return if (p.fedToday()) {
            val filtered = p.history.filterNot { isSameDay(it, now) }
            pets[index] = p.copy(history = filtered)
            savePets(context, pets)
            false
        } else {
            pets[index] = p.copy(history = p.history + now)
            savePets(context, pets)
            true
        }
    }

    /** Remove a single feeding timestamp (used by the history/calendar views). */
    fun removeFeeding(context: Context, index: Int, timeMillis: Long) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        val p = pets[index]
        pets[index] = p.copy(history = p.history.filterNot { it == timeMillis })
        savePets(context, pets)
    }

    fun renamePet(context: Context, index: Int, name: String) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        val clean = name.trim().ifBlank { defaultName(index) }
        pets[index] = pets[index].copy(name = clean)
        savePets(context, pets)
    }

    fun setIcon(context: Context, index: Int, icon: String) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        pets[index] = pets[index].copy(icon = icon)
        savePets(context, pets)
    }

    fun setInterval(context: Context, index: Int, days: Int) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        pets[index] = pets[index].copy(intervalDays = days.coerceAtLeast(0))
        savePets(context, pets)
    }

    fun setColor(context: Context, index: Int, color: Int) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        pets[index] = pets[index].copy(color = color)
        savePets(context, pets)
    }

    fun defaultName(index: Int): String = "Pet ${index + 1}"

    fun defaultIcon(index: Int): String =
        DEFAULT_ICONS.getOrElse(index) { ICON_CHOICES[index % ICON_CHOICES.size] }

    // ---- Date formatting / helpers ----

    private val fullFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())

    fun formatFull(ts: Long): String = fullFormat.format(Date(ts))
    fun formatTime(ts: Long): String = timeFormat.format(Date(ts))

    /**
     * Short label for the widget, e.g. "Today 14:20", "Yesterday 09:05", "09.28 18:40",
     * or "Not fed yet" when there is no history.
     */
    fun formatShort(ts: Long?): String {
        if (ts == null) return "Not fed yet"
        val now = Calendar.getInstance().timeInMillis
        return when {
            isSameDay(ts, now) -> "Today ${timeFormat.format(Date(ts))}"
            isYesterday(ts, now) -> "Yesterday ${timeFormat.format(Date(ts))}"
            else -> "${dateFormat.format(Date(ts))} ${timeFormat.format(Date(ts))}"
        }
    }

    /** Set of day-start millis for every day THIS pet has at least one feeding. */
    fun fedDayStarts(pet: Pet): Set<Long> =
        pet.history.map { startOfDay(it) }.toSet()

    /** Set of day-start millis where ANY pet was fed (for the combined calendar). */
    fun allFedDayStarts(pets: List<Pet>): Set<Long> =
        pets.flatMap { it.history }.map { startOfDay(it) }.toSet()

    /** Icons of pets fed on a given day-start (for calendar day detail). */
    fun iconsFedOn(pets: List<Pet>, dayStart: Long): List<String> =
        pets.filter { p -> p.history.any { startOfDay(it) == dayStart } }.map { it.icon }

    /**
     * Colors (one per pet) that were fed on a given day, in pet order. Used to paint
     * a calendar cell split into N equal stripes when several pets were fed that day.
     */
    fun colorsFedOn(pets: List<Pet>, dayStart: Long): List<Int> =
        pets.filter { p -> p.history.any { startOfDay(it) == dayStart } }.map { it.color }

    /** Map of day-start -> list of pet colors fed that day, across the whole list. */
    fun colorsByDay(pets: List<Pet>): Map<Long, List<Int>> {
        val map = HashMap<Long, MutableList<Int>>()
        pets.forEach { p ->
            val days = p.history.map { startOfDay(it) }.toSet()
            days.forEach { d -> map.getOrPut(d) { mutableListOf() }.add(p.color) }
        }
        return map
    }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(a: Long, b: Long): Boolean {
        val cb = Calendar.getInstance().apply {
            timeInMillis = b
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(a, cb.timeInMillis)
    }

    fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}
