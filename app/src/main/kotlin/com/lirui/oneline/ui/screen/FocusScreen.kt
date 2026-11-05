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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.DayTotal
import com.lirui.oneline.util.FocusStats
import com.lirui.oneline.util.FocusSummary
import com.lirui.oneline.util.SettingsRepo
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
                    PAGE_STATS -> FocusStatsPage(
                        sessions = vm.sessions.collectAsState().value,
                        goalMinutes = vm.dailyGoalMinutes,
                        onSetGoal = vm::setDailyGoal
                    )
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

/**
 * 空闲态：模式 → 时长 → 开始。全部贴在底部，中间只留环。
 *
 * 说明输入框（原来的「在专注什么？」）已按 zuige 的要求下线 —— 每次开始前多一次
 * 输入收益很低，专注页的主任务是"按下开始"。`FocusRun.label` 字段保留，
 * 历史记录里已写过的说明照常显示，记录列表也不会变成一片空白。
 */
@Composable
private fun IdleControls(vm: FocusViewModel) {
    ModeSwitch(countUp = vm.countUp, onSelect = vm::selectMode)

    if (!vm.countUp) {
        Spacer(Modifier.height(12.dp))
        DurationPicker(selected = vm.minutes, onSelect = { vm.selectMinutes(it) })
    }

    Spacer(Modifier.height(18.dp))
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

/**
 * 时长选择：四个预设 + 一个「自定义」格。
 *
 * ★ 第五格的文字会跟着状态变：
 *   - 没选自定义 → 显示 `+`，提示"这里还能自己定"；
 *   - 选了自定义 → 直接显示那个分钟数（比如 `35`）并高亮。
 *   如果固定写「自定义」三个字，64dp 宽的格子里会挤成两行，而且选完也看不出选了多久。
 */
@Composable
private fun DurationPicker(selected: Int, onSelect: (Int) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }
    val isCustom = selected !in FocusRules.PRESET_MINUTES

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FocusRules.PRESET_MINUTES.forEach { minutes ->
            DurationCell(
                text = "$minutes",
                active = minutes == selected,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(minutes) }
            )
        }
        DurationCell(
            text = if (isCustom) "$selected" else "+",
            active = isCustom,
            modifier = Modifier.weight(1f),
            onClick = { showCustom = true }
        )
    }

    if (showCustom) {
        DurationInputDialog(
            title = "自定义时长",
            initial = if (isCustom) selected else 30,
            // 1 分钟 ~ 10 小时：低于 1 分钟记不出东西，超过 10 小时没人会真走完。
            min = 1,
            max = 600,
            quick = listOf(10, 20, 30, 90),
            onDismiss = { showCustom = false },
            onConfirm = {
                onSelect(it)
                showCustom = false
            }
        )
    }
}

