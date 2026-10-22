package com.lirui.oneline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

/**
 * One selectable colour palette.
 *
 * @param vivid true for the high-saturation group shown separately in settings.
 */
data class Palette(
    val id: Int,
    val name: String,
    val vivid: Boolean,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

object Palettes {

    val all: List<Palette> = listOf(
        // ---- Material 3 Expressive (balanced, default) ----
        Palette(0, "雅紫", false, Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
        Palette(1, "青碧", false, Color(0xFF00696D), Color(0xFF4A6363), Color(0xFF3F6374)),
        Palette(2, "海蓝", false, Color(0xFF2B5F8E), Color(0xFF52606D), Color(0xFF6B5778)),
        Palette(3, "暖橙", false, Color(0xFF8B5000), Color(0xFF745943), Color(0xFF5D5F7E)),
        Palette(4, "玫瑰", false, Color(0xFF8E4585), Color(0xFF6E5868), Color(0xFF7D5260)),
        Palette(5, "森林", false, Color(0xFF2E6B3E), Color(0xFF506350), Color(0xFF3B6470)),
        // ---- Vivid ----
        Palette(100, "电光紫", true, Color(0xFF7C4DFF), Color(0xFFB388FF), Color(0xFFEA80FC)),
        Palette(101, "荧光青", true, Color(0xFF00BFA5), Color(0xFF18FFFF), Color(0xFF69F0AE)),
        Palette(102, "亮橙红", true, Color(0xFFFF5722), Color(0xFFFF9100), Color(0xFFFFD600)),
        Palette(103, "电光蓝", true, Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF18FFFF)),
        Palette(104, "玫红", true, Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFFFAB91))
    )

    fun byId(id: Int): Palette = all.firstOrNull { it.id == id } ?: all.first()

    fun balanced(): List<Palette> = all.filter { !it.vivid }
    fun vivid(): List<Palette> = all.filter { it.vivid }
}

/**
 * How the "card" tint is derived **in dark mode** — user-selectable in 设置 → 外观 → 深色卡片.
 *
 * Every entry keeps the seed colour's *hue* and only moves its brightness / saturation.
 * That is the whole point: the old dark container was
 * `seed.copy(alpha = 0.30f) over #141218`, i.e. the seed blended into near-black,
 * which turned 青碧 (#00696D) into a muddy #0E2C31 — the colour read as "darkened and
 * dirty" rather than "the palette you picked".
 *
 * @param brightness target HSV value (0..1) of the container — higher = more colour presence
 * @param chroma     saturation multiplier applied to the seed (0 = fully neutral grey)
 *
 * v2.1: every tier was pulled down one step (RICH 0.62→0.50, STANDARD 0.50→0.42,
 * SOFT 0.30→0.26, NEUTRAL 0.21→0.19) and RICH's chroma 1.30→1.15. 深彩 at 0.62 read as
 * "glowing" rather than "dark" on OLED — the card was brighter than the text deserved.
 */
enum class DarkCardStyle(
    val id: Int,
    val label: String,
    val sub: String,
    val brightness: Float,
    val chroma: Float
) {
    /** No tint at all: neutral grey; cards are told apart by their rounded corners only. */
    NEUTRAL(2, "不染色", "纯灰底，只用圆角区分卡片", 0.19f, 0f),

    /** Dimmer wash: sits close to the background, keeps just a hint of colour. */
    SOFT(1, "淡彩", "淡淡的彩底，接近背景", 0.26f, 1.0f),

    /** Clear dark container in the seed hue — the standard M3 "dark container" feel. */
    STANDARD(0, "标准暗色", "能看出配色，M3 暗色容器观感", 0.42f, 1.05f),

    /** The strongest tint: a rich, unmistakably coloured card. */
    RICH(3, "深彩", "浓郁彩底，存在感最强", 0.50f, 1.15f);

    companion object {
        /** Display order, from no colour to the strongest colour. */
        val all: List<DarkCardStyle> = listOf(NEUTRAL, SOFT, STANDARD, RICH)

        /** ids are persisted; they MUST stay stable across releases. */
        fun byId(id: Int): DarkCardStyle = values().firstOrNull { it.id == id } ?: STANDARD
    }
}

/** Re-seat [seed] at a given HSV brightness / saturation while keeping its hue. */
private fun tone(seed: Color, brightness: Float, chroma: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    hsv[1] = (hsv[1] * chroma).coerceIn(0f, 1f)
    hsv[2] = brightness.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Build a full light scheme from three seed colours (containers derived from the seed). */
private fun lightScheme(p: Palette): ColorScheme {
    val surface = Color(0xFFFFFBFE)
    val containerAlpha = if (p.vivid) 0.22f else 0.16f
    return lightColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = p.primary.copy(alpha = containerAlpha).compositeOver(surface),
        onPrimaryContainer = p.primary,
        secondary = p.secondary,
        onSecondary = Color.White,
        secondaryContainer = p.secondary.copy(alpha = containerAlpha + 0.04f).compositeOver(surface),
        onSecondaryContainer = p.secondary,
        tertiary = p.tertiary,
        onTertiary = Color.White
    )
}

/**
 * Build a full dark scheme; balanced seeds get lifted so they stay readable on near-black.
 *
 * Containers go through [tone] instead of alpha-blending the seed into the near-black
 * surface, so they stay the *same hue* as the palette instead of darkening into grey mud.
 */
private fun darkScheme(p: Palette, cardStyle: DarkCardStyle): ColorScheme {
    fun adapt(c: Color) = if (p.vivid) c else c.copy(alpha = 0.72f).compositeOver(Color.White)
    return darkColorScheme(
        primary = adapt(p.primary),
        onPrimary = Color(0xFF171717),
        primaryContainer = tone(p.primary, cardStyle.brightness, cardStyle.chroma),
        onPrimaryContainer = Color.White,
        secondary = adapt(p.secondary),
        onSecondary = Color(0xFF171717),
        secondaryContainer = tone(p.secondary, cardStyle.brightness, cardStyle.chroma),
        onSecondaryContainer = Color.White,
        tertiary = adapt(p.tertiary),
        onTertiary = Color(0xFF171717)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnelineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteId: Int = 0,
    darkCardStyle: DarkCardStyle = DarkCardStyle.STANDARD,
    content: @Composable () -> Unit
) {
    val palette = Palettes.byId(paletteId)
    val colorScheme = when {
        // Keep the stock expressive light scheme for the default palette.
        !darkTheme && palette.id == 0 -> expressiveLightColorScheme()
        darkTheme -> darkScheme(palette, darkCardStyle)
        else -> lightScheme(palette)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive()
    ) {
        content()
    }
}
