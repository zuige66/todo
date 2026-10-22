package com.lirui.oneline.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R
import com.lirui.oneline.util.SettingsRepo

/**
 * 「后台增强」的可选常驻服务。
 *
 * ★ 定位必须说清楚，否则这段代码很容易被误解成"保活"：
 *   本应用的提醒由 AlarmManager 驱动，**进程死不死都会响**（PendingIntent 由 system_server 持有，
 *   到点由系统 fork 出进程再投递）。这个服务唯一的收益是：
 *     - 进程常驻 → 通知栏「完成/撤回」点下去时省掉一次冷启动，手感更快；
 *     - oom_adj 抬到 foreground-service 档 → LMK 更晚回收、系统更少冻结。
 *   它**挡不住** force-stop、挡不住 MIUI/HyperOS 的一键清理，也挡不住用户在设置里强行停止。
 *
 * 也因此：
 *   1. 默认关闭，必须在设置页由用户主动打开；
 *   2. 必须挂一条用户看得见的常驻通知（系统强制，且这是"诚实"的一部分）；
 *   3. 通知是 LOW 重要级、静音、不可响铃 —— 常驻通知不该打扰人。
 *
 * fgsType 用 `specialUse` 而不是 `dataSync` 的原因（Android 15 / target 35+）：
 *   `BOOT_COMPLETED` 接收器**不允许**启动 dataSync / camera / mediaPlayback / phoneCall /
 *   mediaProjection / microphone 六种类型的前台服务，否则抛 ForegroundServiceStartNotAllowedException；
 *   而且 dataSync 在 Android 15 上还有 6 小时/24h 的运行时长上限。`specialUse` 两者都不受限。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        KeepAlive.isRunning = true
        ensureChannel()
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被系统重建时（intent == null）也要重新进前台：START_STICKY 只负责重建服务，
        // 不会自动把服务放回前台状态。
        goForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        KeepAlive.isRunning = false
        super.onDestroy()
    }

    /**
     * ★ 不要在 onTaskRemoved 里 startForegroundService 复活自己。
     *   Android 12+ 后台启动前台服务需要豁免，多数情况下直接抛异常；即便成功，
     *   也属于"对抗用户操作"，厂商风控与商店审核都会盯这一点。
     */

    private fun goForeground() {
        // 通知权限被拒也不能跳过 startForeground：服务仍需在 5 秒内进前台，
        // 否则 ANR（Windows 上表现为 "Context.startForegroundService() did not then call
        // Service.startForeground()"）。通知本身由系统决定是否展示。
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                fgsType()
            )
        }
    }

    private fun fgsType(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // ★ 绝不能复用 "reminders" 渠道（IMPORTANCE_HIGH）：那样每次服务重建都会响一声。
        val channel = NotificationChannel(
            CHANNEL_KEEPALIVE,
            "后台常驻",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持提醒即时响应，可在设置里关闭"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // 文案可编辑（设置 → 通知 → 常驻通知文案），默认值见 SettingsRepo。
        // 空标题用应用名兜底：系统会把空标题折叠成包名，反而难看。
        val style = SettingsRepo.keepAliveNotifStyle(this)
        val appName = runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault("提醒服务")
        return NotificationCompat.Builder(this, CHANNEL_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(style.title.ifBlank { appName })
            .setContentText(style.body)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL_KEEPALIVE = "keepalive"
        const val NOTIFICATION_ID = 9001
    }
}

/**
 * 启动 / 停止 [KeepAliveService] 的唯一入口。
 *
 * 之所以要一个 object 包一层：
 *  - `startForegroundService` 在 Android 12+ 从后台调用会抛
 *    `ForegroundServiceStartNotAllowedException`，必须 try/catch（调用点不能崩）；
 *  - 停止时要同时清掉通知，否则会留下一条"僵尸"常驻通知。
 */
object KeepAlive {

    /** 服务是否正在本进程运行（service 与 App 同进程，所以进程内的标志就是真相）。 */
    @Volatile
    var isRunning: Boolean = false
        internal set

    fun isEnabled(context: Context): Boolean = SettingsRepo.keepAliveEnabled(context)

    /**
     * 用户拨动开关。
     *
     * ★ 返回值的语义是「**调用之后服务最终在不在跑**」，不是「操作成功没成功」。
     *   v0.6.0 这里在关闭分支也 return true，调用方拿它更新开关状态，
     *   结果点了"关闭"→ 开关被写回 true → 看着永远关不掉。
     *
     * ★ 顺序是「先启动、成功了才落盘」：开关的状态必须反映**服务真的在跑**，
     *   否则会出现"设置里是开的、通知栏没有、服务也没起"的假象（Android 12+ 后台
     *   启动前台服务会被直接拒绝，这个分支是真实存在的）。
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!enabled) {
            SettingsRepo.setKeepAliveEnabled(context, false)
            stop(context)
            return false
        }
        val ok = start(context)
        SettingsRepo.setKeepAliveEnabled(context, ok)
        return ok
    }

    /** @return 是否成功进入前台；false 表示被系统拒绝（后台启动限制），UI 需如实提示。 */
    fun start(context: Context): Boolean {
        val intent = Intent(context, KeepAliveService::class.java)
        return runCatching {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }.isSuccess
    }

    /** 开机 / 升级后按用户上次的选择恢复。失败就算了，不能因此崩掉广播接收器。 */
    fun startIfEnabled(context: Context) {
        if (isEnabled(context) && !isRunning) start(context)
    }

    /**
     * 服务正在跑时，用**新文案重建**那条常驻通知（同一个 id，原地更新）。
     *
     * 用户在设置里改完常驻通知文案时调用 —— 否则要等服务被杀后重启才生效，
     * 看着就像"改了没用"。不 stop 再 start：那会让通知先消失再出现，闪一下。
     */
    fun refresh(context: Context) {
        if (!isRunning) return
        val intent = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
        else context.startService(intent)
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(KeepAliveService.NOTIFICATION_ID)
        }
        isRunning = false
    }
}
