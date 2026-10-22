# 后台常驻 / 开机自启 / 提醒可靠性 —— 方案与红线

> 适用：Oneline（`com.lirui.oneline`，minSdk 26 / targetSdk 36，Compose + AlarmManager）
> 写作时间：2026-10-22。文中「事实」= 官方文档 / 源码 / 实测；「推断」= 厂商行为差异，需真机验证。

---

## 0. 结论先行

| 你的诉求 | 结论 | 实际收益 |
|---|---|---|
| 开机自启 | **必须做**，已有 `BootReceiver`，但**漏了升级重建**（见 §3.3，这是当前最大的漏洞） | 高 |
| 进程常驻（前台服务） | **对本 App 收益接近 0**：闹钟不依赖进程。可作为「用户可关闭的增强开关」存在 | 低 |
| 最近任务隐藏 | **不建议**。`excludeFromRecents` 不提升存活，只会让用户找不到 App | 负 |
| 电池优化白名单 | **建议做**，但要走「解释 + 用户点」的流程，不能强制 | 中（Doze 下定时更稳） |
| 仿李跳跳 | **不能也不需要**。它靠无障碍服务，Play 政策禁止非无障碍用途，且它不是"杀不死" | 负 |

**一句话**：你这个 App 的可靠性来自「闹钟排对了 + 被清掉后能重建」，而不是「进程不死」。
把精力花在 §3.3（重建矩阵）和 §7（引导用户放行），比折腾保活有效一个数量级。

---

## 1. 先纠一个前提：「通知提醒依赖进程存活」是误解

`AlarmManager.setExactAndAllowWhileIdle()` 的 PendingIntent 由 **system_server 里的
AlarmManagerService 持有**，不在你的进程里。到点时系统做的是：

```
AlarmManagerService → 找到 PendingIntent → 你的进程已死？
    → 是：拉起进程（fork zygote → Application.onCreate）→ 再投递 BroadcastReceiver
    → 否：直接投递
```

所以：

- **进程被 LMK 回收、被用户从最近任务划掉，都不影响已排的闹钟。**
- 闹钟真正的三个敌人是：
  1. **force-stop**（`am force-stop`、部分 ROM 的"一键清理"、用户在设置里"强行停止"）
     → 系统会**取消该包的全部闹钟**并进入 stopped state，直到用户再次手动打开 App；
  2. **升级安装**（`PackageInstaller` 会杀进程并清闹钟，且不会自动重建）；
  3. **排程本身错了**（`USE_EXACT_ALARM` 没生效 → 降级成 `setAndAllowWhileIdle` → Doze 下被延后）。
- 「进程存活」只影响两件事：**通知栏按钮点了之后响应多快**（其实也是广播拉起）、
  以及 `NotificationActionReceiver` 里读 DB 的那几十毫秒。

> 所以：保活方案的定位是「提升即时响应手感 + 心理安慰」，**不是**「让提醒能响」。
> 如果你的提醒曾经漏过，先查 §1 的三个敌人，别去查保活。

---

## 2. 整体架构建议

### 2.1 三种机制的分工（不是三选一，是各干各的）

| 机制 | 适合做什么 | 精度 | Doze 下 | 本 App 用法 |
|---|---|---|---|---|
| `AlarmManager` + `BroadcastReceiver` | **单点精确定时**（某天某时某分提醒） | 精确到秒 | `setExactAndAllowWhileIdle` 可用，但有 9 分钟/次的节流；`setAlarmClock` 无节流 | **主管道**（已在用） |
| `WorkManager` | 有约束的、可延迟的后台任务（重试、同步、巡检） | 最小 15 分钟周期，**不保证准点** | 会被批量调度 | **只做兜底巡检**，不做提醒 |
| 前台服务 | 长时任务（下载、播放、导航）+ 抬高 oom_adj | — | 需要 | 仅「可选常驻开关」 |

**别用 WorkManager 做提醒**：它的语义是 "eventually"，官方文档明确说周期任务最小间隔 15 分钟、
且执行时间由系统批量决定。用它排 14:30 的会，可能 15:10 才响。

### 2.2 推荐的三层结构

```
L1 主管道（必须有）
  DB 变更 / 开机 / 升级 / 改时间
        ↓ scheduleAllActive()
  ReminderScheduler → AlarmManager.setExactAndAllowWhileIdle(每条待办 1~4 个 PendingIntent)
        ↓ 到点
  ReminderReceiver(goAsync) → NotificationHelper.postReminder()
        ↓ 点「完成」
  NotificationActionReceiver → TodoRepository.archive()

L2 自愈（必须有）
  BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET → 全量重建
  App 每次冷启动 → reconcile（对比 DB 与内存态，缺的补排）
  WorkManager 每 12h 巡检 → 找出「已过 DUE 时间但通知不存在」的待办，补发一次

L3 增强（可选，默认关）
  用户打开「后台增强」→ KeepAliveService（specialUse 前台服务，START_STICKY）
  + 引导忽略电池优化 + 引导厂商自启动/后台白名单
```

**关键设计原则**：L1 的每一条闹钟都是**独立 PendingIntent**（你现在的 `requestCode = todoId*10 + kind`
已经做对了）。不要用"一条总闹钟 + 循环扫表"，那样一次失败就全哑。

---

## 3. AndroidManifest.xml 需要加什么

### 3.1 权限

