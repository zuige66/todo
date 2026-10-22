package com.lirui.oneline.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import com.lirui.oneline.R

/**
 * 一键把小组件钉到桌面。
 *
 * ★ 为什么做这个入口：MIUI / HyperOS 的组件列表经常不显示刚装好的 App 提供的组件
 *   （Launcher 侧缓存，要等很久甚至要重启 Launcher）。`requestPinAppWidget` 走的是
 *   系统标准流程，不依赖 Launcher 的列表刷新 —— 点一下，系统弹确认，用户点"添加"就上屏。
 *   zuige 在澎湃 OS 3 上实测搜不到组件（v0.8.0 反馈），这个入口就是给这种情况兜底的。
 *
 * @param onUnsupported 系统不支持 pin（API < 26，或个别 ROM 阉割了）时的降级提示。
 */
object WidgetPinHelper {

    fun requestPin(context: Context, onUnsupported: () -> Unit) {
        val manager = AppWidgetManager.getInstance(context) ?: return onUnsupported()
        if (!manager.isRequestPinAppWidgetSupported) {
            onUnsupported()
            return
        }
        val provider = ComponentName(context.applicationContext, TodoWidgetProvider::class.java)
        // extras 传 null：不需要指定尺寸，按 targetCellWidth/Height 的默认 2×2 上屏。
        // 回调也传 null：添加成功后不需要额外动作（onUpdate 会自然走到，刷新列表）。
        val ok = manager.requestPinAppWidget(provider, null, null)
        if (!ok) onUnsupported()
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
