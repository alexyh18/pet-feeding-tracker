package com.example.petfeeding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are cleared on reboot, so re-arm the reminder check after the device boots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderReceiver.ensureChannel(context)
            ReminderScheduler.scheduleNext(context)
        }
    }
}