```xml
<!-- 已有，保持 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- 新增：引导忽略电池优化（Play 政策受限，见 §10） -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- 新增：仅当启用 L3 常驻服务时需要（Android 14+ 强制 fgsType + 对应权限） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

> 注意：`FOREGROUND_SERVICE` 你已经有了（更新下载用）。`FOREGROUND_SERVICE_DATA_SYNC` 也有了，
> 但 **dataSync 不能在 BOOT_COMPLETED 里启动**（target 35+ 会抛
> `ForegroundServiceStartNotAllowedException`，见 §5.4）。

### 3.2 Service / Receiver

```xml
<!-- 升级重建（当前缺失，见 §3.3） -->
<receiver
    android:name=".reminder.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIME_SET" />
    </intent-filter>
</receiver>

<!-- L3 常驻服务：specialUse -->
<service
    android:name=".keepalive.KeepAliveService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="日程提醒常驻，用于及时投递提醒与响应通知栏操作" />
</service>
```

**静态注册能不能收？** `BOOT_COMPLETED`、`MY_PACKAGE_REPLACED`、`TIME_SET` 三个都在
Android 8 隐式广播豁免名单里，可以静态注册，不受后台广播限制。
`LOCKED_BOOT_COMPLETED` 需要 `android:directBootAware="true"`，但你的 Room DB 在
credential-encrypted 存储里，那时**读不到数据**，所以**不做**（除非将来要"开机即响的闹钟"）。

### 3.3 ★ 当前最大的漏洞：升级后提醒全丢

`PackageInstaller` 安装新版本时会杀进程，**该包所有 AlarmManager 闹钟被清空**。
你现在的 `BootReceiver` 只监听 `BOOT_COMPLETED`，所以：

> **用户每升一次级，在他下次打开 App 之前，所有提醒都不会响。**

这不是理论——这也是「明明设了提醒却没响」最常见的原因之一（仅次于精确闹钟权限）。

修复只要一行 intent-filter（见 §3.2）+ 一个 `when` 分支：

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // 用户改了系统时间：RTC 闹钟是绝对 epoch，墙钟变了就得重排
            Intent.ACTION_TIME_CHANGED -> ReminderScheduler.scheduleAllActive(context)
        }
    }
}
```

---

## 4. 开机自启动完整实现

### 4.1 步骤

1. Manifest 声明 `RECEIVE_BOOT_COMPLETED`（已有）。
2. 静态注册 receiver（见 §3.2）。
3. `onReceive` 里**不要做重活**：`scheduleAllActive()` 内部已经是 `CoroutineScope(IO)`，
   但 `BroadcastReceiver.onReceive` 的进程优先级只在这几秒内有效——**保险做法是同样套 `goAsync()`**，
   或者用 `WorkManager` 起一个 `OneTimeWorkRequest` 做重建（推荐，系统会给足时间）。

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try { ReminderScheduler.scheduleAllActiveBlocking(context) } finally { pending.finish() }
    }
}
```

> `scheduleAllActive` 现在返回 `Unit` 且内部自己 launch，没法 await。建议给它加一个
> `suspend fun scheduleAllActiveBlocking()`（把 `active.forEach` 提到 suspend 上下文里），
> receiver 调用阻塞版，其余调用点继续用 fire-and-forget 版。

4. 用户装完 App **从未启动过** 时收不到 `BOOT_COMPLETED`（Android 3.1+ 的 stopped state）——
   这对你无害（没数据可排），但 UI 上要说明"首次请先打开一次"。
5. **国产 ROM 会拦**：MIUI/HyperOS 的「自启动」权限默认关闭时，`BOOT_COMPLETED` 直接不投递。
   只能引导用户去开（§7），没有 API 能绕过。

### 4.2 验证

```bash
export MSYS_NO_PATHCONV=1
ADB=C:/Users/lirui/AppData/Local/Android/Sdk/platform-tools/adb.exe