@Composable
private fun DurationCell(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.cardPlaceholder
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 分钟数输入对话框。两处在用：自定义专注时长、每日目标。
 *
 * ★ 用数字输入框而不是滚轮 / TimePicker：
 *   - TimePicker 选的是"时刻"，拿来选"90 分钟"会变成 01:30，语义错位；
 *   - 滚轮要自己写循环吸附，收益不值那几十行。
 *   直接打字最快，旁边给几个常用值兜底（不想打字的人点一下就走）。
 */
@Composable
private fun DurationInputDialog(
    title: String,
    initial: Int,
    min: Int,
    max: Int,
    quick: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in min..max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    // 只收数字，最多三位：挂个中文输入法也不会变成"三十"这种盘外值。
                    onValueChange = { raw -> text = raw.filter { it.isDigit() }.take(3) },
                    label = { Text("分钟") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.fieldSurface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.fieldSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.fieldBorder,
                        unfocusedBorderColor = MaterialTheme.colorScheme.fieldBorder
                    )
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { q ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.cardPlaceholder)
                                .clickable { text = q.toString() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$q", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (value != null) onConfirm(value) },
                enabled = valid
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 统计
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FocusStatsPage(
    sessions: List<FocusSession>,
    goalMinutes: Int,
    onSetGoal: (Int) -> Unit
) {
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
        // ★ 主次分明：今日是"现在"（用户最关心），放大做主数字；
        //   本周 / 累计是"背景"，两张卡片并排。原来的三张等宽卡片在 392dp 屏上
        //   每张只有 ~87dp，「12小时30分」这种最长组合必被省略号截掉。
        item { TodayCard(summary, goalMinutes, onSetGoal) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PeriodCard("本周", summary.weekSeconds, summary.weekCount, Modifier.weight(1f))
                PeriodCard("累计", summary.totalSeconds, summary.totalCount, Modifier.weight(1f))
            }
        }

        item {
            val avg = last7.sumOf { it.seconds } / 7
            FocusCard {
                SectionHeader("最近 7 天", if (avg > 0) "日均 ${FocusRules.formatDuration(avg)}" else null)
                Spacer(Modifier.height(14.dp))
                WeekBars(last7, today)
            }
        }

        item {
            FocusCard {
                SectionHeader("近 15 周", monthSpan(heat))
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

/**
 * 今日主卡片：环形进度（今日 / 每日目标）+ 时长 + 连续天数。
 *
 * ★ 为什么用环而不是一行大数字：专注是"今天要练满一段时间"的事，
 *   有刻度才有"还差多少"，纯数字只能回答"已经多少"。环同时给出两者，
 *   而且达成（progress 满圈）是一次看得见的奖励。
 *
 * ★ 环里只放百分比，不放时长：环内径约 80dp，`2小时15分` 六个字塞进去会
 *   被迫缩到 11sp 以下，反而看不清。时长放在环右侧，字号可以正常。
 */
@Composable
private fun TodayCard(summary: FocusSummary, goalMinutes: Int, onSetGoal: (Int) -> Unit) {
    var showGoalDialog by remember { mutableStateOf(false) }
    val started = summary.todaySeconds > 0
    val goalSeconds = (goalMinutes.coerceAtLeast(1)) * 60
    val progress = (summary.todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    val percent = summary.todaySeconds * 100 / goalSeconds
    val reached = summary.todaySeconds >= goalSeconds

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.cardBackground)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoalRing(
            size = 96.dp,
            progress = progress,
            text = if (reached) "达成" else "$percent%"
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日专注",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (summary.streakDays > 0) {
                    Text(
                        "连续 ${summary.streakDays} 天",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (started) FocusRules.formatDuration(summary.todaySeconds) else "还没开始",
                // 22sp：右侧可用宽度约 210dp，「12小时30分」≈ 132dp，不会截断。
                style = TextStyle(
                    fontSize = if (started) 22.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = if (started) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "目标 ${FocusRules.formatDuration(goalSeconds)}" +
                        if (summary.todayCount > 0) " · ${summary.todayCount} 次" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "调整目标",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showGoalDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (showGoalDialog) {
        DurationInputDialog(
            title = "每日专注目标",
            initial = goalMinutes,
            min = SettingsRepo.MIN_FOCUS_GOAL_MINUTES,
            max = SettingsRepo.MAX_FOCUS_GOAL_MINUTES,
            quick = listOf(60, 120, 180, 240),
            onDismiss = { showGoalDialog = false },
            onConfirm = {
                onSetGoal(it)
                showGoalDialog = false
            }
        )
    }
}

/**
 * 进度环。底色整圈 + 主色从 12 点顺时针扫 `progress`。
 *
 * 满圈时 `sweepAngle` 正好 360，圆头笔帽会在起点叠一下 —— 视觉上是一圈完整的环，
 * 不是 bug，所以不做特殊处理。
 */
@Composable
private fun GoalRing(size: Dp, progress: Float, text: String) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.cardPlaceholder
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            drawCircle(color = track, radius = radius, style = Stroke(width = stroke))
            if (progress > 0.002f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text,
            style = TextStyle(
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 本周 / 累计。两列后每列宽 ~171dp，17sp 的「12小时30分」不会再被截。 */
@Composable
private fun PeriodCard(label: String, seconds: Int, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.cardBackground)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (seconds <= 0) "—" else FocusRules.formatDuration(seconds),
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
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

/** 卡片标题行：左边标题，右边一句补充（日均时长 / 月份跨度）。没有补充时右边留空。 */
@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekBars(days: List<DayTotal>, today: LocalDate) {
    val accent = MaterialTheme.colorScheme.primary
    val maxSeconds = (days.maxOfOrNull { it.seconds } ?: 0).coerceAtLeast(1)

    Row(
        Modifier
            .fillMaxWidth()
            // 140dp = 旧版 120dp 的柱区（96dp）+ 顶上那一行分钟数（16dp）+ 间隔。
            // 数字行不挤占柱子的高度，否则 7 天的相对高低会被压扁。
            .height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val isToday = day.date == today
            Column(
                // ★ fillMaxHeight 不能省：Row 给了固定高度，但 Column 自己默认是 wrap-content，
                //   里面的 weight 就没有可分配的高度，柱子会塌成 0（看着像"没数据"）。
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 柱顶分钟数。没有数据时也占一行（空串），否则每根柱子的基线会高低不齐。
                Text(
                    if (day.seconds > 0) minuteLabel(day.seconds) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 轨道：没有数据时也有 7 根浅色柱子，这一块才读得出"这是柱状图"，
                    // 而不是一片空卡片（第一版就是一片空，看着像渲染失败）。
                    // 今天那根轨道染一点主色，扫一眼就知道"最后一根是今天"。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isToday) accent.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
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
                    fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                    color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 柱顶数字：向上取整的分钟数（59 秒也显示 1，不出现"有柱子但标 0"）。 */
private fun minuteLabel(seconds: Int): String = ((seconds + 59) / 60).toString()

@Composable
private fun Heatmap(cells: List<DayTotal>) {
    val accent = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.cardPlaceholder
    val weeks = (cells.size / 7).coerceAtLeast(1)
    val gap = 3.dp

    // ★ 用 weight + aspectRatio 让布局自己均分，不再手算像素。
    //   旧写法按 `gap * (weeks - 1)` 扣间隙，但 Row 里其实有 16 个子项
    //   （星期标签列 + 15 个周列）→ 15 个间隙，少扣 3dp 就整行超宽，
    //   最后一列被挤成一条窄竖条（zuige 报的「最右边卡成竖着矩形」）。
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        // 星期标签列：宽度固定，格子用 aspectRatio 保证与右侧方块同高同排。
        Column(
            Modifier.width(14.dp),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            // 只标 一/三/五/日，7 行全标会挤成一列看不清的字。
            listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
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
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                (0 until 7).forEach { d ->
                    val day = cells.getOrNull(w * 7 + d)
                    val seconds = day?.seconds ?: 0
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatColor(base, accent, seconds))
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    // 图例：五档色阶的意义要能直接读出来。原来只有一句"越深越久"，
    // 用户没法判断"深到什么程度算多" —— 现在把 0 / 10 分 / 20 分 / 45 分 / 90 分
    // 五档并排放出来，右边一个"多"就是刻度。
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "一格一天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            "少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        listOf(0, 10 * 60, 20 * 60, 45 * 60, 90 * 60).forEach { seconds ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(heatColor(base, accent, seconds))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 热力图的月份跨度：`8月 – 11月`。
 *
 * 不再往每列上面标月份 —— 一列只有十几 dp 宽，`10月` 三个字塞不下，
 * 要么被压成竖排要么溢出到邻列。一句跨度已经够回答"这张图覆盖到什么时候"。
 */
private fun monthSpan(days: List<DayTotal>): String? {
    if (days.isEmpty()) return null
    val from = days.first().date
    val to = days.last().date
    return when {
        from.year != to.year -> "${from.year % 100}年${from.monthValue}月 – ${to.monthValue}月"
        from.monthValue == to.monthValue -> "${from.monthValue}月"
        else -> "${from.monthValue}月 – ${to.monthValue}月"
    }
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
