package com.lirui.oneline.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import com.lirui.oneline.R

/**
 * 一键把小组件钉到桌面。
 *
 * ★ 为什么做这个入口：MIUI / HyperOS / vivo 的组件列表经常不显示刚装好的 App 组件
 *   （Launcher 侧缓存或裁剪）。`requestPinAppWidget` 是系统标准流程，不依赖列表刷新。
 *
 * ★ vivo 实测 pin 不可用（isRequestPinAppWidgetSupported=false）→ 降级提示。
 *   降级前先查 [AppWidgetManager.getInstalledProvidersForProfile]：如果系统层
 *   **已经注册**了组件，说明只是 Launcher 的列表 UI 没刷出来 —— 让用户在列表里
 *   搜「待办」或「Todo」；如果**没注册**，那就是系统 service 层没认，必须重启手机
 *   让 AppWidgetService 重扫（装完 App 没重启的机器常见）。
 */
object WidgetPinHelper {

    fun requestPin(context: Context, onUnsupported: () -> Unit) {
        val manager = AppWidgetManager.getInstance(context) ?: return onUnsupported()
        val provider = ComponentName(context.applicationContext, TodoWidgetProvider::class.java)

        // 诊断：系统 service 层认不认这个 provider（与 Launcher 的列表 UI 无关）。
        // 用 getInstalledProviders()（API 16+ 无参）：getInstalledProvidersForProfile()
        // 的无参重载在这个 compileSdk 上解析不到，会报 "No value passed for parameter p0"。
        // 把总数一起显示：总数为 0 说明这台机器的查询被 ROM 裁剪（MIUI 系有先例），
        // 那个"未注册"就不是我们的问题。
        val all = manager.getInstalledProviders()
        val registered = all.any { it.provider == provider }
        if (!registered) {
            Toast.makeText(
                context,
                "诊断：系统已注册 ${all.size} 个组件，本应用不在其中。" +
                    "请重启手机后重试；仍提示则长按桌面搜「Todo」",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!manager.isRequestPinAppWidgetSupported) {
            // 这台 ROM 不支持"一键钉桌面"（vivo 实测）。组件其实已经在系统列表里了，
            // 引导用户去 Launcher 的组件列表搜。
            Toast.makeText(
                context,
                "该系统不支持一键添加；请在桌面组件列表里搜「待办」或「Todo」",
                Toast.LENGTH_LONG
            ).show()
            onUnsupported()
            return
        }

        // extras 传 null：不需要指定尺寸，按 targetCellWidth/Height 的默认 2×2 上屏。
        // 回调也传 null：添加成功后不需要额外动作（onUpdate 会自然走到，刷新列表）。
        val ok = manager.requestPinAppWidget(provider, null, null)
        if (!ok) {
            Toast.makeText(
                context,
                "系统拒绝了添加请求；请在桌面组件列表里搜「待办」或「Todo」",
                Toast.LENGTH_LONG
            ).show()
            onUnsupported()
        }
    }

    /** 系统不支持时的默认提示：教用户走传统路径。 */
    fun toastManualAdd(context: Context) {
        Toast.makeText(
            context,
            "请长按桌面空白处 → 添加小组件 → 找到「Todo」",
            Toast.LENGTH_LONG
        ).show()
    }
}