# 不重启设备模拟开机广播
$ADB shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.lirui.oneline
# 模拟升级
$ADB shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -p com.lirui.oneline
# 看闹钟是否排上（关键：RTC_WAKEUP 条目 + 时间点对不对）
$ADB shell dumpsys alarm | grep -A3 oneline
```

---

## 5. 前台服务（L3，可选）

### 5.1 先问一句：真的需要吗

前台服务给你的是 **oom_adj 提升**（`FOREGROUND_SERVICE_ADJ`，比 cached 的 8~15 靠前），
让 LMK 更晚回收、让系统更少冻结它。但它：

- 挡不住 **force-stop**（一脚踹死，adj 再高也没用）；
- 挡不住 MIUI/HyperOS 的「清理」（会按包杀进程树）；
- **必须挂一条常驻通知**，用户看得见，也会被骂；
- Android 13+ 用户可以从通知栏/任务管理器**主动停掉**你的 FGS。

结论：**默认关闭**。做成设置页开关「后台增强（会有常驻通知）」，让在意的人自己开。

### 5.2 实现要点

```kotlin
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null   // 不用 bind

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification(), fgsType())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 重建通知（通知被系统清掉时补回来）
        startForeground(NOTIF_ID, buildNotification(), fgsType())
        return START_STICKY   // 被杀后系统重建：intent == null
    }

    /** Android 14+ 必须显式传 type，且要与 Manifest 声明一致，否则抛异常。 */
    private fun fgsType(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun buildNotification(): Notification {
        // 单独的 LOW 渠道，绝不能复用 "reminders"(IMPORTANCE_HIGH)——否则每次重建都响一声
        val channelId = "keepalive"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "后台常驻", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = "保持提醒准时投递，可在此关闭"
                }
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("提醒服务运行中")
            .setContentText("点击查看待办")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
    }

    override fun onDestroy() {
        // 用户手动停止 / 关机：不要在这里偷偷 startForegroundService 复活自己
        super.onDestroy()
    }
}
```

启动（**只能在前台或已有豁免时启动**）：

```kotlin
fun startKeepAlive(ctx: Context) {
    val i = Intent(ctx, KeepAliveService::class.java)
    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
}
```

### 5.3 版本适配清单

| 版本 | 要求 |
|---|---|
| 8.0 / API 26 | 必须 `startForegroundService()`，且 **5 秒内** 调 `startForeground()`，否则 ANR |
| 9 / API 28 | 前台服务需要 `FOREGROUND_SERVICE` 权限（普通权限，装完即有） |
| 12 / API 31 | **后台启动 FGS 受限**：从后台 `startForegroundService` 会抛 `ForegroundServiceStartNotAllowedException`。只能在 Activity 可见时启动，或走豁免（闹钟到点后短暂白名单、`ACTION_BOOT_COMPLETED`、用户点通知） |
| 13 / API 33 | `POST_NOTIFICATIONS` 运行时权限；通知被拒 → FGS 仍在跑但用户看不见（Play 要求这种情况下要能降级解释） |
| 14 / API 34 | **必须声明 `foregroundServiceType`**，且 `startForeground(id, notif, type)` 传 type，否则 `MissingForegroundServiceTypeException`；还要有对应 `FOREGROUND_SERVICE_<TYPE>` 权限 |
| 15 / API 35 | `BOOT_COMPLETED` **不能**启 `dataSync`/`camera`/`mediaPlayback`/`phoneCall`/`mediaProjection`/`microphone`，抛 `ForegroundServiceStartNotAllowedException`。`specialUse`/`location`/`connectedDevice` 等仍可。另外 `SYSTEM_ALERT_WINDOW` 豁免要求"有一个可见的悬浮窗" |
| 16 / API 36 | 继承 15 的限制（你 target 36）；`specialUse` 仍可用，但 Play 审核会更严 |

> 你用 `specialUse` 而不是 `dataSync`，正是因为后者在开机场景被禁、且有 6 小时/24h 超时上限。

### 5.4 一个反直觉点：`START_STICKY` 对 FGS 意义有限

`START_STICKY` 只保证「进程被 LMK 杀掉后服务重建」，而且**重建时不会重新进前台**——
需要你在 `onStartCommand` 里再 `startForeground()`（上面代码已做）。
但如果是 force-stop 或用户主动 stop，`START_STICKY` 完全无效（系统不会重建）。

**不要**在 `onTaskRemoved()` 里 `startForegroundService` 复活自己：Android 12+ 大概率被拒，
而且属于「对抗用户操作」，Play 审核与厂商风控都会盯。

---

## 6. 从最近任务列表隐藏

### 6.1 `excludeFromRecents` 到底做什么

| | 作用 | 生效范围 |
|---|---|---|
| `android:excludeFromRecents="true"`（Activity 属性） | 该 Activity 所在的 **Task** 不出现在 Recents | 冷启动即生效，但 **只在它是 task root 时有效** |
| `Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` | 同上，按次传入 | 每次启动 |
| `ActivityManager.AppTask.setExcludeFromRecents(boolean)` | 动态改 | 需要先 `getAppTasks()` 拿到自己的 task |
| `android:autoRemoveFromRecents="true"` | task 里所有 Activity 都 finish 后自动从 Recents 移除 | 更适合"用完即走" |

代码：

```kotlin
// 动态设置（例如用户在设置里开「隐身模式」后）
val am = getSystemService(ActivityManager::class.java)
am.appTasks.firstOrNull { it.taskInfo.baseIntent.component?.packageName == packageName }
    ?.setExcludeFromRecents(true)
```

### 6.2 三个必须知道的限制

1. **不提升存活**。Recents 只是 `RecentTasks` 里的记录，删不删它和进程/服务生死无关。
2. **划掉 Recents 卡片 ≠ 杀进程**，但会 `finish` 掉你的 Activity，多数 ROM 顺手杀进程——
   而**你的闹钟照样在**（见 §1）。
3. **和「锁定后台」互斥**：MIUI/EMUI 的"长按卡片上锁"是 ROM 功能，任务根本不在列表里，
   用户连上锁的入口都没有。这在国产 ROM 上是**负收益**。

### 6.3 我的建议：不隐藏

日程 App 的主界面必须能被用户找到。真要「不容易被划掉」，唯一有效的做法是引导用户在
ROM 的最近任务里**长按上锁**（MIUI：下拉卡片 → 出现锁图标；EMUI：下拉卡片 → 锁定），
这是 ROM 能力，**没有标准 API**。

> 顺带澄清一个常见误解：**Service 本来就不在 Recents 里**。Recents 只列 Task（Activity 栈）。
> 所以"隐藏后台任务"这个说法本身，对纯服务型 App 是不成立的——李跳跳在列表里看不见，
> 不是因为它隐藏了，而是因为它压根没有常驻 Activity（见 §8）。

---

## 7. 电池优化白名单 + 厂商后台限制

### 7.1 标准流程（Doze 白名单）

```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

