@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lirui.oneline.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirui.oneline.R
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 「关于」页。
 *
 * 版式抄自 `D:/Workplace/Hyper Music Scape Lock` 的 `LockAboutPage`：
 * 整屏一个滚动列表，hero（图标 + 应用名 + 版本号）**叠在列表之上**，
 * 上滑时 hero 淡出并轻微缩小、动画渐变背景同时淡掉，列表内容看上去是「从下面盖上来」的。
 *
 * 两处刻意的改造（不是照搬）：
 * 1. **配色跟着主题走**。原页硬编码了粉/蓝三套调色板；这里是待办 App，有 11 套配色可选，
 *    所以三套渐变直接用当前 `colorScheme` 的 container 轮换生成，换配色不用改一行代码。
 * 2. **没有「检查更新」**。本应用不申请 INTERNET 权限、完全离线，做不了 GitHub API 检查，
 *    改成「更新与下载」直接打开 Releases 页面。
 */

private const val REPO_URL = "https://github.com/zuige66/todo"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val DEVELOPER_NAME = "zuige"
private const val DEVELOPER_HANDLE = "zuige66"
private const val DEVELOPER_GITHUB_URL = "https://github.com/zuige66"
private const val DEVELOPER_BLOG_URL = "https://blog.zuiges.com"

/** hero 占屏高的比例。原页是 0.60（锁屏整屏），这里是普通页面，留矮一点让列表能露头。 */
private const val HERO_HEIGHT_FRACTION = 0.46f
private const val BACKGROUND_SPEED = 0.12f
private const val COLOR_INTERPOLATION_SECONDS = 12f

/**
 * 图标底色。必须与 `values/colors.xml` 里的 `ic_launcher_background` 完全一致 ——
 * `ic_about_logo.xml` 里那道竖条就是拿这个色当"镂空"用的，差一点就会露出色块。
 */
private val LogoBackground = Color(0xFF00696A)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeight * HERO_HEIGHT_FRACTION
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val backgroundFadeDistance = with(density) { 360.dp.toPx() }
    val logoFadeStart = heroHeightPx * 0.25f
    val logoFadeDistance = heroHeightPx * 0.35f

    // 只读 firstVisibleItem* 会漏掉「hero 还在屏幕上、列表已开始滑」的那一段，
    // 所以第 0 项时要取 scrollOffset，之后直接当作已经滚过整个 hero。
    //
    // 刻意不用 `by`：下面所有读取都发生在 graphicsLayer 的 draw 阶段，传 State 进去
    // 才走「延迟读取」——每滚一个像素只重画、不重组。一旦在组合阶段解包，
    // LazyColumn 和整页都会跟着每一像素重组一次。
    val scrollOffset = remember(listState, heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) heroHeightPx
            else listState.firstVisibleItemScrollOffset.toFloat()
        }
    }

    // 同理：动画时间也只在 draw 阶段读（Canvas 里），否则每帧整页重组。
    val animationTime = rememberAboutAnimationTime()

    // 页面推入：淡入 + 从下往上 28dp。不用整屏缩放 —— 缩放会把根节点的 surface 边缘
    // 露出来，看起来像一张缩小的卡片而不是"翻到下一页"。
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 520f))
    }

    val pkgInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: "-"
    val versionCode = when {
        pkgInfo == null -> 0L
        Build.VERSION.SDK_INT >= 28 -> pkgInfo.longVersionCode
        else -> @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 28.dp.toPx()
            }
            // 不透明底色 + 消费点击：关于页是叠在 Scaffold 之上的覆盖层，
            // 不吞掉触摸的话，hero 那块空白区域的点击会穿透到下面的 FAB。
            .background(cs.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        AnimatedAboutBackground(
            animationTime = animationTime,
            colorScheme = cs,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight + 180.dp)
                .graphicsLayer {
                    // 背景先淡掉（跟着滚动距离线性走），hero 稍后才开始消失，
                    // 两者错开才有「从下面盖上来」的层次。
                    alpha = (1f - scrollOffset.value / backgroundFadeDistance).coerceIn(0f, 1f)
                    // 必须开离屏合成：背景内部用 BlendMode.DstIn 做竖直淡出蒙版，
                    // 没有独立图层时它会拿整块 surface 当目标去混合，底边会留硬边。
                    compositingStrategy = CompositingStrategy.Offscreen
                    translationY = -listState.firstVisibleItemScrollOffset * 0.12f
                }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 0.dp,
                end = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                // hero 是叠在上层的（不是列表项），这里留出等高空位让它可见。
                Spacer(Modifier.height(heroHeight))
            }
            item { AboutSectionTitle("关于应用") }
            item {
                AboutCard {
                    InfoRow("版本", "$versionName ($versionCode)")
                    AboutDivider()
                    InfoRow("包名", context.packageName)
                }
            }
            item { AboutSectionTitle("开发者") }
            item { DeveloperCard() }
            item { AboutSectionTitle("关于项目") }
            item {
                AboutCard {
                    LinkRow("GitHub 仓库", "zuige66/todo") { openUrl(context, REPO_URL) }
                    AboutDivider()
                    LinkRow("更新与下载", "GitHub Releases") { openUrl(context, RELEASES_URL) }
                }
            }
            item {
                Text(
                    "完全离线运行，不申请网络权限；所有待办只保存在本机。\n"
                        + "语义解析为本地规则实现，不会上传任何输入内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 20.dp)
                )
            }
        }

        // hero 画在列表之后（更上层），淡出过程中列表内容是「从下面盖上来」的观感。
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .height(heroHeight)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.graphicsLayer {
                    // 这段读的是 State，属延迟读取：滚动只在 draw 阶段失效图层，不重组。
                    val progress =
                        ((scrollOffset.value - logoFadeStart) / logoFadeDistance).coerceIn(0f, 1f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.1f
                    scaleY = 1f - progress * 0.1f
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = LogoBackground,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(88.dp)
                ) {
                    // 只画矢量「标记」，圆角底色由上面的 Surface 提供。
                    // 千万别改回 R.mipmap.ic_launcher：那是 <adaptive-icon>，
                    // painterResource 只认 <vector>，会在组合这一帧抛
                    // IllegalArgumentException 把关于页打崩（DESIGN §31）。
                    Image(
                        painter = painterResource(R.drawable.ic_about_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    )
                }
                Text(
                    "一句话待办",
                    modifier = Modifier.padding(top = 18.dp),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Text(
                    "v$versionName ($versionCode)",
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 15.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }

        // 返回按钮浮在左上角（关于页没有 TopAppBar，靠它和系统返回键退出）
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = cs.onSurface
            )
        }
    }
}

