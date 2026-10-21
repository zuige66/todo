package com.lirui.oneline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules all active reminders after device reboot. (Time zone changes intentionally
 * re-schedule because epoch alarms are absolute; a time zone change shifts wall-clock.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED ->
                ReminderScheduler.scheduleAllActive(context)
        }
    }
}