```kotlin
/** 是否已在 Doze 白名单 */
fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(ctx.packageName)

/**
 * 直接弹系统对话框（"允许 xxx 在后台不受限制？"）。
 * ⚠️ Play 政策只允许核心功能确实依赖它的应用这么做，且必须先解释再弹。
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBattery(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
    if (isIgnoringBattery(activity)) return
    launcher.launch(Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${activity.packageName}")
    ))
}

/** 更温和的替代：打开"电池优化"列表页，让用户自己找（不触发政策审查） */
fun openBatteryOptimizationList(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { openAppDetail(ctx) }
}
```

> Doze 白名单的真实效果：让你的 `setExactAndAllowWhileIdle` **不受 9 分钟节流**、
> 且 App Standby 不再把你降级成 rare bucket。对"一天几条提醒"的 App，**不开也能用**，
> 开了更稳。别把它说成"必须"，也别在首启就弹。

### 7.2 厂商自启动 / 后台限制（无标准 API，只能 Intent 试探）

各 ROM 的 Activity 名会随版本变，标准做法是**候选矩阵 + try/catch + 兜底到应用详情页**：

```kotlin
private val autoStartCandidates = listOf(
    // 小米 MIUI / HyperOS
    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    // 华为 / 荣耀
    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    // OPPO / realme / OnePlus(ColorOS)
    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
    ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
    // vivo / iQOO
    ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
    // 三星
    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    // 魅族
    ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
)

fun openAutoStartSettings(ctx: Context): Boolean {
    for (c in autoStartCandidates) {
        val intent = Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ctx.packageManager.resolveActivity(intent, 0) == null) continue
        // 有些 ROM 能解析但启动即崩，必须 try/catch
        if (runCatching { ctx.startActivity(intent) }.isSuccess) return true
    }
    openAppDetail(ctx)   // 兜底：设置 → 应用 → 本应用
    return false
}

fun openAppDetail(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
```

**UI 上要给逐条图文/文案指引**（比跳转页更可靠，因为 ROM 会改页面）：

| 厂商 | 要用户开的东西 | 路径 |
|---|---|---|
| 小米 / Redmi（MIUI、HyperOS） | **自启动** + 电池→**无限制** + 最近任务**上锁** | 设置→应用→本应用→自启动；电池→应用智能省电→无限制 |
| 华为 / 荣耀（EMUI、MagicOS） | **应用启动管理** → 手动管理（允许自启动/关联启动/后台活动） | 设置→应用→应用启动管理 |
| OPPO / realme（ColorOS） | **允许自动启动** + 允许后台活动 + 关闭"智能省电" | 设置→电池→应用速冻/耗电保护 |
| vivo / iQOO（OriginOS） | **自启动** + 后台高耗电 | 设置→电池→后台高耗电 |
| 三星（One UI） | 电池→后台使用限制→「永不休眠的应用」加白 | 设置→电池→后台使用限制 |

> 这些页面名会随 ROM 版本变化。**别把入口写死成"教程截图"**，写"设置里搜 XX 关键词"更耐用。

### 7.3 设置页应该长什么样

一节「提醒可靠性」，三个状态行，每行可点：

```
精确闹钟        ✅ 已授予 / ⚠️ 未授予（去设置）
不受电池限制    ✅ 已允许 / ⚠️ 未允许（去设置）
自启动（厂商）  ⚠️ 需手动确认（去设置）   ← 无法检测，只能给入口
```

检测精确闹钟：

```kotlin
fun canExactAlarm(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
```

