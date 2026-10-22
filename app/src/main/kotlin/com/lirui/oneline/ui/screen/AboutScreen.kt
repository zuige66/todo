@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lirui.oneline.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirui.oneline.R
import com.lirui.oneline.update.ApkInstaller
import com.lirui.oneline.update.AppUpdate
import com.lirui.oneline.update.UpdateCheck
import com.lirui.oneline.update.UpdateDownloadService
import com.lirui.oneline.update.UpdateService
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 「关于」页（v0.4 起是第四个导航项，不再是覆盖层）。
 *
 * 版式仍沿用 `D:/Workplace/Hyper Music Scape Lock` 的 `LockAboutPage`：
 * 整屏一个滚动列表，hero（图标 + 应用名 + 版本号）**叠在列表之上**，
 * 上滑时 hero 淡出并轻微缩小、动画渐变背景同时淡掉，列表内容看上去是「从下面盖上来」的。
 *
 * 与它刻意不同的三处：
 * 1. **配色跟着主题走**：那边硬编码了粉/蓝三套调色板，这里三套渐变直接用当前
 *    `colorScheme` 的 container 轮换生成，换配色不用改一行代码。
 * 2. **没有返回按钮**：作为 tab 页，返回键不该被它拦下来（旧版是覆盖层，才需要 BackHandler）。
 * 3. 检查更新的实现见 [UpdateService]；下载走 [ApkInstaller] + 前台服务。
 */

/** 检查更新在这一页的可见状态。 */
private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    /** 仓库还没有发布过任何版本。 */
    data object NoRelease : UpdateUi
}

private const val REPO_URL = "https://github.com/zuige66/todo"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val DEVELOPER_NAME = "zuige"
private const val DEVELOPER_HANDLE = "zuige66"
private const val DEVELOPER_GITHUB_URL = "https://github.com/zuige66"
private const val DEVELOPER_BLOG_URL = "https://blog.zuiges.com"

/** hero 占屏高的比例。原页是 0.60（锁屏整屏），这里是普通页面，留矮一点让列表能露头。 */
private const val HERO_HEIGHT_FRACTION = 0.46f
private const val BACKGROUND_SPEED = 0.12f
private const val COLOR_INTERPOLATION_SECONDS = 12f

/**
 * 关于页的图标就是**原图本身**（`drawable-nodpi/ic_logo.png`，512px）。
 *
 * 不要再给它套底色 Surface、也不要改回 `@mipmap/ic_launcher`：
 *  - 母图是透明底的紫色波浪花瓣，自带完整轮廓，加底色会把花瓣压掉；
 *  - `@mipmap/ic_launcher` 是 48dp 基准的小图，拉到 88dp 会糊。
 */
