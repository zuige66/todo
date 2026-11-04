package com.lirui.oneline.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lirui.oneline.data.model.FocusSession
import com.lirui.oneline.focus.FocusRules
import com.lirui.oneline.focus.FocusRun
import com.lirui.oneline.ui.FocusViewModel
import com.lirui.oneline.ui.theme.cardBackground
import com.lirui.oneline.ui.theme.cardPlaceholder
import com.lirui.oneline.ui.theme.fieldBorder
import com.lirui.oneline.ui.theme.fieldSurface
import com.lirui.oneline.ui.theme.placeholderText
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.DayTotal
import com.lirui.oneline.util.FocusStats
import com.lirui.oneline.util.FocusSummary
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/**
 * 全屏专注页。
 *
 * 从首页顶栏的计时器图标进来（见 TodoAppContent），是**应用内覆盖层**，
 * 和 AddSheet / PrivacyScreen 同一套模式 —— 不是第五个 tab。
 * 理由见 DESIGN.md：专注是"临时模式"，不是"常驻浏览页"，塞进底部导航会把
 * 四个已有入口的层级一起稀释掉。
 *
 * 两态：
 *  - **计时**：圆环倒计时 + 时长选择 + 开始/暂停/结束
 *  - **统计**：今日/本周/累计、连续天数、最近 7 天、15 周热力图、记录列表
 */
@Composable
fun FocusScreen(onClose: () -> Unit) {
    val vm: FocusViewModel = viewModel()
    val context = LocalContext.current
    var page by remember { mutableStateOf(PAGE_TIMER) }

    BackHandler { onClose() }

    // 进页面先跟磁盘对一次账：用户可能在通知栏里点过暂停/结束。
    LaunchedEffect(Unit) { vm.syncFromStore() }

    // 显示时钟。页面关掉这个循环就没了，不在后台空转。
    LaunchedEffect(Unit) {
        while (true) {
            vm.tick()
            delay(250)
        }
    }

    LaunchedEffect(Unit) {
        vm.toasts.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ★ 必须用 Surface 而不是 Box + background。
    //   `background()` 只画底色，**不提供 LocalContentColor** —— 于是所有没显式写
    //   color 的 Text 都回退成纯黑，深色模式下就是黑底黑字（"标题和统计数值全不见了"）。
    //   Surface 同时给出 surface + onSurface 一对语义色，一处解决。
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // ── 顶栏：返回 + 计时/统计 ──
            // 不再放「专注」标题：这一页只有一个功能，标题是纯占位。
            // 两个页签提到最上面，视线第一落点就是"我在哪一页"。
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(Modifier.weight(1f))
                SegmentedPill(selected = page, onSelect = { page = it })
                Spacer(Modifier.weight(1f))
                // 与左侧 IconButton 等宽，保证分段控件真正居中。
                Spacer(Modifier.width(48.dp))
            }

            // ★ 用 weight(1f) 而不是让子页面 fillMaxSize()：Column 里 fillMaxSize 的
            //   可用高度取决于测量顺序，顶栏一改就会溢出到屏幕外。weight 是确定性的。
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (page) {
                    PAGE_TIMER -> FocusTimerPage(vm)
                    PAGE_STATS -> FocusStatsPage(vm.sessions.collectAsState().value)
                }
            }
        }
    }

    vm.askDiscardSeconds?.let { seconds ->
        AlertDialog(
            onDismissRequest = { vm.answerDiscard(false) },
            title = { Text("只专注了 ${FocusRules.formatDuration(seconds)}") },
            text = { Text("要把这段时间记进统计吗？") },
            confirmButton = {
                TextButton(onClick = { vm.answerDiscard(true) }) { Text("记下来") }
            },
            dismissButton = {
                TextButton(onClick = { vm.answerDiscard(false) }) { Text("不记") }
            }
        )
    }
}

private const val PAGE_TIMER = 0
private const val PAGE_STATS = 1

// ─────────────────────────────────────────────────────────────────────────────
// 计时
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FocusTimerPage(vm: FocusViewModel) {
    val run = vm.run
    val now = vm.nowMillis

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ── 中间：环 ──
        //
        // ★ 环的尺寸跟着可用空间走，不写死 264dp：
        //   底部控制区在空闲态比运行态高（模式 + 时长 + 输入框 + 按钮），
        //   小屏上写死尺寸会把环挤出屏幕。BoxWithConstraints 取 min(宽, 高) 最稳。
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val ringSize = minOf(maxWidth, maxHeight - 16.dp).coerceIn(132.dp, 280.dp)
            val fraction = if (run == null) 1f else FocusRules.remainingFraction(run, now)
            val clock = when {
                run == null -> if (vm.countUp) "00:00" else FocusRules.formatClock(vm.minutes * 60)
                run.countUp -> FocusRules.formatClock(FocusRules.elapsedSeconds(run, now))
                else -> FocusRules.formatClock(displayRemainingSeconds(run, now))
            }
            val caption = when {
                run == null -> "准备开始"
                run.paused -> "已暂停"
                run.countUp -> "正计时"
                else -> "专注中"
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FocusRing(
                    size = ringSize,
                    clock = clock,
                    fraction = fraction,
                    caption = caption,
                    dimmed = run != null && run.paused
                )
                if (run != null && run.label.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        run.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // ── 底部：控制区 ──
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (run == null) IdleControls(vm) else RunningControls(vm, run)
        }
    }
}

