package com.lirui.oneline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lirui.oneline.focus.FocusEngine
import com.lirui.oneline.focus.FocusNotifier
import com.lirui.oneline.keepalive.DeviceCompat
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.reminder.ReminderScheduler
import com.lirui.oneline.reminder.ReminderWatchdogWorker
import com.lirui.oneline.ui.MainViewModel
import com.lirui.oneline.ui.screen.PrivacyConsentDialog
import com.lirui.oneline.ui.screen.PrivacyPolicyScreen
import com.lirui.oneline.ui.screen.TodoApp
import com.lirui.oneline.ui.theme.OnelineTheme
import com.lirui.oneline.util.SettingsRepo

class MainActivity : androidx.activity.ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /**
     * 从通知点进来的待办 id（-1 = 无）。放在 Activity 字段而不是 Composable state：
     * [onNewIntent]（singleTask 复用实例时走这里）也要能写它，而那边没有重组上下文。
     * 用 compose runtime 的 mutableStateOf 只是为了让 setContent 里的读取是响应式的。
     */
    private val pendingTodoId = mutableStateOf(-1L)

    /** 从专注通知点进来（true = 打开专注页）。与 [pendingTodoId] 同一套消费/复位方式。 */
    private val pendingFocus = mutableStateOf(false)

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    /**
     * 解析「点通知打开 App」的 intent：action = OPEN_TODO 且带 todoId 时记录，
     * 交给 TodoApp 滚到对应行并短暂高亮（v0.9.3 补丁，P2-4）。
     *
     * 在此之前这个 extra 是**死数据** —— 通知一直带着 todoId，但没有任何一侧读过。
     */
    private fun handleIntent(intent: Intent?) {
        // 专注通知（进行中 / 已完成）点进来 → 直接铺开专注页。
        // 这两条通知的 action 是 FocusNotifier.ACTION_OPEN_FOCUS，不带 todoId。
        if (intent?.action == FocusNotifier.ACTION_OPEN_FOCUS) {
            pendingFocus.value = true
            return
        }
        if (intent?.action != NotificationHelper.ACTION_OPEN_TODO) return
        val id = intent.getLongExtra(NotificationHelper.EXTRA_TODO_ID, -1L)
        // 测试通知的 todoId 是 0（不对应任何待办），忽略。
        if (id > 0L) pendingTodoId.value = id
    }

    /** singleTask：已有实例复用走这里（按 Home 键后再点通知）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 必须替换 instance intent：否则下次 getIntent() 拿到的还是旧 action，
        // 旋屏重建后会重新定位到上一条通知的待办。
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 申请通知权限（Android 13+）。
     *
     * ★ v1.0.0 起**只能在用户同意隐私政策之后调用**。原来是 onCreate 里直接申请 ——
     *   「未征得同意先申请权限」是商店审核的常见拒审理由（个人信息保护法要求
     *   处理个人信息前先告知并取得同意，权限申请属于处理行为的前置动作）。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { requestPerms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // intent 解析与权限无关，放在同意门控之外：从通知点进来时要能定位到那一条。
        handleIntent(intent)

        setContent {
            val appContext = applicationContext
            var accepted by remember { mutableStateOf(SettingsRepo.privacyAccepted(appContext)) }
            var showPolicy by remember { mutableStateOf(false) }

            // ── 未同意隐私政策：只渲染弹窗，主界面完全不组合 ──
            // 这样能保证「同意之前不申请任何权限、不排任何闹钟、不产生任何系统副作用」。
            if (!accepted) {
                if (showPolicy) {
                    // 看政策时把同意弹窗整个撤掉，两个 Dialog 不叠着显示：
                    // 叠着会让返回键归属含糊，而且关掉政策页后弹窗重新出现，语义更清楚。
                    PrivacyPolicyScreen(onBack = { showPolicy = false })
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {}
                    PrivacyConsentDialog(
                        onAgree = {
                            SettingsRepo.setPrivacyAccepted(appContext)
                            accepted = true
                        },
                        // 不同意就真的退出 —— 不能只关弹窗然后继续用（审核明确要求）。
                        onDisagree = { finish() },
                        onViewPolicy = { showPolicy = true }
                    )
                }
                return@setContent
            }

            // ── 已同意：做一次「碰系统」的初始化，然后进主界面 ──
            // 幂等：冷启动 / 进程重建都会跑，重复排闹钟只是白排一次（见 ReminderScheduler 说明）。
            LaunchedEffect(accepted) {
                requestNotificationPermissionIfNeeded()
                // 冷启动对账（L2 自愈的第三道）：用户真正打开 App 时，把 DB 里"应该有"的闹钟重排一遍。
                // 覆盖的场景：被一键清理 force-stop 过、刚升级过、从备份恢复过 —— 这些情况下
                // AlarmManager 里的条目已经没了，只有重新 set 才能恢复。
                ReminderScheduler.scheduleAllActive(appContext)
                ReminderWatchdogWorker.ensureScheduled(appContext)
                // 专注同理：闹钟条目不落盘，冷启动 / 升级 / 重启后要把「还在跑的专注」
                // 的到点闹钟和常驻通知重新挂上，否则计时会一直走、永远不响。
                FocusEngine.restore(appContext, System.currentTimeMillis())
            }

            // Theme preference state (follow-system / light / dark).
            var themeMode by remember { mutableStateOf(SettingsRepo.getThemeMode(this)) }
            val onSetThemeMode: (Int) -> Unit = { mode ->
                themeMode = mode
                SettingsRepo.setThemeMode(this, mode)
            }
            // Colour palette (one of the built-in M3E / vivid presets).
            var paletteId by remember { mutableStateOf(SettingsRepo.paletteId(this)) }
            val onSetPalette: (Int) -> Unit = { id ->
                paletteId = id
                SettingsRepo.setPaletteId(this, id)
            }
            // Dark-mode card tint style (标准暗色 / 淡彩 / 不染色).
            var darkCard by remember { mutableStateOf(SettingsRepo.darkCardStyleId(this)) }
            val onSetDarkCard: (Int) -> Unit = { id ->
                darkCard = id
                SettingsRepo.setDarkCardStyleId(this, id)
            }
            // Reminder switches.
            var remindersEnabled by remember { mutableStateOf(SettingsRepo.remindersEnabled(this)) }
            var remindAtDue by remember { mutableStateOf(SettingsRepo.remindAtDue(this)) }
            var remindInAdvance by remember { mutableStateOf(SettingsRepo.remindInAdvance(this)) }
            // Overdue/divergence switches.
            var overdueNag by remember { mutableStateOf(SettingsRepo.overdueNagEnabled(this)) }
            var overdueInterval by remember { mutableStateOf(SettingsRepo.overdueIntervalHours(this)) }
            // Home grouping: board order / visibility + the buckets inside 有日期.
            var boardOrder by remember { mutableStateOf(SettingsRepo.homeBoardOrder(this)) }
            var hiddenBoards by remember { mutableStateOf(SettingsRepo.hiddenBoards(this)) }
            var homeBuckets by remember { mutableStateOf(SettingsRepo.homeBuckets(this)) }
            var defaultMinute by remember { mutableStateOf(SettingsRepo.defaultMinuteOfDay(this)) }
            var autoRepeat by remember { mutableStateOf(SettingsRepo.autoRepeatEnabled(this)) }
            val onRescheduleReminders: () -> Unit = {
                ReminderScheduler.scheduleAllActive(applicationContext)
            }
            OnelineTheme(
                darkTheme = when (themeMode) {
                    SettingsRepo.DARK -> true
                    SettingsRepo.LIGHT -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                paletteId = paletteId,
                darkCardStyle = com.lirui.oneline.ui.theme.DarkCardStyle.byId(darkCard)
            ) {
                val context = LocalContext.current
                TodoApp(
                    vm,
                    onExactAlarmSettings = {
                        // 带 package Uri 才能直达本应用；不认这个 action 的 ROM 会自动退到应用详情页。
                        DeviceCompat.openExactAlarmSettings(context)
                    },
                    themeMode = themeMode,
                    onSetThemeMode = onSetThemeMode,
                    paletteId = paletteId,
                    onSetPalette = onSetPalette,
                    darkCardStyleId = darkCard,
                    onSetDarkCardStyle = onSetDarkCard,
                    remindersEnabled = remindersEnabled,
                    onSetRemindersEnabled = { on ->
                        remindersEnabled = on
                        SettingsRepo.setRemindersEnabled(this, on)
                        onRescheduleReminders()
                    },
                    remindAtDue = remindAtDue,
                    onSetRemindAtDue = { on ->
                        remindAtDue = on
                        SettingsRepo.setRemindAtDue(this, on)
                        onRescheduleReminders()
                    },
                    remindInAdvance = remindInAdvance,
                    onSetRemindInAdvance = { on ->
                        remindInAdvance = on
                        SettingsRepo.setRemindInAdvance(this, on)
                        onRescheduleReminders()
                    },
                    overdueNag = overdueNag,
                    onSetOverdueNag = { on ->
                        overdueNag = on
                        SettingsRepo.setOverdueNagEnabled(this, on)
                        onRescheduleReminders()
                    },
                    overdueInterval = overdueInterval,
                    onSetOverdueInterval = { hours ->
                        overdueInterval = hours
                        SettingsRepo.setOverdueIntervalHours(this, hours)
                        onRescheduleReminders()
                    },
                    boardOrder = boardOrder,
                    onSetBoardOrder = { order ->
                        boardOrder = order
                        SettingsRepo.setHomeBoardOrder(this, order)
                    },
                    hiddenBoards = hiddenBoards,
                    onSetHiddenBoards = { hidden ->
                        hiddenBoards = hidden
                        SettingsRepo.setHiddenBoards(this, hidden)
                    },
                    homeBuckets = homeBuckets,
                    onSetHomeBuckets = { buckets ->
                        homeBuckets = buckets
                        SettingsRepo.setHomeBuckets(this, buckets)
                    },
                    defaultMinuteOfDay = defaultMinute,
                    onSetDefaultMinuteOfDay = { mo ->
                        defaultMinute = mo
                        SettingsRepo.setDefaultMinuteOfDay(this, mo)
                        // Existing active todos keep their current due; new date-only ones use it.
                        onRescheduleReminders()
                    },
                    autoRepeat = autoRepeat,
                    onSetAutoRepeat = { on ->
                        autoRepeat = on
                        SettingsRepo.setAutoRepeatEnabled(this, on)
                    },
                    onRescheduleReminders = onRescheduleReminders,
                    // 点通知打开：滚到对应待办并高亮（P2-4）。
                    pendingTodoId = pendingTodoId.value,
                    onPendingTodoHandled = { pendingTodoId.value = -1L },
                    // 点专注通知打开：直接进专注页。
                    pendingFocus = pendingFocus.value,
                    onPendingFocusHandled = { pendingFocus.value = false }
                )
            }
        }
    }
}
