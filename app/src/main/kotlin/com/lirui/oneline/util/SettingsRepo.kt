package com.lirui.oneline.util

import android.content.Context

/**
 * Persists user settings (theme, reminder switches) via plain
 * [android.content.SharedPreferences] so the library set stays small and stable.
 *
 * Theme modes:
 *  - FOLLOW_SYSTEM : use the OS-wide setting (default)
 *  - LIGHT         : force light
 *  - DARK          : force dark
 */
object SettingsRepo {

    private const val PREF_NAME = "oneline_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_REMIND_DUE = "remind_due"
    private const val KEY_REMIND_ADVANCE = "remind_advance"
    private const val KEY_OVERDUE_NAG = "overdue_nag"
    private const val KEY_OVERDUE_INTERVAL_HOURS = "overdue_interval_hours"
    private const val KEY_OVERDUE_PIN = "overdue_pin"
    private const val KEY_DEFAULT_TIME = "default_time"
    private const val KEY_AUTO_REPEAT = "auto_repeat"
    private const val KEY_PALETTE = "palette_id"
    private const val KEY_DARK_CARD = "dark_card_style"

    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    // ---------- theme ----------
    fun getThemeMode(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME, FOLLOW_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, mode).apply()
    }

    // ---------- colour palette ----------

    /** Selected palette id (see ui.theme.Palettes). Default 0 = 雅紫. */
    fun paletteId(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_PALETTE, 0)

    fun setPaletteId(context: Context, id: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PALETTE, id).apply()
    }

    /**
     * Dark-mode card tint style (see `ui.theme.DarkCardStyle`). Default 0 = 标准暗色,
     * which is the "clear colour, not muddied into black" look.
     */
    fun darkCardStyleId(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_DARK_CARD, 0)

    fun setDarkCardStyleId(context: Context, id: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DARK_CARD, id).apply()
    }

    // ---------- reminders ----------

    /** Master switch for all reminders (default ON). */
    fun remindersEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMINDERS_ENABLED, true)

    /** Notify exactly at the due time (到点提醒). Default ON. */
    fun remindAtDue(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMIND_DUE, true)

    /** Notify in advance (graded D-7/D-3/D-1 + exact -1h). Default ON. */
    fun remindInAdvance(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMIND_ADVANCE, true)

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    fun setRemindAtDue(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REMIND_DUE, enabled).apply()
    }

    fun setRemindInAdvance(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REMIND_ADVANCE, enabled).apply()
    }

    // ---------- overdue nag (逾期反复叮嘱) ----------

    /** Whether overdue, unfinished todos should be nagged repeatedly. Default ON. */
    fun overdueNagEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERDUE_NAG, true)

    /** Nag interval in hours. Default 24h (daily). */
    fun overdueIntervalHours(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OVERDUE_INTERVAL_HOURS, 24)

    fun setOverdueNagEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OVERDUE_NAG, enabled).apply()
    }

    fun setOverdueIntervalHours(context: Context, hours: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_OVERDUE_INTERVAL_HOURS, hours).apply()
    }

    /** Whether overdue todos are pinned to the top and highlighted red. Default ON. */
    fun overduePinEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERDUE_PIN, true)

    fun setOverduePinEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OVERDUE_PIN, enabled).apply()
    }

    // ---------- default reminder time (无日期/只写日期默认时刻) ----------

    /** Default time-of-day (minute-of-day) for a date-only todo. Default 09:00. */
    fun defaultMinuteOfDay(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DEFAULT_TIME, 9 * 60)

    fun setDefaultMinuteOfDay(context: Context, minuteOfDay: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DEFAULT_TIME, minuteOfDay).apply()
    }

    // ---------- repeating todo rollover (循环重复待办) ----------

    /** Whether a repeating todo rolls to its next occurrence instead of archiving. Default ON. */
    fun autoRepeatEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_REPEAT, true)

    fun setAutoRepeatEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_REPEAT, enabled).apply()
    }
}