/** 空闲态：模式 → 时长 → 说明 → 开始。全部贴在底部，中间只留环。 */
@Composable
private fun IdleControls(vm: FocusViewModel) {
    ModeSwitch(countUp = vm.countUp, onSelect = vm::selectMode)

    if (!vm.countUp) {
        Spacer(Modifier.height(12.dp))
        DurationPicker(selected = vm.minutes, onSelect = { vm.selectMinutes(it) })
    }

    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = vm.label,
        onValueChange = { vm.editLabel(it) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text("在专注什么？（可不填）", color = MaterialTheme.colorScheme.placeholderText)
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.fieldSurface,
            unfocusedContainerColor = MaterialTheme.colorScheme.fieldSurface,
            focusedBorderColor = MaterialTheme.colorScheme.fieldBorder,
            unfocusedBorderColor = MaterialTheme.colorScheme.fieldBorder
        )
    )

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = vm::start,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text("开始专注", style = MaterialTheme.typography.titleMedium)
    }
}

/** 运行态：只剩两个按钮，钉在屏幕最下面 —— 视线在环上，手在底部，不打架。 */
@Composable
private fun RunningControls(vm: FocusViewModel, run: FocusRun) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { if (run.paused) vm.resume() else vm.pause() },
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(if (run.paused) "继续" else "暂停", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = vm::requestFinish,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("结束", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 剩余秒数向上取整：刚开始那一刻应该显示 25:00，而不是 24:59。 */
private fun displayRemainingSeconds(run: FocusRun, now: Long): Int =
    ((FocusRules.remainingMillis(run, now) + 999L) / 1000L).toInt()

/** 波浪圆的瓣数，对齐应用图标那枚「印章」。 */
private const val RING_LOBES = 9

/** 波浪起伏幅度（相对半径）。再大会变成花瓣，再小就看不出是波浪。 */
private const val RING_WAVE = 0.055f

/**
 * 波浪圆路径：半径按 `1 + a·cos(9θ)` 起伏，从 12 点开始顺时针。
 *
 * 用 480 段折线逼近 —— 这个尺寸下肉眼完全看不出折角，
 * 而且比手写贝塞尔可控（瓣数和幅度改一个常数就行）。
 */
private fun wavyCirclePath(center: Offset, radius: Float): Path {
    val path = Path()
    val steps = 480
    val twoPi = (2.0 * Math.PI).toFloat()
    val quarterTurn = (Math.PI / 2.0).toFloat()
    for (i in 0..steps) {
        val a = (i.toFloat() / steps) * twoPi
        val r = radius * (1f + RING_WAVE * kotlin.math.cos(RING_LOBES * a))
        val x = center.x + r * kotlin.math.cos(a - quarterTurn)
        val y = center.y + r * kotlin.math.sin(a - quarterTurn)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun FocusRing(size: Dp, clock: String, fraction: Float, caption: String, dimmed: Boolean) {
    val accent = if (dimmed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.cardPlaceholder

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = (this.size.minDimension * 0.05f).coerceIn(8.dp.toPx(), 16.dp.toPx())
            val radius = this.size.minDimension / 2f - stroke
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val path = wavyCirclePath(center, radius)

            // 底环：整圈浅色，告诉用户"这里是一整圈"
            drawPath(path, track, style = Stroke(width = stroke, cap = StrokeCap.Round))

            // 进度：沿同一条波浪路径截一段 —— 所以进度也是波浪形的，
            // 不是把圆环盖在一个波浪底上（那样两圈形状对不上，很脏）。
            if (fraction > 0.002f) {
                val measure = PathMeasure().apply { setPath(path, false) }
                val segment = Path()
                measure.getSegment(0f, measure.length * fraction, segment, true)
                drawPath(segment, accent, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                clock,
                style = TextStyle(
                    // 字号跟着环走：环小的时候 52sp 会撑破。
                    fontSize = (size.value * 0.20f).sp,
                    fontWeight = FontWeight.Medium,
                    // 等宽数字：不然每秒跳动时整串数字左右抖，很廉价。
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 倒计时 / 正计时切换。小一号的分段控件，不跟顶部的「计时 / 统计」抢注意力。 */
@Composable
private fun ModeSwitch(countUp: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.cardPlaceholder)
            .padding(3.dp)
    ) {
        listOf("倒计时" to false, "正计时" to true).forEach { (label, value) ->
            val active = value == countUp
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 22.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DurationPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FocusRules.PRESET_MINUTES.forEach { minutes ->
            val active = minutes == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.cardPlaceholder
                    )
                    .clickable { onSelect(minutes) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$minutes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 统计
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FocusStatsPage(sessions: List<FocusSession>) {
    val today: LocalDate = AppTime.today()
    val summary = remember(sessions, today) { FocusStats.summary(sessions, today) }
    val last7 = remember(sessions, today) { FocusStats.lastDays(sessions, today, 7) }
    val heat = remember(sessions, today) { FocusStats.heatmap(sessions, today, 15) }
    val recent = remember(sessions) { sessions.take(100) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SummaryCards(summary) }

        item {
            FocusCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "连续专注",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (summary.streakDays > 0) "${summary.streakDays} 天" else "还没开始",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            FocusCard {
                SectionTitle("最近 7 天")
                Spacer(Modifier.height(14.dp))
                WeekBars(last7)
            }
        }

        item {
            FocusCard {
                SectionTitle("近 15 周")
                Spacer(Modifier.height(12.dp))
                Heatmap(heat)
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("记录")
        }

        if (recent.isEmpty()) {
            item {
                Text(
                    "还没有记录。回「计时」开一次试试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(recent, key = { it.id }) { session -> RecordRow(session) }
        }
    }
}

@Composable
private fun SummaryCards(summary: FocusSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("今日", summary.todaySeconds, summary.todayCount, Modifier.weight(1f))
        StatCard("本周", summary.weekSeconds, summary.weekCount, Modifier.weight(1f))
        StatCard("累计", summary.totalSeconds, summary.totalCount, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, seconds: Int, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.cardBackground)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (seconds <= 0) "—" else FocusRules.formatDuration(seconds),
            // 15sp 不是随手定的：卡片在 392dp 屏上只有 ~87dp 可用宽度，
            // 「12小时30分」这种最长组合在 17sp 会顶到边上被省略号截掉。
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (count <= 0) "0 次" else "$count 次",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FocusCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.cardBackground)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun WeekBars(days: List<DayTotal>) {
    val accent = MaterialTheme.colorScheme.primary
    val maxSeconds = (days.maxOfOrNull { it.seconds } ?: 0).coerceAtLeast(1)

    Row(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            Column(
                // ★ fillMaxHeight 不能省：Row 给了固定 120dp，但 Column 自己默认是 wrap-content，
                //   里面的 weight 就没有可分配的高度，柱子会塌成 0（看着像"没数据"）。
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 轨道：没有数据时也有 7 根浅灰柱子，这一块才读得出"这是柱状图"，
                    // 而不是一片空卡片（第一版就是一片空，看着像渲染失败）。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    )
                    if (day.seconds > 0) {
                        val frac = (day.seconds.toFloat() / maxSeconds).coerceIn(0.08f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(frac)
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    weekdayCn(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Heatmap(cells: List<DayTotal>) {
    val accent = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.cardPlaceholder
    val weeks = (cells.size / 7).coerceAtLeast(1)
    val gap = 3.dp

    // ★ 格子尺寸必须按可用宽度算，不能写死。
    //   写死 13dp 在 392dp 宽的机器上只占 252dp，右边空出一大块（第一版就是这样）；
    //   写死成更大的值，360dp 的小屏又会溢出。BoxWithConstraints 是唯一稳的做法。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = 14.dp
        val available = maxWidth - labelWidth - 2.dp - gap * (weeks - 1)
        val cell = (available / weeks).coerceIn(8.dp, 20.dp)

        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            // 只标 一/三/五/日，7 行全标会挤成一列看不清的字。
            Column(
                Modifier.padding(end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (label.isNotEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            (0 until weeks).forEach { w ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    (0 until 7).forEach { d ->
                        val day = cells.getOrNull(w * 7 + d)
                        val seconds = day?.seconds ?: 0
                        Box(
                            Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(3.dp))
                                .background(heatColor(base, accent, seconds))
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "一格一天，越深越久",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 0 / 半小时内 / 半小时 / 一小时 / 一小时以上 —— 五档够用，再多就看不出差别了。 */
private fun heatColor(base: Color, accent: Color, seconds: Int): Color {
    val t = when {
        seconds <= 0 -> 0f
        seconds < 15 * 60 -> 0.25f
        seconds < 30 * 60 -> 0.5f
        seconds < 60 * 60 -> 0.75f
        else -> 1f
    }
    return lerp(base, accent, t)
}

@Composable
private fun RecordRow(session: FocusSession) {
    val start = Instant.ofEpochMilli(session.startedAtMillis).atZone(AppTime.ZONE)
    val whenText = "${start.monthValue}月${start.dayOfMonth}日 " +
        String.format(java.util.Locale.US, "%02d:%02d", start.hour, start.minute)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.cardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                session.label.ifBlank { "专注" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                whenText + if (session.completed) "" else " · 提前结束",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            FocusRules.formatDuration(session.actualSeconds),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (session.completed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 小零件
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SegmentedPill(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.cardPlaceholder)
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        listOf("计时", "统计").forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun weekdayCn(date: LocalDate): String =
    "一二三四五六日"[date.dayOfWeek.value - 1].toString()