// ── 内容块 ───────────────────────────────────────────────────────────────────

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 6.dp)
    )
}

/** 与设置页、待办行同一套卡片底色（`primaryContainer`），整个 App 读起来是一个卡片系统。 */
@Composable
private fun AboutCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun AboutDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun LinkRow(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(summary, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // openUrl 内部已经处理失败（Toast），这里不再包一层，避免重复提示
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * 开发者卡片：头像块 + 名称 + GitHub 号 + 箭头，整头可点、就地弹性展开/收起
 * （`animateContentSize` + 箭头旋转 90°），展开区是 GitHub / Blog 两项外链。
 *
 * 没有头像图片，用主题色渐变圆 + 首字母代替，换配色时也跟着变。
 */
@Composable
private fun DeveloperCard() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "developerArrow"
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "z",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimary
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    DEVELOPER_NAME,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    "@$DEVELOPER_HANDLE",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = cs.onSurfaceVariant
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                LinkRow("GitHub", DEVELOPER_HANDLE) { openUrl(context, DEVELOPER_GITHUB_URL) }
                LinkRow("博客", "blog.zuiges.com") { openUrl(context, DEVELOPER_BLOG_URL) }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}

// ── hero 背景动效 ────────────────────────────────────────────────────────────

@Composable
private fun AnimatedAboutBackground(
    animationTime: State<Float>,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // 颜色插值放在 draw 阶段算：它每帧都变，放进组合阶段就等于每帧重组一次整页。
        // animatedGradientColors 是纯函数，draw scope 里调用没有任何副作用。
        val colors = animatedGradientColors(animationTime.value, colorScheme)
        // 底层斜向线性渐变
        drawRect(
            brush = Brush.linearGradient(
                colors = colors.map { it.copy(alpha = 0.58f) },
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        // 四个缓慢游走的径向光斑，叠出「mesh gradient」的层次
        val radius = size.maxDimension * 0.62f
        val t = animationTime.value * BACKGROUND_SPEED
        val centers = listOf(
            Offset(size.width * (0.18f + 0.10f * sin(t)), size.height * (0.20f + 0.08f * cos(t * 0.8f))),
            Offset(size.width * (0.82f + 0.10f * cos(t * 0.9f)), size.height * (0.78f + 0.10f * sin(t * 0.7f))),
            Offset(size.width * (0.22f + 0.12f * cos(t * 0.65f)), size.height * (0.80f + 0.08f * sin(t * 0.85f))),
            Offset(size.width * (0.80f + 0.12f * sin(t * 0.72f)), size.height * (0.20f + 0.08f * cos(t * 0.62f)))
        )
        centers.forEachIndexed { index, center ->
            val color = colors[index]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.84f), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )
        }
        // 竖直淡出：让背景底边平滑融进 surface，而不是一条硬边
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.White,
                    0.68f to Color.White,
                    1f to Color.Transparent
                )
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

/**
 * 帧驱动的累计时间；只有关于页在前台时才会跑。
 * 返回 `State` 而不是 `Float`：调用方只能在 draw 阶段读，避免每帧重组整页。
 */
@Composable
private fun rememberAboutAnimationTime(): State<Float> {
    val animationTime = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { frameTime ->
                if (previousFrame != 0L) {
                    animationTime.floatValue += (frameTime - previousFrame) / 1_000_000_000f
                }
                previousFrame = frameTime
            }
        }
    }
    return animationTime
}

/**
 * 三套渐变调色板全部由当前 `colorScheme` 轮换生成 —— 用户换配色，关于页跟着换。
 * 段与段之间用 smoothstep 做缓动，避免色彩切换时出现「顿一下」的线性折点。
 */
private fun animatedGradientColors(animationTime: Float, cs: ColorScheme): List<Color> {
    val p0 = listOf(cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer, cs.primary)
    val p1 = listOf(cs.secondaryContainer, cs.tertiaryContainer, cs.primaryContainer, cs.secondary)
    val p2 = listOf(cs.tertiaryContainer, cs.primaryContainer, cs.secondaryContainer, cs.tertiary)
    val palettes = listOf(p0, p1, p2)

    val segmentValue = animationTime / COLOR_INTERPOLATION_SECONDS
    val segment = floor(segmentValue).toInt()
    val rawProgress = segmentValue - floor(segmentValue)
    val progress = rawProgress * rawProgress * (3f - 2f * rawProgress)
    val start = palettes[segment % 3]
    val end = palettes[(segment + 1) % 3]
    return start.indices.map { index -> lerp(start[index], end[index], progress) }
}
