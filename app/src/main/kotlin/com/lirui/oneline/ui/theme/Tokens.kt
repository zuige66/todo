package com.lirui.oneline.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * 全应用语义色（design tokens）。
 *
 * 存在的理由：之前每个页面各写各的 `surfaceContainerHigh` / `surfaceContainerHighest` /
 * `primaryContainer`，同一个"卡片"在待办行、添加页预览框、设置页小卡片、通知预览里
 * 是四种不同的灰/彩，深色模式尤其明显（纯灰把 11 套配色的努力全抵消了）。
 *
 * 三条规矩，改这里之前先读一遍：
 * 1. **一律由 [ColorScheme] 派生**，不写死 ARGB —— 这样 11 套配色、浅色/深色、暗色彩底
 *    三档（[DarkCardStyle]）都自动跟随，不用改别处。
 * 2. **浅色压 alpha 叠 surface，深色抬 HSV 明度**。深色底上用 alpha 混合会走进"近黑"，
 *    得到发灰的脏色；抬明度只改 V，色相保住。
 * 3. 语义命名，不按"第几层灰"命名。要新颜色先想清楚它是"卡片 / 输入面 / 占位 / 强调"
 *    里的哪一种，别再造一个 `surfaceVariant2`。
 */

/** 判断当前是浅色还是深色方案。`surface` 的亮度是最省事的判据（不依赖 Context）。 */
private fun ColorScheme.isLight(): Boolean = surface.luminance() > 0.5f

/** 只改 HSV 的 V（明度），色相/饱和度原样保留。 */
private fun shiftBrightness(color: Color, delta: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[2] = (hsv[2] + delta).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ── 卡片 ─────────────────────────────────────────────────────────────────────

/**
 * 所有"卡片"的统一底色：待办行、归档行、设置页分组卡、添加页预览框、通知预览框。
 *
 * 直接用 `primaryContainer`：它在 Theme.kt 里已经是"种子色低透明度叠 surface"（浅色）
 * 或"保色相的作品暗色容器"（深色），不需要在调用点再 compositeOver 一次 ——
 * 再混一次就淡到看不见了。
 */
val ColorScheme.cardBackground: Color get() = primaryContainer

/**
 * 拖拽中的卡片。**必须和静止态同色相**，只差一档明度：
 * 浅色压深一点（更"实"，像被拿起来），深色抬亮一点（浮起来）。
 * 早先这里用的是 `surfaceContainerHigh`（中性灰），拖动瞬间卡片"褪色"，非常突兀。
 */
val ColorScheme.cardDragging: Color
    get() = if (isLight()) {
        primary.copy(alpha = 0.10f).compositeOver(primaryContainer)
    } else {
        shiftBrightness(primaryContainer, 0.07f)
    }

/** 占位 / 空位卡片：只给底，不给边框，避免和真实卡片抢注意力。 */
val ColorScheme.cardPlaceholder: Color
    get() = onSurface.copy(alpha = if (isLight()) 0.035f else 0.06f).compositeOver(surface)

/** 卡片描边（需要"虚线框"语义时用，比如拖拽占位的边界）。 */
val ColorScheme.cardBorder: Color
    get() = outlineVariant.copy(alpha = if (isLight()) 0.55f else 0.42f)

// ── 输入面 ───────────────────────────────────────────────────────────────────

/** 输入框/小设置块的填充面。比卡片更"轻"，但仍然带主题色温而不是纯灰。 */
val ColorScheme.fieldSurface: Color
    get() = if (isLight()) {
        onSurface.copy(alpha = 0.03f).compositeOver(surface)
    } else {
        onSurface.copy(alpha = 0.06f).compositeOver(surface)
    }

/** OutlinedTextField 的轮廓色：比默认 outline 浅，降低视觉重量。 */
val ColorScheme.fieldBorder: Color
    get() = outline.copy(alpha = 0.45f)

/** 占位文字（placeholder）。要"浅"但不能浅到看不清 —— 0.55 是实测的下限。 */
val ColorScheme.placeholderText: Color
    get() = onSurfaceVariant.copy(alpha = 0.55f)

// ── 按钮 ─────────────────────────────────────────────────────────────────────

/**
 * 主按钮容器色。
 *
 * 刻意**不用** `primary`（饱和度过高，浅色下像一块色块贴在页面上），
 * 而用 `secondaryContainer`：它本身就是"种子色低透明度叠 surface"的结果，
 * 天然和卡片同族，同时 `onSecondaryContainer` 是它的深色版，对比度足够。
 */
val ColorScheme.actionContainer: Color get() = secondaryContainer
val ColorScheme.actionContent: Color get() = onSecondaryContainer

/** 次级按钮（取消 / 关闭）：无底色，只有文字色。 */
val ColorScheme.secondaryActionContent: Color get() = onSurfaceVariant

/** 禁用态，M3 标准比例。 */
val ColorScheme.actionDisabledContainer: Color get() = onSurface.copy(alpha = 0.08f)
val ColorScheme.actionDisabledContent: Color get() = onSurface.copy(alpha = 0.36f)

/** 保存成功的短暂反馈色。 */
val ColorScheme.feedbackSuccess: Color get() = tertiary
