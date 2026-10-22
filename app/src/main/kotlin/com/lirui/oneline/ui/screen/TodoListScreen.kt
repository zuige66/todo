@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.lirui.oneline.ui.screen

import android.widget.Toast

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lirui.oneline.data.model.Todo
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.ui.MainViewModel
import com.lirui.oneline.ui.theme.DarkCardStyle
import com.lirui.oneline.ui.theme.Palette
import com.lirui.oneline.ui.theme.Palettes
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.Format
import com.lirui.oneline.util.HomeBoard
import com.lirui.oneline.util.HomeBucket
import com.lirui.oneline.util.HomeGrouping
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAB_TODO = 0
private const val TAB_ARCHIVED = 1
private const val TAB_SETTINGS = 2

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodoApp(
    vm: MainViewModel,
    onExactAlarmSettings: () -> Unit,
    themeMode: Int,
    onSetThemeMode: (Int) -> Unit,
    paletteId: Int,
    onSetPalette: (Int) -> Unit,
    darkCardStyleId: Int,
    onSetDarkCardStyle: (Int) -> Unit,
    remindersEnabled: Boolean,
    onSetRemindersEnabled: (Boolean) -> Unit,
    remindAtDue: Boolean,
    onSetRemindAtDue: (Boolean) -> Unit,
    remindInAdvance: Boolean,
    onSetRemindInAdvance: (Boolean) -> Unit,
    overdueNag: Boolean,
    onSetOverdueNag: (Boolean) -> Unit,
    overdueInterval: Int,
    onSetOverdueInterval: (Int) -> Unit,
    boardOrder: List<HomeBoard>,
    onSetBoardOrder: (List<HomeBoard>) -> Unit,
    hiddenBoards: Set<HomeBoard>,
    onSetHiddenBoards: (Set<HomeBoard>) -> Unit,
    homeBuckets: Set<HomeBucket>,
    onSetHomeBuckets: (Set<HomeBucket>) -> Unit,
    defaultMinuteOfDay: Int,
    onSetDefaultMinuteOfDay: (Int) -> Unit,
    autoRepeat: Boolean,
    onSetAutoRepeat: (Boolean) -> Unit,
    onRescheduleReminders: () -> Unit
) {
    val uiState by vm.state.collectAsState()
    TodoAppContent(
        active = uiState.active,
        archived = uiState.archived,
        vm = vm,
        onExactAlarmSettings = onExactAlarmSettings,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        paletteId = paletteId,
        onSetPalette = onSetPalette,
        darkCardStyleId = darkCardStyleId,
        onSetDarkCardStyle = onSetDarkCardStyle,
        remindersEnabled = remindersEnabled,
        onSetRemindersEnabled = onSetRemindersEnabled,
        remindAtDue = remindAtDue,
        onSetRemindAtDue = onSetRemindAtDue,
        remindInAdvance = remindInAdvance,
        onSetRemindInAdvance = onSetRemindInAdvance,
        overdueNag = overdueNag,
        onSetOverdueNag = onSetOverdueNag,
        overdueInterval = overdueInterval,
        onSetOverdueInterval = onSetOverdueInterval,
        boardOrder = boardOrder,
        onSetBoardOrder = onSetBoardOrder,
        hiddenBoards = hiddenBoards,
        onSetHiddenBoards = onSetHiddenBoards,
        homeBuckets = homeBuckets,
        onSetHomeBuckets = onSetHomeBuckets,
        defaultMinuteOfDay = defaultMinuteOfDay,
        onSetDefaultMinuteOfDay = onSetDefaultMinuteOfDay,
        autoRepeat = autoRepeat,
        onSetAutoRepeat = onSetAutoRepeat,
        onRescheduleReminders = onRescheduleReminders
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodoAppContent(
    active: List<Todo>,
    archived: List<Todo>,
    vm: MainViewModel,
    onExactAlarmSettings: () -> Unit,
    themeMode: Int,
    onSetThemeMode: (Int) -> Unit,
    paletteId: Int,
    onSetPalette: (Int) -> Unit,
    darkCardStyleId: Int,
    onSetDarkCardStyle: (Int) -> Unit,
    remindersEnabled: Boolean,
    onSetRemindersEnabled: (Boolean) -> Unit,
    remindAtDue: Boolean,
    onSetRemindAtDue: (Boolean) -> Unit,
    remindInAdvance: Boolean,
    onSetRemindInAdvance: (Boolean) -> Unit,
    overdueNag: Boolean,
    onSetOverdueNag: (Boolean) -> Unit,
    overdueInterval: Int,
    onSetOverdueInterval: (Int) -> Unit,
    boardOrder: List<HomeBoard>,
    onSetBoardOrder: (List<HomeBoard>) -> Unit,
    hiddenBoards: Set<HomeBoard>,
    onSetHiddenBoards: (Set<HomeBoard>) -> Unit,
    homeBuckets: Set<HomeBucket>,
    onSetHomeBuckets: (Set<HomeBucket>) -> Unit,
    defaultMinuteOfDay: Int,
    onSetDefaultMinuteOfDay: (Int) -> Unit,
    autoRepeat: Boolean,
    onSetAutoRepeat: (Boolean) -> Unit,
    onRescheduleReminders: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Todo?>(null) }
    var justCompleted by remember { mutableStateOf<Todo?>(null) }
    var tab by remember { mutableStateOf(TAB_TODO) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 完成通知自动消失（3 秒）。
    LaunchedEffect(justCompleted) {
        if (justCompleted != null) {
            delay(3000)
            justCompleted = null
        }
    }

    val title = when (tab) {
        TAB_TODO -> "一句话待办"
        TAB_ARCHIVED -> "已归档"
        else -> "设置"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (tab == TAB_TODO) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavTab("待办", tab == TAB_TODO) { tab = TAB_TODO }
                NavTab("已归档", tab == TAB_ARCHIVED) { tab = TAB_ARCHIVED }
                NavTab("设置", tab == TAB_SETTINGS) { tab = TAB_SETTINGS }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                TAB_TODO -> TodoList(
                    active,
                    boardOrder,
                    hiddenBoards,
                    homeBuckets,
                    onCircleTap = { todo ->
                        // Circle animation plays first; archive once it has played (~600ms).
                        vm.archive(todo.id)
                        // A repeating todo rolls forward instead of archiving, and undo cannot
                        // restore that, so only show the completion bar for real archiving.
                        val rollsForward =
                            todo.repeatDays > 0 && SettingsRepo.autoRepeatEnabled(context)
                        if (!rollsForward) justCompleted = todo
                    },
                    onTodoEdit = { todo ->
                        editTarget = todo
                        vm.previewExisting(todo)
                        showAdd = true
                    }
                )
                TAB_ARCHIVED -> ArchivedList(
                    archived,
                    onRestore = { vm.unarchive(it) },
                    onDelete = { vm.delete(it) }
                )
                TAB_SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    onSetThemeMode = onSetThemeMode,
                    paletteId = paletteId,
                    onSetPalette = onSetPalette,
                    darkCardStyleId = darkCardStyleId,
                    onSetDarkCardStyle = onSetDarkCardStyle,
                    remindersEnabled = remindersEnabled,
                    onSetRemindersEnabled = { on ->
                        onSetRemindersEnabled(on)
                        onRescheduleReminders()
                    },
                    remindAtDue = remindAtDue,
                    onSetRemindAtDue = { on ->
                        onSetRemindAtDue(on)
                        onRescheduleReminders()
                    },
                    remindInAdvance = remindInAdvance,
                    onSetRemindInAdvance = { on ->
                        onSetRemindInAdvance(on)
                        onRescheduleReminders()
                    },
                    overdueNag = overdueNag,
                    onSetOverdueNag = { on ->
                        onSetOverdueNag(on)
                        onRescheduleReminders()
                    },
                    overdueInterval = overdueInterval,
                    onSetOverdueInterval = { hours ->
                        onSetOverdueInterval(hours)
                        onRescheduleReminders()
                    },
                    boardOrder = boardOrder,
                    onSetBoardOrder = onSetBoardOrder,
                    hiddenBoards = hiddenBoards,
                    onSetHiddenBoards = onSetHiddenBoards,
                    homeBuckets = homeBuckets,
                    onSetHomeBuckets = onSetHomeBuckets,
                    defaultMinuteOfDay = defaultMinuteOfDay,
                    onSetDefaultMinuteOfDay = onSetDefaultMinuteOfDay,
                    autoRepeat = autoRepeat,
                    onSetAutoRepeat = onSetAutoRepeat,
                    onRescheduleReminders = onRescheduleReminders,
                    onExactAlarmSettings = onExactAlarmSettings,
                    onTestNotification = {
                        if (NotificationHelper.canPostNotifications(context)) {
                            NotificationHelper.postTest(
                                context,
                                "测试通知",
                                "通知权限与提醒渠道正常 ✅"
                            )
                        } else {
                            Toast.makeText(context, "请先允许通知权限", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestTodo = { vm.addTestInOneMinute() }
                )
            }
            // Completion bar overlaps top; only shown while on the todo tab.
            if (tab == TAB_TODO) {
                justCompleted?.let { t ->
                    CompletedBar(
                        title = t.title,
                        onUndo = {
                            vm.unarchive(t.id)
                            justCompleted = null
                        },
                        onDismiss = { justCompleted = null },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddSheet(
            initialText = editTarget?.let {
                buildString {
                    append(it.title)
                    if (it.detail.isNotBlank()) {
                        append(" ")
                        append(it.detail)
                    }
                    it.dueAtMillis?.let { d ->
                        // Pinned zone — the prefilled sentence must read the same wall clock
                        // the todo was created in, regardless of the device timezone.
                        val z = java.time.Instant.ofEpochMilli(d).atZone(AppTime.ZONE)
                        append(" ")
                        append(if (z.toLocalDate().equals(AppTime.today())) "今天" else Format.date(d))
                        val t = z.toLocalTime()
                        append(if (t.minute == 0) "${t.hour}点" else "${t.hour}点${t.minute}分")
                    }
                }
            } ?: "",
            onDismiss = {
                showAdd = false
                editTarget = null
            },
            onAdd = { text, keepRepeat ->
                if (editTarget != null) {
                    when (vm.edit(editTarget!!.id, text, keepRepeat)) {
                        com.lirui.oneline.ui.AddResult.Success -> {
                            showAdd = false; editTarget = null
                        }
                        else -> {}
                    }
                } else {
                    when (vm.add(text)) {
                        com.lirui.oneline.ui.AddResult.Success -> showAdd = false
                        com.lirui.oneline.ui.AddResult.Blank -> {}
                        com.lirui.oneline.ui.AddResult.DateError -> showAdd = false
                        com.lirui.oneline.ui.AddResult.TimeError -> showAdd = false
                    }
                }
            },
            onPreview = { vm.parse(it) },
            parses = vm.lastParses,
            editing = editTarget != null,
            initialRepeatDays = editTarget?.repeatDays ?: 0
        )
    }
}

/**
 * One shared, palette-derived tint for every "card" in the app: active todo rows,
 * archived rows and the settings cards. Derived in `Theme.kt` from the palette seed,
 * so it stays coordinated in light *and* dark mode.
 */
private val ColorScheme.cardBackground: Color get() = primaryContainer

/**
 * Navigation tab: the outer box keeps the equal-width tap target; the inner pill is
 * sized by the label itself so the colour block *just* covers the text instead of
 * spanning the whole tab. `secondaryContainer` keeps it soft — the previous full
 * `primary` block was far too loud, especially with the vivid palettes.
 */
@Composable
private fun RowScope.NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                )
                .padding(horizontal = 22.dp, vertical = 7.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompletedBar(title: String, onUndo: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Same tinted card tint as every other card, so the undo bar reads as part of the list
    // (surfaceContainerHigh used to be a flat grey that broke the palette).
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.cardBackground,
        shadowElevation = 6.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "已完成「$title」",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                "撤销",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onUndo).padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun TodoList(
    todos: List<Todo>,
    boardOrder: List<HomeBoard>,
    hiddenBoards: Set<HomeBoard>,
    enabledBuckets: Set<HomeBucket>,
    onCircleTap: (Todo) -> Unit,
    onTodoEdit: (Todo) -> Unit
) {
    if (todos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无待办，点右下角 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        // 时间必须自己往前走。原来 `now` 只在重组时取一次，而列表只在数据变化时重组，
        // 所以 23:55 的待办到了 23:59 依旧是"今天"、依旧不标红——看起来就像"过期没提示"。
        var now by remember { mutableStateOf(AppTime.nowMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(20_000)
                now = AppTime.nowMillis()
            }
        }
        val today = AppTime.today()

        // Board membership and red marking share [HomeGrouping.boardOf] → [Format.isOverdue],
        // so a row can never sit under 逾期 without being red.
        val byBoard = todos.groupBy {
            HomeGrouping.boardOf(it.dueAtMillis, today, now, it.hasExactTime)
        }
        val boards = HomeGrouping.visibleBoards(boardOrder, hiddenBoards)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            for (board in boards) {
                val boardItems = byBoard[board].orEmpty().sortedWith(
                    compareBy({ it.dueAtMillis ?: Long.MAX_VALUE }, { it.createdAt })
                )
                if (boardItems.isEmpty()) continue

                item(key = "board_${board.id}") { BoardHeader(board) }

                // Only 有日期 gets sub-grouped, and only when at least one bucket is enabled.
                // An empty selection means "flat list" by design (see HomeGrouping).
                if (board != HomeBoard.DATED || enabledBuckets.isEmpty()) {
                    items(boardItems, key = { it.id }) { todo ->
                        TodoListRow(todo, today, now, onCircleTap, onTodoEdit)
                    }
                    continue
                }

                // Resolve each todo onto a rendered bucket, keeping bucket declaration order.
                val grouped = HashMap<HomeBucket, MutableList<Todo>>()
                for (todo in boardItems) {
                    val due = todo.dueAtMillis ?: continue
                    val natural = HomeGrouping.bucketOf(due, today)
                    val target = HomeGrouping.resolveBucket(natural, enabledBuckets) ?: continue
                    grouped.getOrPut(target) { mutableListOf() } += todo
                }
                for (bucket in HomeBucket.all) {
                    val list = grouped[bucket] ?: continue
                    item(key = "bucket_${bucket.id}") { BucketHeader(bucket) }
                    items(list, key = { it.id }) { todo ->
                        TodoListRow(todo, today, now, onCircleTap, onTodoEdit)
                    }
                }
            }
        }
    }
}

/** One todo row; extracted so the board/bucket branches don't duplicate the overdue logic. */
@Composable
private fun TodoListRow(
    todo: Todo,
    today: java.time.LocalDate,
    now: Long,
    onCircleTap: (Todo) -> Unit,
    onTodoEdit: (Todo) -> Unit
) {
    // 到点即逾期并标红——与板块归属同一个判定函数。
    val overdue = Format.isOverdue(todo.dueAtMillis, today, now, todo.hasExactTime)
    TodoRow(
        todo,
        overdue = overdue,
        onCircleTap = { onCircleTap(todo) },
        onTodoEdit = { onTodoEdit(todo) }
    )
}

/** Board title (逾期 / 有日期 / 无日期) — one step stronger than a bucket title. */
@Composable
private fun BoardHeader(board: HomeBoard) {
    Text(
        board.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
    )
}

/** Bucket title (今日 / 明天 / …) inside the 有日期 board. */
@Composable
private fun BucketHeader(bucket: HomeBucket) {
    Text(
        bucket.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 28.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun ArchivedList(archived: List<Todo>, onRestore: (Long) -> Unit, onDelete: (Long) -> Unit) {
    if (archived.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有已归档的待办", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(archived, key = { it.id }) { todo ->
                // Same tinted card as an active todo, so the two lists look consistent.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.cardBackground)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            todo.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (todo.detail.isNotBlank()) {
                            Text(
                                todo.detail,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        todo.dueAtMillis?.let { due ->
                            Text(
                                Format.dueLine(due),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Text(
                        "恢复",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onRestore(todo.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { onDelete(todo.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: Int,
    onSetThemeMode: (Int) -> Unit,
    paletteId: Int,
    onSetPalette: (Int) -> Unit,
    darkCardStyleId: Int,
    onSetDarkCardStyle: (Int) -> Unit,
    remindersEnabled: Boolean,
    onSetRemindersEnabled: (Boolean) -> Unit,
    remindAtDue: Boolean,
    onSetRemindAtDue: (Boolean) -> Unit,
    remindInAdvance: Boolean,
    onSetRemindInAdvance: (Boolean) -> Unit,
    overdueNag: Boolean,
    onSetOverdueNag: (Boolean) -> Unit,
    overdueInterval: Int,
    onSetOverdueInterval: (Int) -> Unit,
    boardOrder: List<HomeBoard>,
    onSetBoardOrder: (List<HomeBoard>) -> Unit,
    hiddenBoards: Set<HomeBoard>,
    onSetHiddenBoards: (Set<HomeBoard>) -> Unit,
    homeBuckets: Set<HomeBucket>,
    onSetHomeBuckets: (Set<HomeBucket>) -> Unit,
    defaultMinuteOfDay: Int,
    onSetDefaultMinuteOfDay: (Int) -> Unit,
    autoRepeat: Boolean,
    onSetAutoRepeat: (Boolean) -> Unit,
    onRescheduleReminders: () -> Unit,
    onExactAlarmSettings: () -> Unit,
    onTestNotification: () -> Unit,
    onTestTodo: () -> Unit
) {
    val context = LocalContext.current
    // Used to keep at least one board visible.
    val visibleBoardCount = boardOrder.count { it !in hiddenBoards }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item { SectionHeader("提醒") }
        item {
            SettingsCard {
                ToggleItem("开启提醒", "总开关，关闭后不再提醒", remindersEnabled, onSetRemindersEnabled)
                ItemDivider()
                ToggleItem("到点提醒", "到设定时间再提醒一次", remindAtDue, onSetRemindAtDue)
                ItemDivider()
                ToggleItem("提前提醒", "提前 7/3/1 天及 1 小时", remindInAdvance, onSetRemindInAdvance)
            }
        }

        item { SectionHeader("权限") }
        item {
            SettingsCard {
                ListItem(
                    headlineContent = { Text("通知与精确闹钟") },
                    supportingContent = { Text("国产 ROM 可能拦截，请允许通知、精确闹钟和自启动") },
                    trailingContent = {
                        Text(
                            "去设置",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onExactAlarmSettings)
                )
            }
        }
        if (!canScheduleExact(context)) {
            item {
                Text(
                    "未获得精确闹钟权限，提醒可能被系统延后几分钟。请开启上方开关并允许后台运行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        item { SectionHeader("测试") }
        item {
            SettingsCard {
                ListItem(
                    headlineContent = { Text("发送测试通知") },
                    supportingContent = { Text("立刻弹一条通知，检查权限与渠道") },
                    trailingContent = {
                        Text(
                            "发送",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onTestNotification)
                )
                ItemDivider()
                ListItem(
                    headlineContent = { Text("新建测试待办") },
                    supportingContent = { Text("下一分钟到期，验证提醒是否准时") },
                    trailingContent = {
                        Text(
                            "创建",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onTestTodo)
                )
            }
        }

        item { SectionHeader("外观") }
        item {
            SettingsCard {
                ThemeDropdown(themeMode, onSetThemeMode)
                ItemDivider()
                PaletteDropdown(paletteId, onSetPalette)
                ItemDivider()
                // 深色模式下的卡片底色：不再用"种子色叠近黑"，而是三档明度可选。
                ChoiceItem(
                    "深色卡片底色",
                    DarkCardStyle.all.map { it.id to it.label },
                    darkCardStyleId,
                    onSetDarkCardStyle
                )
                Text(
                    DarkCardStyle.byId(darkCardStyleId).sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }

        item { SectionHeader("首页分组") }
        item {
            SettingsCard {
                ListItem(
                    headlineContent = { Text("板块顺序", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(
                            "用 ↑↓ 调整先后，右侧开关控制是否显示",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                boardOrder.forEachIndexed { index, board ->
                    ItemDivider()
                    BoardOrderRow(
                        board = board,
                        canMoveUp = index > 0,
                        canMoveDown = index < boardOrder.lastIndex,
                        visible = board !in hiddenBoards,
                        // 不允许把最后一个可见板块也关掉：首页会变空白，且没有入口回来。
                        canToggle = if (board in hiddenBoards) true else visibleBoardCount > 1,
                        onMoveUp = { onSetBoardOrder(boardOrder.swapped(index, index - 1)) },
                        onMoveDown = { onSetBoardOrder(boardOrder.swapped(index, index + 1)) },
                        onVisibleChange = { on ->
                            onSetHiddenBoards(
                                if (on) hiddenBoards - board else hiddenBoards + board
                            )
                        }
                    )
                }
                ItemDivider()
                Text(
                    "「有日期」板块内的分组",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
                )
                BucketChips(homeBuckets) { bucket, on ->
                    onSetHomeBuckets(if (on) homeBuckets + bucket else homeBuckets - bucket)
                }
                Text(
                    "没勾选的档位会并入下一个已勾选的档位；全部不勾时该板块直接平铺，不再分小标题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }

        item { SectionHeader("逾期处理") }
        item {
            SettingsCard {
                ToggleItem("逾期反复叮嘱", "过期后每隔一段时间再提醒", overdueNag, onSetOverdueNag)
                if (overdueNag) {
                    ItemDivider()
                    ChoiceItem(
                        "提醒间隔",
                        listOf(6 to "6小时", 12 to "12小时", 24 to "1天", 48 to "2天"),
                        overdueInterval,
                        onSetOverdueInterval
                    )
                }
            }
        }

        item { SectionHeader("待办行为") }
        item {
            SettingsCard {
                ChoiceItem(
                    "默认提醒时刻",
                    listOf(9 * 60 to "9:00", 12 * 60 to "12:00", 18 * 60 to "18:00", 21 * 60 to "21:00"),
                    defaultMinuteOfDay,
                    onSetDefaultMinuteOfDay
                )
                ItemDivider()
                ToggleItem("自动顺延", "每天/每周/每月完成后顺延", autoRepeat, onSetAutoRepeat)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 6.dp)
    )
}

/**
 * M3E-style grouped settings card: one large rounded surface per section.
 * Uses the same tinted [ColorScheme.cardBackground] as the todo rows so the whole
 * app reads as one card system.
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.cardBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** A board row in 设置 → 首页分组: order arrows + visibility switch. */
@Composable
private fun BoardOrderRow(
    board: HomeBoard,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    visible: Boolean,
    canToggle: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibleChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(board.label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                }
                Switch(checked = visible, onCheckedChange = onVisibleChange, enabled = canToggle)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** Multi-select chips for the buckets inside 有日期. */
@Composable
private fun BucketChips(enabled: Set<HomeBucket>, onToggle: (HomeBucket, Boolean) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (bucket in HomeBucket.all) {
            val on = bucket in enabled
            FilterChip(
                selected = on,
                onClick = { onToggle(bucket, !on) },
                label = { Text(bucket.label) },
                leadingIcon = if (on) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

/** Swap two entries; an out-of-range index leaves the list untouched (edge arrows). */
private fun <T> List<T>.swapped(i: Int, j: Int): List<T> {
    if (i !in indices || j !in indices) return this
    return toMutableList().also {
        val tmp = it[i]
        it[i] = it[j]
        it[j] = tmp
    }
}

@Composable
private fun ToggleItem(
    label: String,
    sub: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** True when the app may fire exact alarms (Android 12+ gates this behind a permission). */
private fun canScheduleExact(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 31) return true
    val am = context.getSystemService(android.app.AlarmManager::class.java)
    return am?.canScheduleExactAlarms() ?: false
}

/** M3E exposed dropdown for the appearance (follow-system / light / dark). */
@Composable
private fun ThemeDropdown(themeMode: Int, onSetThemeMode: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        SettingsRepo.FOLLOW_SYSTEM to "跟随系统",
        SettingsRepo.LIGHT to "浅色模式",
        SettingsRepo.DARK to "深色模式"
    )
    val current = options.firstOrNull { it.first == themeMode }?.second ?: "跟随系统"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        ListItem(
            headlineContent = { Text("外观", style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(current, style = MaterialTheme.typography.bodySmall) },
            trailingContent = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        // M3 默认菜单只有 4dp 圆角、底色是 surfaceContainer —— 和全局卡片不是一套。
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.cardBackground,
            shadowElevation = 8.dp
        ) {
            options.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSetThemeMode(mode); expanded = false },
                    trailingIcon = if (mode == themeMode) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

/** M3E exposed dropdown listing the balanced palettes and the vivid group. */
@Composable
private fun PaletteDropdown(paletteId: Int, onSetPalette: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = Palettes.byId(paletteId)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        ListItem(
            headlineContent = { Text("配色", style = MaterialTheme.typography.bodyLarge) },
            supportingContent = {
                Text(
                    if (current.vivid) "${current.name} · 鲜艳" else current.name,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.cardBackground,
            shadowElevation = 8.dp
        ) {
            Text(
                "Material 3 Expressive",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            Palettes.balanced().forEach { p -> PaletteItem(p, paletteId, onSetPalette) { expanded = false } }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "鲜艳",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            Palettes.vivid().forEach { p -> PaletteItem(p, paletteId, onSetPalette) { expanded = false } }
        }
    }
}

@Composable
private fun PaletteItem(
    palette: Palette,
    selectedId: Int,
    onSetPalette: (Int) -> Unit,
    onDone: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(palette.name) },
        onClick = { onSetPalette(palette.id); onDone() },
        leadingIcon = {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.primary,
                modifier = Modifier.size(18.dp)
            ) {}
        },
        trailingIcon = if (palette.id == selectedId) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else null
    )
}
/** A labelled set of M3E filter chips (single choice). */
@Composable
private fun ChoiceItem(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    overdue: Boolean,
    onCircleTap: () -> Unit,
    onTodoEdit: () -> Unit
) {
    var checked by remember(todo.id, todo.dueAtMillis) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // iOS-Notes-style long fill arc (600ms) drawn as it fills.
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 0.92f else 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
    )
    // One tinted card per todo, derived from the active palette.
    val cardColor = MaterialTheme.colorScheme.cardBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .combinedClickable(onClick = {}, onLongClick = onTodoEdit)
            .graphicsLayer { if (checked) this.scaleX = scale; this.scaleY = scale }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(
            progress = progress,
            overdue = overdue,
            onClick = {
                if (!checked) {
                    checked = true
                    scope.launch {
                        delay(650)
                        onCircleTap()
                    }
                }
            },
            modifier = Modifier.padding(end = 16.dp)
        )
        val titleAlpha = 1f - 0.4f * progress
        Column(Modifier.weight(1f)) {
            // 最大字 = 事件标题. 逾期标红.
            Text(
                todo.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    checked -> MaterialTheme.colorScheme.outline
                    overdue -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.alpha(titleAlpha)
            )
            if (todo.detail.isNotBlank()) {
                Text(
                    todo.detail,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 逾期时详情同样标红，只降一点透明度保住标题的主次。
                    color = if (overdue) MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Weekday + time stay here, under the title.
            todo.dueAtMillis?.let { due ->
                Text(
                    Format.weekdayTime(due),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Right-hand meta: only the repeat badge + the date.
        todo.dueAtMillis?.let { due ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 10.dp)
            ) {
                repeatLabel(todo.repeatDays)?.let { RepeatBadge(it) }
                Text(
                    Format.monthDay(Format.dueAtMillis(due)),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 每天 / 每周 / 每月 / 每 N 天, or null when the todo does not repeat. */
private fun repeatLabel(days: Int): String? = when {
    days == 1 -> "每天"
    days == 7 -> "每周"
    days >= 28 -> "每月"
    days > 1 -> "每${days}天"
    else -> null
}

@Composable
private fun RepeatBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CheckCircle(progress: Float, overdue: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Uniform primary ring for every todo; overdue switches to the error color.
    val borderColor = if (overdue) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    val fillColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(26.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeW = 2.dp.toPx()
            drawCircle(
                color = borderColor,
                radius = size.minDimension / 2 - strokeW,
                style = Stroke(width = strokeW)
            )
            if (progress > 0f) {
                // filled circle proportional to progress (iOS style)
                drawCircle(
                    color = fillColor,
                    radius = (size.minDimension / 2 - strokeW) * 0.92f,
                    alpha = progress * 0.85f
                )
            }
            if (progress >= 1f) {
                val c = center
                val s = size.minDimension
                drawLine(
                    color = Color.White,
                    start = Offset(c.x - s * 0.16f, c.y),
                    end = Offset(c.x + s * 0.02f, c.y + s * 0.17f),
                    strokeWidth = strokeW + 1f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(c.x + s * 0.02f, c.y + s * 0.17f),
                    end = Offset(c.x + s * 0.2f, c.y - s * 0.15f),
                    strokeWidth = strokeW + 1f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}