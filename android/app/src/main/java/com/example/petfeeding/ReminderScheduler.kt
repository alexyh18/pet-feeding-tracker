package com.example.petfeeding

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules per-pet feeding reminders using an INEXACT alarm. Inexact alarms do not
 * require the special "schedule exact alarm" permission on Android 12+/14, and are the
 * recommended, battery-friendly choice for reminders.
 *
 * We schedule a single "next check" alarm; when it fires, ReminderReceiver re-evaluates
 * all pets, posts any due notifications, and schedules the next check. A BOOT_COMPLETED
 * receiver re-arms the alarm after a reboot.
 */
object ReminderScheduler {

    const val ACTION_CHECK = "com.example.petfeeding.ACTION_CHECK_REMINDERS"
    private const val REQ_CHECK = 9100

    /**
     * Arm the next reminder check. We align the check to the earliest upcoming due time
     * across all pets (clamped to the future); if nothing is due/enabled we still schedule
     * a daily check so newly-due pets get caught.
     */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        val pets = FeedingStore.loadPets(context)
        val dueTimes = pets.mapNotNull { it.nextDueMillis() }

        // Earliest future due time, or ~24h from now as a periodic fallback.
        val nextFutureDue = dueTimes.filter { it > now }.minOrNull()
        val target = nextFutureDue ?: (now + 24L * 60 * 60 * 1000)

        val pi = checkPendingIntent(context)
        // Inexact alarm that still fires in Doze (allowed without special permission).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, target, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(checkPendingIntent(context))
    }

    private fun checkPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_CHECK
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQ_CHECK, intent, flags)
    }
}
