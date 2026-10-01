package com.example.petfeeding

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Fires when a reminder check alarm goes off (and on boot, via BootReceiver re-arm).
 * Posts a notification for every pet that is due to be fed today, then schedules the
 * next check.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)

        val now = System.currentTimeMillis()
        val pets = FeedingStore.loadPets(context)

        pets.forEachIndexed { index, pet ->
            val due = pet.nextDueMillis() ?: return@forEachIndexed
            // Due today or overdue, and not already fed today.
            if (due <= now && !pet.fedToday()) {
                postNotification(context, index, pet)
            }
        }

        // Re-arm the next check.
        ReminderScheduler.scheduleNext(context)
    }

    private fun postNotification(context: Context, index: Int, pet: FeedingStore.Pet) {
        // Respect the Android 13+ runtime notification permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val contentPi = PendingIntent.getActivity(context, index, openIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🐾 Time to feed ${pet.icon} ${pet.name}")
            .setContentText("${pet.name} is due for a feeding today.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_BASE_ID + index, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and notify; ignore.
        }
    }

    companion object {
        const val CHANNEL_ID = "feeding_reminders"
        private const val NOTIF_BASE_ID = 7000

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Feeding reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminds you when a pet is due to be fed"
                }
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
        }
    }
}