没有 `USE_EXACT_ALARM` 时，跳 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`（带 package Uri）。
你有 `USE_EXACT_ALARM`，理论上装完即有，但**仍建议显示状态**——因为
应用商店侧载/某些 ROM 会走 `SCHEDULE_EXACT_ALARM` 路径。

---

## 8. 「李跳跳」原理分析，以及你能不能复制

### 8.1 它是什么

李跳跳（`大小姐李跳跳`，2023-08 已宣布无限期停更）是**跳过开屏广告**的工具，做法不是屏蔽广告，
而是**在广告页出现的瞬间模拟手指点"跳过"**。技术底座是 **AccessibilityService（无障碍服务）**：

1. 用户在「设置→无障碍」里手动开启 → 系统 `AccessibilityManagerService` 通过
   `bindService(BIND_AUTO_CREATE)` 把服务拉起来并长期持有；
2. 服务收到 `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED` 事件，
   拿到当前窗口的**控件树**（不是截图）；
3. 用本地规则库（包名 + 控件 id + 文案"跳过/SKIP/关闭/倒计时"）匹配；
4. 命中就 `performAction(ACTION_CLICK)` 或派发手势，点掉它。

它没有网络权限、规则全在本地，所以"不联网、不泄露隐私"是成立的。

### 8.2 它为什么"看起来杀不死、还看不见"

| 现象 | 真实原因 |
|---|---|
| 进程被杀了很快又活 | **系统 bind 的服务**：进程死亡 → system_server 立刻重连（`BIND_AUTO_CREATE`）。这是**系统级重建**，不是 App 自己在守护 |
| 最近任务里看不到 | 它**没有常驻 Activity**，只有 Service。Recents 只列 Task → 天然不出现。不是"隐藏" |
| 平时不占内存感知 | 无障碍服务的进程被系统按普通 cached 进程管理，事件来了才醒 |

**但"杀不死"是错觉**：媒体实测（人民日报 2023 报道）长时间后台运行后照样被系统杀，
李跳跳自己会弹提示让用户去开「自启动 / 一键清理白名单 / 允许后台高耗电 / 关闭省电模式」。
**连它都要靠用户去开厂商白名单**——这恰好说明：没有任何第三方手段能对抗 ROM 的清理。

### 8.3 你能用标准 API 复制吗？→ 不能，也不需要

1. **不能**：无障碍服务的用途被 Google 明确限定为"帮助残障用户"。2023 年起 Play 政策明令
   禁止用 AccessibilityService 做非无障碍目的（自动化点击、广告拦截、自动化测试属违规）。
   拿它当保活手段 = 违规 + 上架被拒 + 用户看到"该应用可读取屏幕全部内容"直接卸载。
2. **不需要**：你的提醒不依赖常驻进程（§1）；你的 App 有必须可见的主界面（§6.3），
   所以"在多任务里隐身"在语义上就是矛盾的。
3. **那些野路子别碰**：1 像素透明 Activity、双进程互拉（`NDK fork`）、
   `JobScheduler` 全家桶互相拉起、监听 `ACTION_SCREEN_ON` 自杀式重启——
   Android 8 之后相继失效，且被 MIUI/EMUI 定向打击，还会被判"恶意行为"下架。

---

## 9. 三档定性：官方推荐 / 只提高概率 / 做不到

### ✅ 官方推荐（该做）
- `AlarmManager` + `BroadcastReceiver` 做提醒（你的主链路，正确）
- `USE_EXACT_ALARM`（提醒类应用合规用法）+ `canScheduleExactAlarms()` 检查与降级
- `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` 重建
- `POST_NOTIFICATIONS` 运行时请求 + 拒绝时降级提示
- 前台服务（`specialUse`）+ 可见通知 + 用户可关
- 引导用户把 App 加入电池优化白名单（先解释、再跳转）
- 设置页展示「提醒可靠性」自检状态

### ⚠️ 只能提高存活率，不能保证
- 前台服务：提升 oom_adj，但 force-stop / ROM 清理照样死
- `START_STICKY`：只对 LMK 有效，对 force-stop 无效
- 电池白名单：减少 Doze 延后，不防清理
- 厂商自启动白名单：让 `BOOT_COMPLETED` 能被投递，但用户可能随时收回
- `setAlarmClock()`：最准（临时白名单 + 无节流），但状态栏会出现闹钟图标，语义像闹钟而不是提醒
- 最近任务上锁（ROM 功能）：用户手动操作，可能被系统更新重置

### ❌ 做不到 / 不要做
- 保证"杀不死"：**不存在**。`force-stop` 是用户与系统的最高权限
- 隐藏最近任务还能提升存活：不成立
- 无障碍服务保活：违规，且并不防杀
- 1px Activity / 双进程 / 监听系统广播自杀重启：已失效 + 违规
- 静默自启前台服务（用户无感知）：Android 12+ 直接抛异常

---

## 10. 合规与用户知情

1. **前台服务必须有可见通知**，不能伪装、不能用 IMPORTANCE_MIN 试图藏起来（Play 会拒）。
2. **不能默认开启常驻**。开关默认关，文案写清"会增加耗电，但提醒更及时"。
3. **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 是受限权限**：Play 政策要求
   (a) 核心功能确实受电池优化影响（提醒类可辩护）；(b) 必须先向用户解释再请求；
   (c) 不能作为使用前提。违规会被下架。稳妥做法：默认只跳
   `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`（列表页），把"直接弹窗"留给用户主动点"一键优化"。
4. **`USE_EXACT_ALARM`**：Play 只允许**闹钟/计时器/日历**类应用声明。你是待办提醒，
   属于可辩护范围，但**不要**用它做别的定时任务（比如每小时拉一次更新）。
5. **无障碍**：禁止用于非无障碍目的（见 §8.3）。
6. **国内商店**：自启动/后台运行属于"敏感权限"，需要隐私政策明示、不得默认开启、
   不得诱导用户关闭系统安全机制。
7. **诚实文案**：设置页别写"永久保活""杀不死"。写"提高提醒准时率"，并给出
   "如果清理了后台，下次打开 App 或重启后会自动恢复"——这才是真实行为。

---

## 11. 针对 Oneline 的推荐落地方案

### 阶段 1（本周，必做，纯增益零代价）

| # | 改动 | 文件 |
|---|---|---|
| 1 | `BootReceiver` 增加 `MY_PACKAGE_REPLACED` + `TIME_SET`，Manifest 补 intent-filter | `reminder/BootReceiver.kt`、`AndroidManifest.xml` |
| 2 | `ReminderScheduler` 增加 `suspend fun scheduleAllActiveBlocking()`，receiver 用 `goAsync()` 包住 | `reminder/ReminderScheduler.kt` |
| 3 | 新增 `keepalive/ReminderHealth.kt`：检测精确闹钟 / 电池白名单 / 通知权限，返回三态 | 新文件 |
| 4 | 设置页加「提醒可靠性」分区：三行状态 + 点击跳转 | `ui/screen/TodoListScreen.kt` |
| 5 | App 冷启动时 `scheduleAllActive()` 一次（reconcile，防 DB 与闹钟漂移） | `OnelineApp.kt` 或 `MainViewModel` init |
| 6 | WorkManager 每 12h 巡检：找出 `dueAtMillis` 已过但 `!archived` 且无通知的待办，补发一条 | 新文件 `reminder/ReminderWatchdog.kt` |

> 阶段 1 不做任何"保活"，但能修掉**升级丢提醒**这个真 bug。ROI 最高。

### 阶段 2（可选，用户可见的开关）

| # | 改动 |
|---|---|
| 7 | 设置→「后台增强」开关（默认关）→ 启动/停止 `KeepAliveService`（specialUse，LOW 通知） |
| 8 | 同一分区放「忽略电池优化」按钮 + 说明文案 + `isIgnoringBatteryOptimizations` 状态 |
| 9 | 「开启自启动」按钮 → 厂商 Intent 矩阵 + 兜底应用详情页 |
| 10 | 各厂商图文指引（可折叠，文案里写"设置里搜索 XX"而不是写死路径） |

### 阶段 3（明确不做，写进 README/关于页）

- 不做 `excludeFromRecents`（用户找不到 App，且无存活收益）
- 不做无障碍、不做隐藏图标、不做 1px Activity
- 不承诺"杀不死"

---

## 12. 测试清单

前置：

```bash
export MSYS_NO_PATHCONV=1
ADB=C:/Users/lirui/AppData/Local/Android/Sdk/platform-tools/adb.exe
PKG=com.lirui.oneline
```

### 12.1 排程正确性

| # | 命令 / 操作 | 期望 |
|---|---|---|
| 1 | `$ADB shell dumpsys alarm \| grep -A2 $PKG` | 每条未完成待办有对应 `RTC_WAKEUP`，时间点与 DB 一致 |
| 2 | `$ADB shell cmd appops get $PKG android:schedule_exact_alarm` | `allow`（否则走了降级路径） |
| 3 | `$ADB shell dumpsys package $PKG \| grep -i exact` | `USE_EXACT_ALARM: granted=true` |

### 12.2 重建（最关键）

| # | 命令 | 期望 |
|---|---|---|
| 4 | `$ADB shell am force-stop $PKG` 后再 `dumpsys alarm \| grep $PKG` | 闹钟**清零**（确认 force-stop 语义）；重新打开 App 后全部重建 |
| 5 | `$ADB shell am broadcast -a android.intent.action.BOOT_COMPLETED -p $PKG` | 闹钟重建（不必真重启） |
| 6 | `$ADB shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -p $PKG` | 闹钟重建 |
| 7 | `$ADB shell am broadcast -a android.intent.action.TIME_SET -p $PKG` | 闹钟重建，且时间点仍是"墙钟那一刻" |
| 8 | `$ADB shell am kill $PKG` | 闹钟**保留**（这是与 force-stop 的关键区别） |

### 12.3 Doze / 省电

| # | 命令 | 期望 |
|---|---|---|
| 9 | `$ADB shell dumpsys deviceidle force-idle` → 等提醒到点 | 通知仍按时到（白名单下更稳） |
| 10 | `$ADB shell dumpsys deviceidle unforce` | 恢复 |
| 11 | 手动开系统「省电模式」后测 | 记录是否延迟（MIUI 会明显延迟非白名单应用） |
| 12 | `$ADB shell dumpsys activity oom \| grep $PKG` | 看 adj：前台服务应在 `fg-service` 档（对照实验用） |

### 12.4 前台服务（阶段 2）

| # | 命令 | 期望 |
|---|---|---|
| 13 | 开关打开 → `dumpsys activity services $PKG` | `isForeground=true`，`foregroundServiceType=specialUse` |
| 14 | `$ADB shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS $PKG` + 广播 BOOT_COMPLETED | 若用 `dataSync` 会崩；`specialUse` 不崩（验证选型正确） |
| 15 | 通知栏划掉常驻通知 | 服务不崩、通知回来（`START_STICKY` + `onStartCommand` 重发） |

### 12.5 最近任务（若做实验）

| # | 操作 | 期望 |
|---|---|---|
| 16 | 开关 `excludeFromRecents` → 按 Home → 开 Recents | 卡片消失；`dumpsys activity recents \| grep $PKG` 无记录 |
| 17 | 卡片消失后从通知进 App | 仍能正常打开（不能出现"打不开"） |

### 12.6 真机（gauguinpro / HyperOS）手工项

- 关机重启 → 不打开 App → 等一条 5 分钟后的提醒是否响（验证 ROM 自启动是否放行）
- 一键清理 → 等提醒（预期：**不响**，这是正常物理规律）→ 打开 App 后是否补排
- 锁屏 + 勿扰 + 省电模式三开 → 通知是否会响（`IMPORTANCE_HIGH` + 渠道未被用户关）

---

## 附录：一句话速查

- 提醒不准 → 查 `USE_EXACT_ALARM`、查 `dumpsys alarm` 有没有条目、查 Doze 白名单
- 提醒全哑 → 大概率 force-stop 或**刚升级过**（等 §3.3 修完就不是了）
- 想"更稳" → 加白名单引导，不是加保活
- 想"隐身" → 只会在用户找不到 App 的路上越走越远

---

## 13. 实现记录（v0.5.0，2026-10-22 落地）

阶段 1 与阶段 2 已全部实现，阶段 3 明确不做。下面是改动清单与验收方式。

### 13.1 改动文件

| 文件 | 改了什么 |
|---|---|
| `reminder/BootReceiver.kt` | 增加 `MY_PACKAGE_REPLACED` / `TIME_SET`；改用 `goAsync()` + `scheduleAllActiveBlocking()`；开机时按用户设置恢复常驻服务 |
| `reminder/ReminderScheduler.kt` | 新增 `suspend scheduleAllActiveBlocking()`，`scheduleAllActive()` 改为它的 fire-and-forget 包装 |
| `reminder/ReminderWatchdog.kt` | **新增**。WorkManager 每 12h 巡检：全量重排 + 补发最近 24h 内错过的提醒（用托盘通知去重） |
| `reminder/NotificationHelper.kt` | 新增 `hasActiveReminder()`：用 `getActiveNotifications()` 判断，而不是自存"已提醒"标记 |
| `keepalive/DeviceCompat.kt` | **新增**。精确闹钟 / 电池白名单 / 通知权限的状态检测；精确闹钟、电池优化、厂商自启动、应用详情的跳转；厂商候选矩阵 + 引导文案 |
| `keepalive/KeepAliveService.kt` | **新增**。`specialUse` 前台服务（LOW 静音常驻通知、`START_STICKY`）+ `KeepAlive` 启停门面 |
| `util/SettingsRepo.kt` | 新增 `keepAliveEnabled` / `setKeepAliveEnabled`（默认关） |
| `OnelineApp.kt` | 启动时 `ReminderWatchdogWorker.ensureScheduled()`（覆盖"开机后从没打开过 App"的区间） |
| `MainActivity.kt` | 冷启动对账 `scheduleAllActive()`；精确闹钟跳转改走 `DeviceCompat` |
| `ui/screen/TodoListScreen.kt` | 设置页「权限」→「提醒可靠性」四条自检行 + 新增「后台常驻」分区 |
| `AndroidManifest.xml` | 三个重建 action；`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`FOREGROUND_SERVICE_SPECIAL_USE`；`KeepAliveService`（含 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`） |
| `app/build.gradle.kts` | versionCode 7 / versionName 0.5.0 |

### 13.2 几个实现上的取舍（不是随手写的）

1. **`KeepAlive.setEnabled` 先启动、成功了才落盘**。
   Android 12+ 从后台启动前台服务会被直接拒绝，如果先把设置写成"开"，
   就会出现"设置里是开的、通知栏没有、服务也没起"的假象。
2. **厂商开关永远显示"需确认"**，不显示红字"未开启"。
   没有任何标准 API 能读到厂商的自启动开关，标红等于编。
3. **常驻服务用独立渠道 `keepalive`（LOW、静音）**，绝不复用 `reminders`（HIGH）——
   否则每次服务重建都会响一声。
4. **巡检补发只补 24 小时内的**，且托盘里已有通知就跳过。
   补一条三天前的过期提醒是骚扰；逾期唠叨链还活着时再补一条是重复。
5. **冷启动对账放 `MainActivity`，巡检注册放 `Application`**：
   前者要"用户真的打开了 App"，后者要覆盖"没打开也算"的区间。

### 13.3 验收清单（在 §12 的基础上新增）

| # | 操作 | 期望 |
|---|---|---|
| 18 | 装 0.5.0 覆盖旧版本（不清数据），看 `dumpsys alarm \| grep oneline` | 闹钟**还在**（`MY_PACKAGE_REPLACED` 生效）；这是 0.4.2 及以前必然丢失的场景 |
| 19 | `am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -p com.lirui.oneline` | 日志有重建、闹钟条目恢复 |
| 20 | 设置页开「后台常驻」 | 通知栏出现「提醒服务运行中」（静音、无声音）；`dumpsys activity services com.lirui.oneline` 显示 `isForeground=true` `specialUse` |
| 21 | 关掉「后台常驻」 | 服务消失、通知消失、设置里开关为关 |
| 22 | 设置页四条状态：手动去系统设置改电池优化后返回 | 状态自动从「去加入」变成「已加入」（ON_RESUME 刷新） |
| 23 | `am force-stop` 后等 12h（或 `adb shell cmd jobscheduler run -f com.lirui.oneline <jobId>` 强制触发 WorkManager） | 巡检把闹钟补回来；24h 内的错过提醒补一条通知 |
| 24 | 进设置页看「自启动 / 后台运行」 | 文案是当前机型对应的引导（小米/华为/OPPO/vivo/三星/魅族），状态显示"需确认"而非红色 |

> `cmd jobscheduler run` 的 jobId 需先 `dumpsys jobscheduler | grep oneline` 找；
> 更省事的做法是把系统时间往后调 12 小时（`TIME_SET` 也会顺带验证重建）。

---

## 14. v0.5.1 修正（2026-10-22）

用户在真机上验证 0.5.0 后报了三处问题，全部是"文案/几何说错了"而不是逻辑错：

| 反馈 | 真实原因 | 处理 |
|---|---|---|
| 点了「去加入」系统对话框、这里显示"已加入"，但去系统设置看「后台高耗电」还是关的 | **两个不同的开关**：`isIgnoringBatteryOptimizations` 读的是 AOSP 的"忽略电池优化"白名单；vivo/OPPO/小米 的「后台高耗电 / 允许后台活动」是厂商私有开关，没有标准 API 可读 | 行标题改为「系统电池优化」，副文案明确写"厂商的『后台高耗电 / 允许后台活动』是另一个开关，见下一条" |
| 自启动明明开了，这里显示"需确认" | 厂商开关**本来就读不到**，所以状态只能是"需确认"（上一版刻意为之） | 增加「我已开启自启动」开关：用户自己确认后状态变「已确认」。这是诚实且可用的折中 —— 记录的是用户的声明，不是伪造的检测结果 |
| 关于页顶栏收不起来；下滑后标题压在状态栏上 | 见 `docs/DEV-NOTES.md`「沉浸式顶栏（v0.5.1 重写）」，三个独立根因 | 顶栏滑满整条 + 状态栏单一口径 + 关于页 listState 不再被遮 |

> 结论一句话：**能检测的（通知 / 精确闹钟 / 电池优化白名单）如实显示；不能检测的（厂商自启动、
> 后台高耗电）交给用户确认，绝不伪造。**

## 15. v0.5.2 修正（2026-10-22）

| 反馈 | 根因 | 处理 |
|---|---|---|
| 顶栏上面变成空白、能看到下面的内容 | 0.5.1 把状态栏那一截交给 `Spacer` 让位却没画底色，TopAppBar 的 surface 只有 64dp 主体；且 alpha 曾加在整条容器上 | 底色移到滑动容器（覆盖 statusBar+64），淡出只作用于标题 |
| 「系统电池优化」点了没反应 | `requestIgnoreBatteryOptimizations` 里 `if (已加白名单) return true` —— 已经加过就什么都不做 | 新入口 `openBatteryOptimizationSettings()`：未加白名单→弹确认框；已加/不能用→打开列表页；再不行→应用详情。**保证每次都做点看得见的事** |
| 不要「我已开启自启动」开关，也不要在行尾显示「已加入 / 已确认」 | — | 删掉该开关与 `autostart_confirmed` 键；`ReliabilityRow.ok` 改为三态 `Boolean?`：`null` = 检测不到 → 只显示「前往」。只有「通知 / 精确闹钟」还保留「已允许 / 去开启」 |

---

## 16. v0.6.2 发布记录（2026-10-22）

| 改动 | 说明 |
|---|---|
| 「测试」下移 | 低频排障项从「通知」与「外观」之间移到设置页**最底部** |
| 「通知」节语义纠正 | zuige 指出「通知模板」只含 标题模板+完成后后缀+预览，与「提醒文案」「常驻通知文案」**并列**；NotificationTitleEditor 改为自折叠，撤掉外层大折叠 |
| chip 选中态 | `FilterChip` 默认 secondaryContainer 在深色配色下与卡片底几乎无色差 → 新增 `SettingsFilterChip`（主色实底/反色字/主色粗边），ChoiceItem 与 BucketChips 共用 |
| 常驻通知文案 | 设置 → 通知 → 常驻通知文案：可编辑后台常驻通知的标题/正文，保存即原地重建通知 |
| 添加弹层预览 | 内边距 16→14/12、行距 10→8；标题与 chip 内文字左对齐（chip 有 12dp 水平内边距） |
| 后台常驻关不掉 | `KeepAlive.setEnabled` 关闭分支误返 true → 返回值语义改为「调用后服务在不在跑」 |
| 归档撤销条被挡 | 移到浮层顶栏下方并跟随其 translationY |
| 关于页沉浸式 | 浮层顶栏在关于页不显示，hero 即页头 |
| 顶栏四轮不动的真因 | `graphicsLayer` 写在 `background` 之后，层只包住内部内容 → 底色永不动；顺序改为 层→底色→裁剪 |

**发布链路**：GitHub Release `v0.6.2`（asset `todo-0.6.2.apk`）+ 博客镜像
`https://blog.zuiges.com/downloads/oneline/todo-0.6.2.apk`。README 徽章与下载入口同步。

