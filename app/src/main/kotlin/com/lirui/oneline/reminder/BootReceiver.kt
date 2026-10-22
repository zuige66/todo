package com.lirui.oneline.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules all active reminders after device reboot.
 *
 * Alarms do not survive a reboot, so they must be rebuilt from the DB.
 *
 * The manifest used to also listen for `TIMEZONE_CHANGED` but this receiver never handled
 * it. Now that every date/time is resolved in a single pinned zone (北京时间, see
 * `AppTime.ZONE`) a device time-zone change no longer shifts what a stored
 * `dueAtMillis` means — the epoch stays valid — so the action was dropped rather than
 * implemented. Keep this in mind if that zone ever becomes user-configurable.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED ->
                ReminderScheduler.scheduleAllActive(context)
        }
    }
}