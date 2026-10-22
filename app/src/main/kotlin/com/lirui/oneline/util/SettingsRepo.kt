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
    // "overdue_pin" (逾期置顶) is retired as of v2.1 — board order now decides placement.
    // The stale key may still sit in existing installs; it is simply never read.
    private const val KEY_DEFAULT_TIME = "default_time"
    private const val KEY_AUTO_REPEAT = "auto_repeat"
    private const val KEY_PALETTE = "palette_id"
    private const val KEY_DARK_CARD = "dark_card_style"
    private const val KEY_HOME_BOARD_ORDER = "home_board_order"
    private const val KEY_HOME_BOARD_HIDDEN = "home_board_hidden"
    private const val KEY_HOME_BUCKETS = "home_buckets"
    private const val KEY_CUSTOM_QUOTES = "custom_quotes"
    private const val KEY_KEEP_ALIVE = "keep_alive"

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

    // ---------- 沉浸式顶栏（已退役，v0.6）----------
    //
    // 浮层顶栏成为固定行为后这个开关没有意义；KEY_IMMERSIVE_TOPBAR 留在老用户的
    // SharedPreferences 里不再读取，与「overdue_pin」同一处理方式（保留脏键、永不读取）。

    // ---------- 后台常驻 (keep-alive FGS) ----------
    //
    // 只记录"用户想要什么"，不记录"服务现在在不在跑"。
    // 真实状态以 `KeepAlive.isRunning`（进程内标志）+ 系统服务列表为准 ——
    // 把两者混成一个键，就会出现"设置里是开的、服务其实没起来"的假象。

    /** 用户是否打开了「后台常驻」。默认关：它会挂一条常驻通知，不该默认替用户决定。 */
    fun keepAliveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_ALIVE, false)

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
    }

    // ---------- 提醒文案池 (custom quotes) ----------
    //
    // 存成单个字符串（`\n` 分隔）而不是 StringSet：
    // 1. StringSet 丢顺序，用户排在第一位的那句会随机跑位；
    // 2. 一条文案本身不可能含换行（通知正文是单行），所以换行是安全的分隔符。
    //
    // 语义：**键不存在 = 用内置默认池**；键存在但为空串 = 用户把文案删光了。
    // 后者不写键（见 setCustomQuotes），所以实际只有"键不存在"和"非空列表"两种状态。

    /**
     * @return null = 没自定义过，调用方应使用 [Quotes.defaults]
     */
    fun customQuotes(context: Context): List<String>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_CUSTOM_QUOTES)) return null
        val raw = prefs.getString(KEY_CUSTOM_QUOTES, "").orEmpty()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 保存自定义文案。
     *
     * 清空 = 恢复默认（删掉键），而不是"一句都没有"—— 空池子会让通知正文变成空白，
     * 那是用户不会想要的副作用。
     */
    fun setCustomQuotes(context: Context, quotes: List<String>) {
        val cleaned = quotes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val edit = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (cleaned.isEmpty()) {
            edit.remove(KEY_CUSTOM_QUOTES)
        } else {
            edit.putString(KEY_CUSTOM_QUOTES, cleaned.joinToString("\n"))
        }
        edit.apply()
    }

    // ---------- home list grouping (首页分组) ----------
    //
    // Boards are persisted as a comma-separated id list so the ORDER survives; a StringSet
    // would lose it. Buckets store an explicit empty string when the user unticks everything,
    // which must stay distinguishable from "never configured" (that one means "all on").

    /** Board order, always complete: unknown/missing ids are appended in the default order. */
    fun homeBoardOrder(context: Context): List<HomeBoard> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HOME_BOARD_ORDER, null) ?: return HomeBoard.defaultOrder
        val parsed = raw.split(",").mapNotNull { HomeBoard.byId(it.trim()) }.distinct()
        return parsed + HomeBoard.defaultOrder.filter { it !in parsed }
    }

    fun setHomeBoardOrder(context: Context, order: List<HomeBoard>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HOME_BOARD_ORDER, order.joinToString(",") { it.id }).apply()
    }

    /** Boards switched off on the home page. */
    fun hiddenBoards(context: Context): Set<HomeBoard> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HOME_BOARD_HIDDEN, null) ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { HomeBoard.byId(it.trim()) }.toSet()
    }

    fun setHiddenBoards(context: Context, hidden: Set<HomeBoard>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HOME_BOARD_HIDDEN, hidden.joinToString(",") { it.id }).apply()
    }

    /** Enabled buckets inside 有日期. Empty set = don't sub-group, render the board flat. */
    fun homeBuckets(context: Context): Set<HomeBucket> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_HOME_BUCKETS)) return HomeBucket.defaultEnabled
        val raw = prefs.getString(KEY_HOME_BUCKETS, "").orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { HomeBucket.byId(it.trim()) }.toSet()
    }

    fun setHomeBuckets(context: Context, buckets: Set<HomeBucket>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HOME_BUCKETS, buckets.joinToString(",") { it.id }).apply()
    }

    // ---------- notification title (通知标题) ----------
    //
    // v0.4 起版式从「四段拼接」改成「一个模板 + 一个完成后缀」：
    //
    //   旧： 事件 + 空格 + lead + dueWord + tail        （三段各自一个设置项，易错位）
    //   新： 事件 + 空格 + template.replace({提醒}, 状态语)
    //
    // `{提醒}` 是**占位符**，不是固定文字。原因：通知标题里的状态语随提醒类型变化
    // （提前提醒 / 1小时后到期 / 该办了 / 还没完成，别忘了哦，见 ReminderReceiver），
    // 若模板写死「该办了」，提前提醒也会说"该办了"，语义就错了。
    // 用户把 `{提醒}` 删掉是合法的 —— 那时四种提醒共用同一句固定标题，由用户自己决定。
    //
    // 旧键（notif_lead_emoji / notif_tail_emoji / notif_due_word）保留**只读**：
    // `dueWord` 仍是 DUE 状态语的取值（迁移不丢用户设置），lead/tail 只在首次迁移时
    // 拼进模板，之后不再读取。别把它们捡回来当第二套写入路径 —— 那正是"框里还有、
    // 通知里没有"这类不一致的温床。

    private const val KEY_NOTIF_TITLE_TEMPLATE = "notif_title_template"
    private const val KEY_NOTIF_LEAD = "notif_lead_emoji"
    private const val KEY_NOTIF_TAIL = "notif_tail_emoji"
    private const val KEY_NOTIF_DUE_WORD = "notif_due_word"
    private const val KEY_NOTIF_DONE = "notif_done_emoji"

    /** 模板里的状态语占位符。改这个常量等于破坏已存数据，别动。 */
    const val NOTIF_REMINDER_TOKEN = "{提醒}"

    const val DEFAULT_NOTIF_DUE_WORD = "该办了"
    const val DEFAULT_NOTIF_LEAD = "🚀🚀"
    const val DEFAULT_NOTIF_TAIL = "⏳⏳"
    const val DEFAULT_NOTIF_DONE = "🎉🎉"

    /** 模板 + 完成后缀的默认值（与迁移结果一致）。 */
    const val DEFAULT_NOTIF_TEMPLATE = DEFAULT_NOTIF_LEAD + NOTIF_REMINDER_TOKEN + DEFAULT_NOTIF_TAIL

    /**
     * 读取「标题模板」，**顺带完成旧数据迁移**。
     *
     * 迁移只在第一次读时发生（写回 `notif_title_template` 后就不再计算），
     * 所以用户之后怎么改 lead/tail 都不影响模板 —— 单一数据源。
     */
    fun notifTitleTemplate(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_NOTIF_TITLE_TEMPLATE, null)
        if (existing != null) return existing

        // 旧数据：lead + 状态语 + tail 拼成模板。空串是合法值（用户清空过），不能回退默认。
        val lead = prefs.getString(KEY_NOTIF_LEAD, DEFAULT_NOTIF_LEAD).orEmpty()
        val tail = prefs.getString(KEY_NOTIF_TAIL, DEFAULT_NOTIF_TAIL).orEmpty()
        val template = lead + NOTIF_REMINDER_TOKEN + tail
        prefs.edit().putString(KEY_NOTIF_TITLE_TEMPLATE, template).apply()
        return template
    }

    /** 一次性保存「标题模板 + 完成后缀」—— 与设置页那一个保存按钮一一对应。 */
    fun saveNotificationTitle(context: Context, template: String, done: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIF_TITLE_TEMPLATE, template)
            .putString(KEY_NOTIF_DONE, done)
            .apply()
    }

    /** 一次读全，避免每发一条通知就查三遍 SharedPreferences。 */
    fun notificationStyle(context: Context): NotificationStyle {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return NotificationStyle(
            titleTemplate = notifTitleTemplate(context),
            done = p.getString(KEY_NOTIF_DONE, DEFAULT_NOTIF_DONE).orEmpty(),
            dueWord = p.getString(KEY_NOTIF_DUE_WORD, DEFAULT_NOTIF_DUE_WORD).orEmpty()
        )
    }

    /**
     * DUE 状态语（"该办了"）—— 不再是独立设置项，迁移旧值时写入，
     * 目前只在测试通知和设置页默认值里用到。
     */
    fun setNotifDueWord(context: Context, value: String) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NOTIF_DUE_WORD, value).apply()
}

/**
 * 通知标题的样式。
 *
 * - [titleTemplate]：事件名之后的部分，`{提醒}` 会被替换成当前提醒类型的状态语；
 * - [done]：勾选完成后整条标题换成 `事件 + 空格 + done`；
 * - [dueWord]：DUE 那一类的状态语（"该办了"），模板里通过 `{提醒}` 展开。
 *
 * 任何一个字段为空串都表示「这一段不要」，不是「回退默认」。
 */
data class NotificationStyle(
    val titleTemplate: String,
    val done: String,
    val dueWord: String
)