## 17. v0.7.0（2026-10-22）

预览卡片可直接选日期/时间。规则（zuige 拍板）：

1. 手动选的**优先于**句子解析值；
2. 没有"清除时间"——时间设了就在（可改不可清）；日期可以改成「无日期」；
3. 多日期待办（"明天，后天 开会"）**每条独立**覆盖；
4. 编辑模式初始显示的就是该待办当前的日期时间（`previewExisting` 反推，本就如此）；
5. 不加额外按钮行，入口就在 chip 上（保持干净）。

实现要点：覆盖表（`index → LocalDate?/LocalTime`）只存在于 AddSheet，
提交前合并成最终 `List<ParsedTodo>` 走 `repo.addFromParsed` 老路 —— 不开新数据通路。
`vm.add(text)/edit(id,text)` 因此删除，改为 `addParsed/editParsed`（校验逻辑原样保留）。
`ParsedTodo.hasExactTime` 是计算属性（`dueTime != null`），copy 时不能传。

## 18. v0.7.x 发布记录（2026-10-22）

| 版本 | 内容 |
|---|---|
| v0.7.0 | 预览卡片可直接选日期/时间（M3 DatePicker/TimePicker）：手动优先句子、多日期各自独立、时间可改不可清、日期可改「无日期」；提交链路改为 UI 侧合并 `List<ParsedTodo>`（`vm.addParsed/editParsed`，删旧 `add/edit`） |
| v0.7.1 | 标题（事件）与详情也可编辑：点预览行弹对话框，同属手动覆盖；标题清空禁用提交 |
| v0.7.2 | 预览 chip 样式统一（删空心描边变体，一律实底）；去掉标题/详情行的编辑铅笔图标 |

发布链路：GitHub Release + 博客镜像（Hexo git 部署，`D:/Workplace/hexo/source/downloads/oneline/`）。
