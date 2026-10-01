package com.example.petfeeding

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared persistence layer used by BOTH the main app UI and the home-screen widget.
 *
 * Data is stored in a single SharedPreferences file as a JSON array of pets, so the
 * Activity and the AppWidgetProvider always read/write the same source of truth.
 * Data survives app close and device reboot.
 */
object FeedingStore {

    const val PET_COUNT = 4
    private const val PREFS = "pet_feeding_prefs"
    private const val KEY_PETS = "pets_json"

    data class Pet(
        val name: String,
        val history: List<Long>
    ) {
        fun lastFed(): Long? = history.maxOrNull()

        fun fedToday(): Boolean {
            val now = Calendar.getInstance()
            return history.any { isSameDay(it, now.timeInMillis) }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadPets(context: Context): MutableList<Pet> {
        val raw = prefs(context).getString(KEY_PETS, null)
        val pets = mutableListOf<Pet>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until minOf(arr.length(), PET_COUNT)) {
                    val o = arr.getJSONObject(i)
                    val name = (if (o.isNull("name")) "" else o.optString("name", ""))
                        .ifBlank { defaultName(i) }
                    val histArr = o.optJSONArray("history") ?: JSONArray()
                    val hist = ArrayList<Long>(histArr.length())
                    for (j in 0 until histArr.length()) hist.add(histArr.getLong(j))
                    pets.add(Pet(name, hist))
                }
            } catch (_: Exception) {
                pets.clear()
            }
        }
        // Normalize to exactly PET_COUNT pets
        while (pets.size < PET_COUNT) pets.add(Pet(defaultName(pets.size), emptyList()))
        return pets.subList(0, PET_COUNT).toMutableList()
    }

    private fun savePets(context: Context, pets: List<Pet>) {
        val arr = JSONArray()
        pets.forEach { p ->
            val o = JSONObject()
            o.put("name", p.name)
            val h = JSONArray()
            p.history.forEach { h.put(it) }
            o.put("history", h)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_PETS, arr.toString()).apply()
    }

    /** Append the current time to a pet's feeding history and persist. */
    fun recordFeeding(context: Context, index: Int, timeMillis: Long = System.currentTimeMillis()) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        val p = pets[index]
        pets[index] = p.copy(history = p.history + timeMillis)
        savePets(context, pets)
    }

    fun renamePet(context: Context, index: Int, name: String) {
        val pets = loadPets(context)
        if (index !in pets.indices) return
        val clean = name.trim().ifBlank { defaultName(index) }
        pets[index] = pets[index].copy(name = clean)
        savePets(context, pets)
    }

    fun defaultName(index: Int): String = "Pet ${index + 1}"

    private val fullFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())

    fun formatFull(ts: Long): String = fullFormat.format(Date(ts))

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

    private fun isSameDay(a: Long, b: Long): Boolean {
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
}