@Composable
fun AboutScreen(
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    listState: androidx.compose.foundation.lazy.LazyListState? = null
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // ★ 必须用调用方传进来的 scroll state：外层顶栏（TodoAppContent 的 TopBarCollapse）
    //   是靠 snapshotFlow 监听**这一个** LazyListState 来实现沉浸式收起的。
    //   v0.4.2 这里写成 `val listState = rememberLazyListState()`，把参数遮住了 ——
    //   结果顶栏在关于页永远收不起来（用户看到的"关于页上面还有一条导航栏"就是这个）。
    val state = listState ?: rememberLazyListState()
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeight * HERO_HEIGHT_FRACTION
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val backgroundFadeDistance = with(density) { 360.dp.toPx() }
    val logoFadeStart = heroHeightPx * 0.25f
    val logoFadeDistance = heroHeightPx * 0.35f

    // ── 检查更新 ─────────────────────────────────────────────────────────────
    var updateUi by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var failureReason by remember { mutableStateOf<String?>(null) }
    /** APK 下载进度（0..100）；-1 = 未在下载。由前台服务的显式广播驱动。 */
    var downloadPercent by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    // 下载进度只在页面可见时有意义：切到别的 tab 就解绑，回来重新等广播
    // （服务是前台服务，下载本身不受影响）。
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val percent = intent.getIntExtra(UpdateDownloadService.EXTRA_PROGRESS, -1)
                val done = intent.getBooleanExtra(UpdateDownloadService.EXTRA_DONE, false)
                downloadPercent = if (done) -1 else percent.coerceIn(0, 100)
            }
        }
        val filter = IntentFilter(UpdateDownloadService.ACTION_UPDATE_PROGRESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // 只读 firstVisibleItem* 会漏掉「hero 还在屏幕上、列表已开始滑」的那一段，
    // 所以第 0 项时要取 scrollOffset，之后直接当作已经滚过整个 hero。
    //
    // 刻意不用 `by`：下面所有读取都发生在 graphicsLayer 的 draw 阶段，传 State 进去
    // 才走「延迟读取」——每滚一个像素只重画、不重组。一旦在组合阶段解包，
    // LazyColumn 和整页都会跟着每一像素重组一次。
    val scrollOffset = remember(state, heroHeightPx) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) heroHeightPx
            else state.firstVisibleItemScrollOffset.toFloat()
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

    /**
     * 检查更新。
     *
     * 三重防重复：函数入口 guard、按钮 [enabled]、以及"失败只写一个 [failureReason]"——
     * 连点十下也只会有一个弹窗实例（同一个状态值不会叠出第二个对话框）。
     */
    fun requestUpdateCheck() {
        if (updateUi is UpdateUi.Checking) return
        updateUi = UpdateUi.Checking
        scope.launch {
            when (val result = UpdateService.check(versionName)) {
                is UpdateCheck.UpToDate -> {
                    updateUi = UpdateUi.UpToDate
                    Toast.makeText(context, "已是最新版本 v$versionName", Toast.LENGTH_SHORT).show()
                }
                is UpdateCheck.NoRelease -> {
                    updateUi = UpdateUi.NoRelease
                    Toast.makeText(context, "仓库还没有发布过版本", Toast.LENGTH_SHORT).show()
                }
                is UpdateCheck.Available -> {
                    updateUi = UpdateUi.Idle
                    availableUpdate = result.update
                }
                is UpdateCheck.Failed -> {
                    updateUi = UpdateUi.Idle
                    failureReason = result.reason
                }
            }
        }
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
                    translationY = -state.firstVisibleItemScrollOffset * 0.12f
                }
        )

        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                // 顶栏是浮层（详情见 TodoListScreen 的 TopBarCollapse），这里让出它的高度。
                top = topPadding,
                end = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                // hero 是叠在上层的（不是列表项），这里留出等高空位让它可见。
                Spacer(Modifier.height(heroHeight))
            }
            item { AboutSectionTitle("开发者") }
            item { DeveloperCard() }
            item { AboutSectionTitle("应用") }
            item {
                AboutCard {
                    InfoRow("版本", "$versionName ($versionCode)")
                    AboutDivider()
                    InfoRow("包名", context.packageName)
                    AboutDivider()
                    UpdateRow(
                        ui = updateUi,
                        downloadPercent = downloadPercent,
                        enabled = updateUi !is UpdateUi.Checking && downloadPercent < 0,
                        onClick = { requestUpdateCheck() }
                    )
                }
            }
            item { AboutSectionTitle("项目") }
            item {
                AboutCard {
                    LinkRow("GitHub 仓库", "zuige66/todo") { openUrl(context, REPO_URL) }
                    AboutDivider()
                    LinkRow("更新与下载", "GitHub Releases") { openUrl(context, RELEASES_URL) }
                    AboutDivider()
                    LinkRow("问题反馈", "GitHub Issues") { openUrl(context, ISSUES_URL) }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
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
                // 原图直出：不套底色、不裁切。母图 1024px 透明底，波浪花瓣完整保留。
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                Text(
                    // 跟着 app_name 走，别再写死 —— 应用名改一次要动的地方越少越好。
                    stringResource(R.string.app_name),
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

        // v0.4：关于页是导航 tab，左上角不再需要返回按钮（系统返回键也不该被它拦下）。

        // ── 更新弹窗 ──
        // 两个弹窗都由单一状态驱动，所以"连点十次"不会叠出十个对话框。
        availableUpdate?.let { update ->
            UpdateAvailableDialog(
                update = update,
                currentVersion = versionName,
                onDismiss = { availableUpdate = null },
                onDownload = {
                    availableUpdate = null
                    ApkInstaller.downloadAndInstall(context, update.apkUrl, update.version)
                },
                onOpenPage = {
                    availableUpdate = null
                    openUrl(context, update.releaseUrl)
                }
            )
        }
        failureReason?.let { reason ->
            UpdateFailedDialog(reason = reason, onDismiss = { failureReason = null })
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

/**
 * 「检查更新」行：右侧是状态区。
 *
 * 状态优先级：下载中 > 检查中 > 文字状态 > 箭头。
 * 下载中的百分比来自前台服务的显式广播 —— 部分 ROM 会把前台服务通知折叠，
 * 应用内这个数字才是用户真正看得到的进度。
 */
@Composable
private fun UpdateRow(
    ui: UpdateUi,
    downloadPercent: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val checking = ui is UpdateUi.Checking
    val status = when {
        downloadPercent >= 0 -> "下载中 $downloadPercent%"
        checking -> "检查中…"
        ui is UpdateUi.UpToDate -> "已是最新"
        ui is UpdateUi.NoRelease -> "暂无发布版本"
        else -> null
    }
    ListItem(
        headlineContent = { Text("检查更新", style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                if (ui is UpdateUi.UpToDate) "当前已是最新版本"
                else "从 GitHub Releases 获取最新版本",
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (checking || downloadPercent >= 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = cs.primary
                    )
                }
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.primary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun LinkRow(title: String, summary: String, onClick: () -> Unit) {    ListItem(
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
 * 开发者卡片：头像 + 名称 + GitHub 号 + 箭头，整头可点、就地弹性展开/收起
 * （`AnimatedVisibility` 的 expandVertically + 箭头旋转 90°），展开区是 GitHub / Blog 两项外链。
 *
 * 头像用真图 `res/drawable-nodpi/dev_avatar.jpg`（500×500，与 HyperMeloLock 关于页同一张），
 * 圆裁 56dp。早先偷懒画了个渐变圆 + 首字母"z"，那个是占位，不是成品。
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
        modifier = Modifier.fillMaxWidth()
    ) {
        // ★ 这层 Column 不是装饰，是修 bug 的：
        // `Surface` 的 content **没有作用域**，所有子节点都被塞进同一个 Box（Z 轴堆叠），
        // 所以 `Row(...); if (expanded) Column(...)` 会让展开区直接**压在卡头上**。
        // 原版 HyperMeloLock 用的是 `Card`，Card 的 content 是 ColumnScope，才没有这个问题。
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像与 HyperMeloLock 关于页同一张：res/drawable-nodpi/dev_avatar.jpg（500×500）。
                // 放 drawable-nodpi 是刻意的：不要密度缩放，圆裁后按 56dp 原样裁切。
                Image(
                    painter = painterResource(R.drawable.dev_avatar),
                    contentDescription = "开发者头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
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
            // 展开区用 AnimatedVisibility（带 clip）而不是给整张卡加 animateContentSize：
            // 后者只动画父节点尺寸、**不裁子内容**，展开的那 300ms 里子项会先按最终高度画出来，
            // 看上去就是"内容已经在那了、卡片还在长高"——和上面的重叠 bug 叠在一起特别明显。
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    LinkRow("GitHub", DEVELOPER_HANDLE) { openUrl(context, DEVELOPER_GITHUB_URL) }
                    LinkRow("博客", "blog.zuiges.com") { openUrl(context, DEVELOPER_BLOG_URL) }
                }
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
