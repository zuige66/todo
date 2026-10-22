package com.lirui.oneline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lirui.oneline.ui.MainViewModel
import com.lirui.oneline.ui.screen.TodoApp
import com.lirui.oneline.ui.theme.OnelineTheme
import com.lirui.oneline.keepalive.DeviceCompat
import com.lirui.oneline.reminder.ReminderScheduler
import com.lirui.oneline.reminder.ReminderWatchdogWorker
import com.lirui.oneline.util.SettingsRepo

class MainActivity : androidx.activity.ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ask for notification permission on first launch (Android 13+).
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPerms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        // 冷启动对账（L2 自愈的第三道）：用户真正打开 App 时，把 DB 里"应该有"的闹钟重排一遍。
        // 覆盖的场景：被一键清理 force-stop 过、刚升级过、从备份恢复过 —— 这些情况下
        // AlarmManager 里的条目已经没了，只有重新 set 才能恢复。幂等，重复执行只是白排一次。
        ReminderScheduler.scheduleAllActive(applicationContext)
        ReminderWatchdogWorker.ensureScheduled(applicationContext)
        setContent {
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
                    onRescheduleReminders = onRescheduleReminders
                )
            }
        }
    }
}