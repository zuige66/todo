package com.lirui.oneline.keepalive

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 与「系统允许我们活多久」有关的一切检测与跳转。
 *
 * 这个文件只有两个职责：
 *  1. **状态检测** —— 告诉设置页每一条到底是"已放行"还是"需要用户去点一下"；
 *  2. **跳转** —— 把用户送到正确的系统页面。
 *
 * ★ 红线：**没有标准 API 能检测「自启动 / 后台高耗电」是否已放行**。
 *   那是厂商在系统设置里加的非 AOSP 开关，第三方应用只能给入口 + 引导文案，
 *   不能假装知道答案。所以 [autoStartState] 只有"未知"这一种可能，UI 上必须
 *   写成"需确认"而不是"未开启"。
 */
object DeviceCompat {

    // ---------- 状态检测 ----------

    /** Android 12+ 精确闹钟是否被允许（API < 31 恒为 true）。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** 是否已在「电池优化」白名单（即"不受限制"）。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Android 13+ 的通知权限（低版本恒为 true）。 */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------- 跳转 ----------

    /**
     * 精确闹钟授权页。
     *
     * 带 `package:` Uri 才能直达本应用；某些 ROM 不认这个 action，退回应用详情页。
     * 如果 Manifest 里声明了 `USE_EXACT_ALARM`（本应用就是），系统一般已直接授予，
     * 这条路径只作兜底。
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (ok) return
        }
        openAppDetail(context)
    }

    /**
     * 打开「电池优化」相关设置。**永远做点看得见的事**。
     *
     * ★ v0.5.1 的 bug：这里原来写成
     * ```
     * if (isIgnoringBatteryOptimizations(context)) return true   // ← 已经加过白名单就直接返回
     * ```
     * 于是"点了一下什么都没发生"。对用户来说这就是"不能正常跳转"。
     * 现在的顺序：
     *   1. 还没进白名单 → 直接弹系统确认框（一步到位，这是用户最想要的那条路）；
     *   2. 已经在白名单 / 确认框不可用（部分 ROM 删了这个 Activity）→ 打开列表页，
     *      让用户能看见"自己在哪里"；
     *   3. 都不行 → 应用详情页。
     *
     * @return true = 至少打开了一个系统页面
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        if (!isIgnoringBatteryOptimizations(context)) {
            val opened = runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (opened) return true
        }
        val listed = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (listed) return true
        openAppDetail(context)
        return false
    }

    /**
     * 厂商「自启动 / 后台运行」页。
     *
     * 这些 Activity 名是各 ROM 的私有实现，**随版本变化**，所以：
     *  - 先 `resolveActivity` 判断能否解析（避免 ActivityNotFound 崩溃）；
     *  - 再 `try/catch` 包住 `startActivity`（有些 ROM 能解析但启动即抛 SecurityException）；
     *  - 全部失败则退到应用详情页，并返回 false 让 UI 提示"自己找一下"。
     *
     * @return true = 至少跳到了某个候选页
     */
    fun openAutoStartSettings(context: Context): Boolean {
        for (component in autoStartCandidates()) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) == null) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        openAppDetail(context)
        return false
    }

    /** 设置 → 应用 → 本应用（所有 ROM 都有，永远可用）。 */
    fun openAppDetail(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ---------- 厂商识别 ----------

    /** 归一化后的厂商标识，用于选引导文案。 */
    fun vendor(): Vendor {
        val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> Vendor.XIAOMI
            brand.contains("huawei") || brand.contains("honor") -> Vendor.HUAWEI
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> Vendor.OPPO
            brand.contains("vivo") || brand.contains("iqoo") -> Vendor.VIVO
            brand.contains("samsung") -> Vendor.SAMSUNG
            brand.contains("meizu") -> Vendor.MEIZU
            else -> Vendor.OTHER
        }
    }

    enum class Vendor { XIAOMI, HUAWEI, OPPO, VIVO, SAMSUNG, MEIZU, OTHER }

    /**
     * 厂商专属引导文案。**一行放下**（v0.7.3：设置页文案精简，zuige 要求"尽量一行"）。
     *
     * 写法约束：**只写"设置里搜什么"，不写死"第几项第几屏"** —— 这些页面的层级
     * 每个 ROM 版本都在改，写死了下个版本就是错的，比不写更糟。
     */
    fun vendorHint(): String = vendorHint(vendor())

    fun vendorHint(vendor: Vendor): String = when (vendor) {
        Vendor.XIAOMI -> "小米：设置搜「自启动」允许，省电策略改「无限制」"
        Vendor.HUAWEI -> "华为：应用启动管理改为「手动管理」"
        Vendor.OPPO -> "OPPO：搜「自启动」允许，电池里关「智能省电」"
        Vendor.VIVO -> "vivo：电池里打开「后台高耗电」并允许自启动"
        Vendor.SAMSUNG -> "三星：电池 → 后台使用限制 → 永不休眠"
        Vendor.MEIZU -> "魅族：权限管理里允许「后台运行」与「自启动」"
        Vendor.OTHER -> "在系统设置里允许本应用「自启动 / 后台运行」"
    }

    /** 候选矩阵：顺序即优先级。ROM 版本变了就往下掉到下一个候选。 */
    private fun autoStartCandidates(): List<ComponentName> = listOf(
        // 小米 MIUI / HyperOS
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        // 华为 / 荣耀
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        // OPPO / realme / 一加（ColorOS）
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.startupapp.StartupAppListActivity"
        ),
        ComponentName(
            "com.oplus.safecenter",
            "com.oplus.safecenter.startupapp.StartupAppListActivity"
        ),
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        // vivo / iQOO
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        // 三星
        ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        ),
        // 魅族
        ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
    )
}
