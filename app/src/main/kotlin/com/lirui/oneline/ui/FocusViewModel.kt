package com.lirui.oneline.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.oneline.data.FocusRepository
import com.lirui.oneline.data.model.FocusSession
import com.lirui.oneline.focus.FocusEngine
import com.lirui.oneline.focus.FocusRules
import com.lirui.oneline.focus.FocusRun
import com.lirui.oneline.focus.FocusStore
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 专注页的状态。
 *
 * ★ 状态从 [FocusStore]（磁盘）读，不从内存构造 —— 专注页随时可能被重新创建
 *   （切 tab 后回来、旋屏、进程被回收后再进来），只有盘上那份是真相。
 *
 * ★ `nowMillis` 是 **UI 的显示时钟**，不是计时器。每 250ms 由 [tick] 推一次，
 *   只用来重绘"还剩几秒"。真正的时长永远由 `FocusRules.elapsedMillis(run, now)` 算出来，
 *   所以 UI 卡住、掉帧、被系统冻结都不会让专注时长变短或变长。
 */
class FocusViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FocusRepository(app)

    /** 进行中的专注；null = 空闲。 */
    var run by mutableStateOf(FocusStore.active(app))
        private set

    /** 显示用时钟（毫秒）。 */
    var nowMillis by mutableStateOf(System.currentTimeMillis())
        private set

    /** 空闲时选的时长（分钟）。 */
    var minutes by mutableStateOf(FocusStore.lastMinutes(app))
        private set

    /** 空闲时选的模式：true = 正计时（从 0 往上数），false = 倒计时。 */
    var countUp by mutableStateOf(FocusStore.lastCountUp(app))
        private set

    /** 统计页进度环的刻度：每日专注目标（分钟）。 */
    var dailyGoalMinutes by mutableStateOf(SettingsRepo.focusDailyGoalMinutes(app))
        private set

    /**
     * 中途结束时"太短了，要不要记"的待确认秒数。
     * null = 没有待确认的对话框；>0 = 弹窗问用户。
     */
    var askDiscardSeconds by mutableStateOf<Int?>(null)
        private set

    /**
     * 「这次是不是忘了关」的待确认秒数（> 2 小时）。
     * null = 没有待确认；>0 = 弹窗让用户选「按当前值 / 自定义 / 不计入」。
     */
    var askLongRunSeconds by mutableStateOf<Int?>(null)
        private set

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** 全部历史记录（统计页与记录列表共用）。 */
    val sessions: StateFlow<List<FocusSession>> = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 显示时钟 ----------

    /**
     * 由 UI 的 `LaunchedEffect` 驱动（页面关掉就停，不空转）。
     *
     * 时间到的那一下在这里处理：用户正看着计时器，没必要等闹钟。
     * 和闹钟撞车不会写两条 —— [FocusEngine.complete] 内部用 `takeActive` 去重。
     */
    fun tick() {
        // 空闲时不刷新：否则页面开着就每 250ms 重组一次，白烧电。
        val r = run ?: return
        val t = System.currentTimeMillis()
        nowMillis = t
        if (!r.paused && FocusRules.isFinished(r, t)) {
            viewModelScope.launch {
                if (FocusEngine.complete(getApplication(), t)) {
                    run = null
                    _toasts.tryEmit("专注完成")
                } else {
                    // 闹钟已经先一步收走了这次专注：只把本地状态对齐，不再提示。
                    run = null
                }
            }
        }
    }

    /** 从磁盘重新读一次（通知栏里点了暂停/结束之后，回到 App 要能对上）。 */
    fun syncFromStore() {
        run = FocusStore.active(getApplication())
        nowMillis = System.currentTimeMillis()
    }

    // ---------- 空闲态 ----------

    fun selectMinutes(value: Int) {
        if (value <= 0) return
        minutes = value
    }

    fun selectMode(up: Boolean) {
        countUp = up
    }

    /** 改每日目标。范围夹在 [SettingsRepo.MIN_FOCUS_GOAL_MINUTES] ~ [SettingsRepo.MAX_FOCUS_GOAL_MINUTES]。 */
    fun setDailyGoal(minutes: Int) {
        val v = minutes.coerceIn(
            SettingsRepo.MIN_FOCUS_GOAL_MINUTES,
            SettingsRepo.MAX_FOCUS_GOAL_MINUTES
        )
        dailyGoalMinutes = v
        SettingsRepo.setFocusDailyGoalMinutes(getApplication(), v)
    }

    // ---------- 动作 ----------

    fun start() {
        val t = System.currentTimeMillis()
        // 说明（label）的输入框已下线，新开的专注一律不带说明；
        // FocusRun.label 字段保留，历史记录里已有的说明照常显示。
        run = FocusEngine.start(getApplication(), minutes, "", t, countUp)
        nowMillis = t
    }

    fun pause() {
        val t = System.currentTimeMillis()
        run = FocusEngine.pause(getApplication(), t)
        nowMillis = t
    }

    fun resume() {
        val t = System.currentTimeMillis()
        run = FocusEngine.resume(getApplication(), t)
        nowMillis = t
    }

    /**
     * 用户点了「结束」。
     *
     * 四条路（口径见 [FocusRules]），**从最长到最短判**：
     *  - > 2 小时 → 弹窗问「按当前值 / 自定义 / 不计入」（像忘了关）；
     *  - ≥ 60 秒 → 直接记入，提示一句；
     *  - 1~59 秒 → 弹窗问「要记下来吗」；
     *  - 0 秒 → 什么都不留。
     */
    fun requestFinish() {
        val r = run ?: return
        val t = System.currentTimeMillis()
        val elapsed = FocusRules.elapsedSeconds(r, t)
        when {
            FocusRules.shouldAskForLongRun(elapsed) -> askLongRunSeconds = elapsed
            FocusRules.shouldRecordSilently(elapsed) -> recordAndClose(t, record = true)
            FocusRules.shouldAskBeforeDiscard(elapsed) -> askDiscardSeconds = elapsed
            else -> recordAndClose(t, record = false)
        }
    }

    /** 短时长弹窗的回答。 */
    fun answerDiscard(record: Boolean) {
        askDiscardSeconds = null
        recordAndClose(System.currentTimeMillis(), record)
    }

    /**
     * 超长弹窗的回答。
     *
     * @param record false = 「不计入」，结束但不落库。
     * @param customSeconds 「自定义」时用户填的秒数；null = 按当前值。
     */
    fun answerLongRun(record: Boolean, customSeconds: Int? = null) {
        askLongRunSeconds = null
        recordAndClose(System.currentTimeMillis(), record, customSeconds)
    }

    /** 点弹窗外面 / 返回键：只收起询问，**不结束计时**（误触不该白白丢一次专注）。 */
    fun cancelLongRun() {
        askLongRunSeconds = null
    }

    /** 统计页记录列表的删除：数据层早就备好了，这里只是把 UI 接上。 */
    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            _toasts.tryEmit("已删除记录")
        }
    }

    private fun recordAndClose(t: Long, record: Boolean, overrideSeconds: Int? = null) {
        viewModelScope.launch {
            val seconds = FocusEngine.finishEarly(getApplication(), t, record, overrideSeconds)
            run = null
            nowMillis = t
            if (seconds > 0) {
                _toasts.tryEmit("已记入 ${FocusRules.formatDuration(seconds)}")
            }
        }
    }
}
