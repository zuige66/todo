package com.lirui.oneline.focus

import android.content.Context
import android.content.SharedPreferences

/**
 * 进行中的专注落在 SharedPreferences 里，**不是内存状态**。
 *
 * 理由很硬：专注一开就是 25 分钟，这期间进程被系统回收、被一键清理、被用户划掉
 * 都是常态。状态只要在内存里，用户切回来看到的就是"什么都没在计时"，
 * 而通知栏里那条倒计时还在走 —— 两边对不上，数据也就丢了。
 *
 * 落盘之后：
 *  - 通知由系统 chronometer 渲染，进程死了也继续走；
 *  - 闹钟由 system_server 持有，进程死了也照样到点响；
 *  - 用户再打开 App，[active] 读回来的就是同一个 run，剩余时间一秒不差。
 */
object FocusStore {

    private const val PREF_NAME = "oneline_focus"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PLANNED = "planned_seconds"
    private const val KEY_ACCUMULATED = "accumulated_millis"
    private const val KEY_SEGMENT_START = "segment_start_millis"
    private const val KEY_PAUSED = "paused"
    private const val KEY_STARTED_AT = "started_at_millis"
    private const val KEY_LABEL = "label"
    private const val KEY_COUNT_UP = "count_up"
    private const val KEY_LAST_MINUTES = "last_minutes"
    private const val KEY_LAST_COUNT_UP = "last_count_up"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 当前有没有在跑（或暂停着）的专注。 */
    fun hasActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun active(context: Context): FocusRun? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return null
        val countUp = p.getBoolean(KEY_COUNT_UP, false)
        val planned = p.getInt(KEY_PLANNED, 0)
        // ★ 校验走 FocusRules.isUsable：正计时 planned=0 是正常的，不能当坏数据。
        //   （v1.0.2 在这里判 `planned <= 0` 直接 return null，正计时退出重进就"没了"。）
        if (!FocusRules.isUsable(planned, countUp)) {
            // 真坏数据（倒计时却没存到计划时长）：顺手清盘，别留一个永远读不出来的僵尸。
            clearActive(context)
            return null
        }
        return FocusRun(
            plannedSeconds = planned,
            accumulatedMillis = p.getLong(KEY_ACCUMULATED, 0L),
            segmentStartMillis = p.getLong(KEY_SEGMENT_START, 0L),
            paused = p.getBoolean(KEY_PAUSED, false),
            startedAtMillis = p.getLong(KEY_STARTED_AT, 0L),
            label = p.getString(KEY_LABEL, "").orEmpty(),
            countUp = countUp
        )
    }

    fun saveActive(context: Context, run: FocusRun) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_PLANNED, run.plannedSeconds)
            .putLong(KEY_ACCUMULATED, run.accumulatedMillis)
            .putLong(KEY_SEGMENT_START, run.segmentStartMillis)
            .putBoolean(KEY_PAUSED, run.paused)
            .putLong(KEY_STARTED_AT, run.startedAtMillis)
            .putString(KEY_LABEL, run.label)
            .putBoolean(KEY_COUNT_UP, run.countUp)
            .apply()
    }

    fun clearActive(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_PLANNED)
            .remove(KEY_ACCUMULATED)
            .remove(KEY_SEGMENT_START)
            .remove(KEY_PAUSED)
            .remove(KEY_STARTED_AT)
            .remove(KEY_LABEL)
            .remove(KEY_COUNT_UP)
            .apply()
    }

    /**
     * 原子地「读出来并清掉」。
     *
     * ★ 这是防重复落库的唯一闸门。结束一次专注有两个触发源：
     *   ① 闹钟到点（FocusAlarmReceiver）；
     *   ② 用户正在前台时 UI 自己判定时间到（FocusViewModel 的每秒 tick）。
     *   两条路可能同时发生 —— 谁先拿到 run 谁负责写库，后到的拿到 null 就什么都不做。
     *   没有这个闸门，同一次专注会在列表里出现两条。
     *
     * 同进程内 `@Synchronized` 足够：Receiver 与 ViewModel 都跑在 App 进程里
     * （manifest 里没有给 FocusAlarmReceiver 指定 process）。
     */
    @Synchronized
    fun takeActive(context: Context): FocusRun? {
        val run = active(context) ?: return null
        clearActive(context)
        return run
    }

    /** 上次选的时长（分钟），下次进来沿用 —— 别让用户每次都重选一遍。 */
    fun lastMinutes(context: Context): Int {
        val v = prefs(context).getInt(KEY_LAST_MINUTES, FocusRules.DEFAULT_MINUTES)
        return if (v > 0) v else FocusRules.DEFAULT_MINUTES
    }

    fun setLastMinutes(context: Context, minutes: Int) {
        if (minutes <= 0) return
        prefs(context).edit().putInt(KEY_LAST_MINUTES, minutes).apply()
    }

    /** 上次用的是不是正计时，下次进来沿用。 */
    fun lastCountUp(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_COUNT_UP, false)

    fun setLastCountUp(context: Context, countUp: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAST_COUNT_UP, countUp).apply()
    }
}
