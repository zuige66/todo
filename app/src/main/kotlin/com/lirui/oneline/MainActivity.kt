package com.lirui.oneline

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lirui.oneline.backup.BackupManager
import com.lirui.oneline.focus.FocusEngine
import com.lirui.oneline.focus.FocusNotifier
import com.lirui.oneline.keepalive.DeviceCompat
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.reminder.ReminderScheduler
import com.lirui.oneline.reminder.ReminderWatchdogWorker
import com.lirui.oneline.ui.MainViewModel
import com.lirui.oneline.ui.screen.TodoApp
import com.lirui.oneline.ui.screen.UpdateAvailableDialog
import com.lirui.oneline.ui.theme.OnelineTheme
import com.lirui.oneline.update.ApkInstaller
import com.lirui.oneline.update.AppUpdate
import com.lirui.oneline.update.UpdateCheck
import com.lirui.oneline.update.UpdateService
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

    /** 当前装机的版本名：自更新检查与弹窗都要用。取不到就留空，检查会跳过。 */
    private val currentVersionName: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

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
     * main 分支自 v1.0.14 起移除了隐私同意门控 —— 那条门控只服务于国内商店上架，
     * 仍完整保留在 `release/vivo-store` 合规分支。这里在冷启动初始化里直接申请。
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

            // 本地备份恢复 → 设置项被整批写回 SharedPreferences，界面必须重读。
            // ★ 只重建 Compose 子树，**绝不 rebuild Activity**（见 BackupManager 注释：
            //   vivo 上 recreate 会打回 edge-to-edge，露出 values-night 的黑色窗口背景）。
            var settingsEpoch by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                BackupManager.restoreCompleted.collect { settingsEpoch++ }
            }

            // ── 冷启动自动检查更新（main 分支 v1.0.14 起）──
            // 只有发现新版本才弹窗；已是最新 / 无 release / 网络失败一律静默，不打扰。
            // 状态刻意放在 key(settingsEpoch) **外面**：恢复备份重建 Compose 子树时
            // 不会重复发请求，已经弹出来的窗也不会被重建冲掉。
            var autoUpdate by remember { mutableStateOf<AppUpdate?>(null) }
            LaunchedEffect(Unit) {
                if (currentVersionName.isBlank()) return@LaunchedEffect
                when (val result = UpdateService.check(currentVersionName)) {
                    is UpdateCheck.Available -> autoUpdate = result.update
                    else -> Unit
                }
            }

            // ── 冷启动「碰系统」的初始化 ──
            // 幂等：冷启动 / 进程重建都会跑，重复排闹钟只是白排一次（见 ReminderScheduler 说明）。
            LaunchedEffect(Unit) {
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

            // ★ 下面这一整块是「从 SharedPreferences 建出来的状态」。恢复备份后 epoch +1，
            //   key() 把整块销毁重建：二十多个 `remember { SettingsRepo.xxx() }` 重新读盘，
            //   TodoApp 内部的 remember（通知文案草稿 / 手势开关 / 小组件开关…）也一并刷新。
            //   Activity 与 Window 完全不动 —— 不重建系统栏，就不会出现上下黑边。
            //   （刻意不重排下面 120 行的缩进，保持这一版 diff 只有「包一层」两行，
            //     便于单独回退；key 块的作用域由首尾两处注释标出。）
            key(settingsEpoch) {
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

                // 冷启动自动检查发现的更新。与关于页手动检查那套弹窗状态独立，互不干扰。
                autoUpdate?.let { update ->
                    UpdateAvailableDialog(
                        update = update,
                        currentVersion = currentVersionName,
                        onDismiss = { autoUpdate = null },
                        onDownload = {
                            autoUpdate = null
                            ApkInstaller.downloadAndInstall(context, update.apkUrl, update.version)
                        },
                        onOpenPage = {
                            autoUpdate = null
                            openReleasePage(context, update.releaseUrl)
                        }
                    )
                }
            }
            } // key(settingsEpoch) 结束：设置状态 + OnelineTheme + TodoApp 整块
        } // setContent
    }

    /** 发布页交给浏览器；个别 ROM 没有能处理 ACTION_VIEW 的 Activity，失败静默忽略。 */
    private fun openReleasePage(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
