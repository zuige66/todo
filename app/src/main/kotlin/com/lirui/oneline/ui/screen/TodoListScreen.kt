@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.lirui.oneline.ui.screen

import android.Manifest
import android.os.Build
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lirui.oneline.data.model.Todo
import com.lirui.oneline.keepalive.DeviceCompat
import com.lirui.oneline.keepalive.KeepAlive
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.ui.MainViewModel
import com.lirui.oneline.ui.CIRCLE_FILL_MILLIS
import com.lirui.oneline.ui.theme.DarkCardStyle
import com.lirui.oneline.ui.theme.Palette
import com.lirui.oneline.ui.theme.Palettes
import com.lirui.oneline.ui.theme.actionContainer
import com.lirui.oneline.ui.theme.actionContent
import com.lirui.oneline.ui.theme.actionDisabledContainer
import com.lirui.oneline.ui.theme.actionDisabledContent
import com.lirui.oneline.ui.theme.cardBackground
import com.lirui.oneline.ui.theme.cardDragging
import com.lirui.oneline.ui.theme.cardPlaceholder
import com.lirui.oneline.ui.theme.fieldBorder
import com.lirui.oneline.ui.theme.fieldSurface
import com.lirui.oneline.ui.theme.placeholderText
import com.lirui.oneline.ui.theme.secondaryActionContent
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.Format
import com.lirui.oneline.util.HomeBoard
import com.lirui.oneline.util.HomeBucket
import com.lirui.oneline.util.HomeGrouping
import com.lirui.oneline.util.NotificationStyle
import com.lirui.oneline.util.Quotes
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAB_TODO = 0
private const val TAB_ARCHIVED = 1
private const val TAB_SETTINGS = 2
private const val TAB_ABOUT = 3

/** 顶栏本体高度（不含状态栏），与 M3 `TopAppBarDefaults.TopAppBarExpandedHeight` 一致。 */
private val TOP_BAR_BODY_HEIGHT = 64.dp

/**
 * 沉浸式顶栏的收起量。
 *
 * ★ v0.4.2 教训：一开始用 `NestedScrollConnection` 驱动（挂 Scaffold / 挂 LazyColumn 都试过），
 *   真机上顶栏纹丝不动 —— 派发链路不可靠（中间隔着 Scaffold 的 SubcomposeLayout，
 *   `consumed` 拿不到滚动量）。所以现在改成**直接读列表的滚动状态**：
 *   纯 Compose state，必然拿得到，而且和滚动量严格 1:1。
 */
private class TopBarCollapseState {
    /** 0 = 完全展开，负值 = 收起多少像素。只在 `graphicsLayer` 里读。 */
    val offset = mutableFloatStateOf(0f)
}

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
    immersiveTopBar: Boolean,
    onSetImmersiveTopBar: (Boolean) -> Unit,
    onRescheduleReminders: () -> Unit
) {
    val uiState by vm.state.collectAsState()
    // v0.4：关于页升级成第四个导航项，不再做覆盖层。
    // 覆盖层的旧理由（"二选一会让列表滚动位置丢失"）已经不成立 —— 四个 tab 都走 `when`，
    // 而 `tab` 本身是 remember 的 state，切走再切回来列表位置由 LazyColumn 自己记着。
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
        immersiveTopBar = immersiveTopBar,
        onSetImmersiveTopBar = onSetImmersiveTopBar,
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
    immersiveTopBar: Boolean,
    onSetImmersiveTopBar: (Boolean) -> Unit,
    onRescheduleReminders: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Todo?>(null) }
    var justCompleted by remember { mutableStateOf<Todo?>(null) }
    var tab by remember { mutableStateOf(TAB_TODO) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 归档中的 id 集合由 ViewModel 持有，行被回收也不会丢 —— 见 MainViewModel.requestArchive。
    val pendingArchive by vm.pendingArchive.collectAsState()

    // ── 沉浸式顶栏 ──
    // ★ 不用 M3 的 enterAlwaysScrollBehavior。它是"先消费滚动去收标题栏、剩下的才给列表"，
    //   再叠加 Scaffold 的 contentPadding 随顶栏高度变化 —— 手指滑 10dp，列表滚 10dp 的同时
    //   内容整体上移 64dp，读起来就是"滑动变快了"（这就是反馈里的现象）。
    //
    // 现在的做法：
    //   1. 顶栏高度**固定**（Scaffold 的 contentPadding 恒定 → 内容不会因为收起而位移）；
    //   2. 顶栏自己用 graphicsLayer 平移，位移量 = 列表**实际滚掉了多少**（1:1，不消费）；
    //   3. 收起上限 64dp，状态栏那一截始终留着，内容不会钻到状态栏后面。
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val statusBarDp = with(density) { statusBarTopPx.toDp() }
    val topBarHeight = statusBarDp + TOP_BAR_BODY_HEIGHT
    // ★★ 收起上限必须是「整条顶栏」= statusBar + 64dp，不能只是 64dp。
    //   顶栏的标题位于状态栏**下方**，只滑 64dp 的话标题正好停在状态栏那一截上，
    //   与状态栏图标叠在一起 —— 这就是"下滑之后上面卡住"的根因（v0.4.2 起的 bug）。
    //   滑满整条高度，顶栏才是真的离场，内容也才真的能滚到状态栏下面（真正沉浸）。
    val maxCollapsePx = with(density) { topBarHeight.toPx() }
    val topBarCollapse = remember { TopBarCollapseState() }

    // ── 四个列表各自的滚动状态（提升到这里，用来驱动顶栏）──
    val todoListState = rememberLazyListState()
    val archivedListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    val activeListState = when (tab) {
        TAB_TODO -> todoListState
        TAB_ARCHIVED -> archivedListState
        TAB_SETTINGS -> settingsListState
        else -> aboutListState
    }
    // 顶栏位移 = 列表滚掉的距离（最多 64dp）。
    //
    // ★ 不消费任何滚动：列表该滚多少滚多少，顶栏只是跟着位移 ——
    //   这是"滑动速度不变"的关键（M3 的 scrollBehavior 会消费滚动 + 改 contentPadding，
    //   两者叠加让用户觉得"滑得特别快"）。
    // ★ 直接读 listState 而不是 nestedScroll：派发链路实测不可靠（顶栏纹丝不动）。
    LaunchedEffect(activeListState, immersiveTopBar) {
        if (!immersiveTopBar) {
            topBarCollapse.offset.floatValue = 0f
            return@LaunchedEffect
        }
        snapshotFlow {
            // 第一项都滚出视口了，说明至少滚过一个 item，肯定超过 64dp。
            if (activeListState.firstVisibleItemIndex > 0) {
                maxCollapsePx
            } else {
                activeListState.firstVisibleItemScrollOffset.toFloat()
            }
        }.collect { scrolled ->
            topBarCollapse.offset.floatValue = -scrolled.coerceIn(0f, maxCollapsePx)
        }
    }

    // 归档失败 / 待办已不存在这类一次性提示。
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 完成通知自动消失（3 秒）。
    LaunchedEffect(justCompleted) {
        if (justCompleted != null) {
            delay(3000)
            justCompleted = null
        }
    }

    val title = when (tab) {
        TAB_TODO -> "Todo"
        TAB_ARCHIVED -> "归档"
        TAB_SETTINGS -> "设置"
        else -> "关于"
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // ★ 自己管状态栏：Scaffold 的 contentWindowInsets 一律清零。
            //   否则"顶栏高度"和"列表 contentPadding"会各自按不同口径加一次状态栏，
            //   一旦某次系统 insets 的派发时机变了（切 tab、全面屏手势切换），
            //   两边就错开，表现就是内容突然钻到状态栏后面 / 顶栏压住状态栏。
            //   现在只有一个口径：statusBarDp 显式加进 bar 与每个列表的 padding。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // 不在这里挂 nestedScroll：connection 已经下放到每个列表的 LazyColumn 上
            // （见 TodoList / ArchivedList / SettingsScreen / AboutScreen）。
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavTab("Todo", tab == TAB_TODO) { tab = TAB_TODO }
                    NavTab("归档", tab == TAB_ARCHIVED) { tab = TAB_ARCHIVED }
                    NavTab("设置", tab == TAB_SETTINGS) { tab = TAB_SETTINGS }
                    NavTab("关于", tab == TAB_ABOUT) { tab = TAB_ABOUT }
                }
            },
            floatingActionButton = {
                // 缩放 + 淡出：切到「归档 / 设置 / 关于」时 FAB 不再"啪"地消失
                AnimatedVisibility(
                    visible = tab == TAB_TODO,
                    enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.6f) + fadeOut()
                ) {
                    FloatingActionButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            }
        ) { padding ->
            // ★ 顶栏不再是 Scaffold 的 topBar。
            //
            // 用 topBar 的时候，列表的视口被压到顶栏下方 —— 顶栏一收起，上面就留下一条
            // 64dp 的空带（内容滚不进去）。现在顶栏是**浮层**，列表视口从状态栏下沿开始，
            // 靠 contentPadding.top 把首项让到标题栏下面，收起时内容正好补上那块位置。
            Box(
                Modifier
                    .fillMaxSize()
                    // 只吃底栏高度：状态栏由每个列表的 contentPadding.top 负责（见 statusBarDp）。
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                when (tab) {
                    TAB_TODO -> TodoList(
                        active,
                        boardOrder,
                        hiddenBoards,
                        homeBuckets,
                        pendingArchive = pendingArchive,
                        topInset = statusBarDp,
                        onCircleTap = { todo ->
                            // 点圆圈：先播 650ms 填充动画，动画走完再写库。计时器在 ViewModel 里，
                            // 行被回收（快速滑动）也不会中断 —— 这是 v0.3 归档失效的根因修复。
                            vm.requestArchive(todo.id, animate = true)
                            // A repeating todo rolls forward instead of archiving, and undo cannot
                            // restore that, so only show the completion bar for real archiving.
                            val rollsForward =
                                todo.repeatDays > 0 && SettingsRepo.autoRepeatEnabled(context)
                            if (!rollsForward) justCompleted = todo
                        },
                        onSwipeArchive = { todo ->
                            // 右滑：行已经滑出屏幕，立即写库，不再等动画。
                            vm.requestArchive(todo.id, animate = false)
                            val rollsForward =
                                todo.repeatDays > 0 && SettingsRepo.autoRepeatEnabled(context)
                            if (!rollsForward) justCompleted = todo
                        },
                        onTodoEdit = { todo ->
                            editTarget = todo
                            vm.previewExisting(todo)
                            showAdd = true
                        },
                        listState = todoListState
                    )
                    TAB_ARCHIVED -> ArchivedList(
                        archived,
                        onRestore = { vm.unarchive(it) },
                        onDelete = { vm.delete(it) },
                        listState = archivedListState,
                        topInset = statusBarDp
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
                        immersiveTopBar = immersiveTopBar,
                        onSetImmersiveTopBar = onSetImmersiveTopBar,
                        onRescheduleReminders = onRescheduleReminders,
                        onExactAlarmSettings = onExactAlarmSettings,
                        onTestNotification = {
                            if (NotificationHelper.canPostNotifications(context)) {
                                NotificationHelper.postTest(
                                    context,
                                    "通知测试",
                                    SettingsRepo.notificationStyle(context).dueWord,
                                    quote = Quotes.random(context)
                                )
                            } else {
                                Toast.makeText(context, "请先允许通知权限", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onTestTodo = { vm.addTestInOneMinute() },
                        listState = settingsListState,
                        topInset = statusBarDp
                    )
                    TAB_ABOUT -> AboutScreen(
                        topPadding = statusBarDp + TOP_BAR_BODY_HEIGHT,
                        listState = aboutListState
                    )
                }

                // Completion bar：浮在内容上方，只在 Todo 页显示
                if (tab == TAB_TODO) {
                    AnimatedContent(
                        targetState = justCompleted,
                        transitionSpec = {
                            if (targetState != null) {
                                (slideInVertically { -it } + fadeIn()) togetherWith fadeOut()
                            } else {
                                fadeIn() togetherWith (slideOutVertically { -it } + fadeOut())
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                        label = "completedBar"
                    ) { t ->
                        if (t != null) {
                            CompletedBar(
                                title = t.title,
                                onUndo = {
                                    vm.unarchive(t.id)
                                    justCompleted = null
                                },
                                onDismiss = { justCompleted = null },
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = statusBarDp + 8.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 顶栏浮层：坐标从屏幕顶端算起，高度固定（状态栏 + 64dp），Scaffold 不参与。
        //
        // 状态栏那一截**自己留**（Spacer），并且给 TopAppBar 传 WindowInsets(0)：
        // 之前交给 TopAppBar 的默认 windowInsets 处理，它会自己加一次状态栏 padding，
        // 于是"顶栏高度"与"内部实际布局"两个口径并存；一旦 insets 派发时机变化就会错位。
        // 现在只有一个口径：外层高度 = statusBar + 64，内部先让出 statusBar 再放 64 的主体。
        Box(
            Modifier
                .fillMaxWidth()
                .height(topBarHeight)
                .align(Alignment.TopCenter)
                // ★★★ graphicsLayer 必须放在 background **之前**。
                //   Compose 的修饰符链是"左外右内"：graphicsLayer 只包住**它右边**的绘制。
                //   v0.5.2/0.5.3 写成了 `.background().clipToBounds().graphicsLayer{}`——
                //   底色画在层外面，translationY 动的是层内的 TopAppBar，底色纹丝不动。
                //   这就是四轮都修不好的真因：文字和底色根本不在同一个坐标系里。
                //   现在顺序是 层 → 底色 → 裁剪：三者一起平移，整条同时离场。
                .graphicsLayer {
                    // 延迟读取：滚动时只改 RenderNode 的 translationY，不重组。
                    // 位移上限 = 整条高度，滑到底就是彻底离场（内容顶到状态栏下面）。
                    translationY = topBarCollapse.offset.floatValue
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clipToBounds()
        ) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(statusBarDp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TOP_BAR_BODY_HEIGHT)
                ) {
                    TopAppBar(
                        title = {
                            Crossfade(
                                targetState = title,
                                animationSpec = tween(180),
                                label = "topBarTitle"
                            ) { t ->
                                Text(
                                    t,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                    // ★ 不给标题单独加淡出。
                                    //   v0.5.1/0.5.2 试过"标题先淡、整条后走"，结果出现一个
                                    //   明确的坏状态：文字已经没了、下面还有一条空底色 ——
                                    //   用户看到的就是"导航栏还在，只是文字消失了"。
                                    //   标题和底色必须**同时**离场：要么一起在，要么一起走。
                                )
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            // 与底部导航栏同一个容器色，上下呼应。
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
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
 * One shared, palette-derived tint for every "card" in the app.
 *
 * 定义已经搬到 `ui/theme/Tokens.kt`（[com.lirui.oneline.ui.theme.cardBackground]）：
 * 这里原本是本文件 private 的属性，导致 AddSheet / 通知预览只能各自写
 * `surfaceContainerHigh` 之类的灰 —— 同一张卡片在三个页面是三种颜色。
 */

/**
 * Navigation tab：外层 Box 保住等宽点击热区，内层胶囊由文字自己撑开。
 *
 * v0.4 从 3 项变 4 项，内边距 22dp -> 12dp：360dp 宽的小屏上每项只剩 ~90dp，
 * 22dp 的内边距会让「Todo」胶囊几乎顶满整格，四项并排看着像四块砖。
 * 12dp 既保住胶囊形态，也不会挤到文字。`maxLines = 1` + 胶囊宽度自适应，
 * 所以「Todo」比「设置」宽一点也不会错位。
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
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
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
    pendingArchive: Set<Long>,
    onCircleTap: (Todo) -> Unit,
    onSwipeArchive: (Todo) -> Unit,
    onTodoEdit: (Todo) -> Unit,
    listState: LazyListState,
    /**
     * 状态栏高度。列表视口从屏幕顶端开始（浮层顶栏不占布局位），所以首项要让出
     * `状态栏 + 顶栏主体`；两者必须由同一个值算出，不能一半靠 Scaffold 一半靠常量。
     */
    topInset: Dp = 0.dp
) {
    val empty = todos.isEmpty()
    // 空状态自己淡入淡出；下面仍是 `if (!empty)` 的原始分支，只是不再用 if/else 二选一，
    // 这样空列表和有列表之间切换时不会"闪"一下。
    AnimatedVisibility(
        visible = empty,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(120)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无待办，点右下角 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!empty) {
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
            // state 提升到外层，顶栏靠它算收起量（见 TodoAppContent）。
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 顶栏是浮层、不占布局位：列表视口从屏幕顶端（状态栏上面）开始，
            // 用 contentPadding 把首项让到标题栏下面。顶栏收起时内容正好补上那块位置，
            // 并继续往上滚到状态栏底下 —— 那个观感才是"沉浸"。
            contentPadding = PaddingValues(
                top = topInset + TOP_BAR_BODY_HEIGHT + 8.dp,
                bottom = 8.dp
            )
        ) {
            for (board in boards) {
                val boardItems = byBoard[board].orEmpty().sortedWith(
                    compareBy({ it.dueAtMillis ?: Long.MAX_VALUE }, { it.createdAt })
                )
                if (boardItems.isEmpty()) continue

                // 「有日期」不显示板块标题：里面本来就是今日/明天/… 这些档位标题，
                // 再叠一层"有日期"纯属噪音；平铺时靠上下的板块标题形成分隔就够了。
                if (board != HomeBoard.DATED) {
                    item(key = "board_${board.id}") { BoardHeader(board, Modifier.animateItem()) }
                }
                // Only 有日期 gets sub-grouped, and only when at least one bucket is enabled.
                // An empty selection means "flat list" by design (see HomeGrouping).
                if (board != HomeBoard.DATED || enabledBuckets.isEmpty()) {
                    items(boardItems, key = { it.id }) { todo ->
                        TodoListRow(
                            todo, today, now, todo.id in pendingArchive,
                            onCircleTap, onSwipeArchive, onTodoEdit,
                            Modifier.animateItem()
                        )
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
                    item(key = "bucket_${bucket.id}") { BucketHeader(bucket, Modifier.animateItem()) }
                    items(list, key = { it.id }) { todo ->
                        TodoListRow(
                            todo, today, now, todo.id in pendingArchive,
                            onCircleTap, onSwipeArchive, onTodoEdit,
                            Modifier.animateItem()
                        )
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
    checked: Boolean,
    onCircleTap: (Todo) -> Unit,
    onSwipeArchive: (Todo) -> Unit,
    onTodoEdit: (Todo) -> Unit,
    modifier: Modifier = Modifier
) {
    // 到点即逾期并标红——与板块归属同一个判定函数。
    val overdue = Format.isOverdue(todo.dueAtMillis, today, now, todo.hasExactTime)
    TodoRow(
        todo,
        overdue = overdue,
        checked = checked,
        onCircleTap = { onCircleTap(todo) },
        onSwipeArchive = { onSwipeArchive(todo) },
        onTodoEdit = { onTodoEdit(todo) },
        modifier = modifier
    )
}

/** Board title (逾期 / 有日期 / 无日期) — one step stronger than a bucket title. */
@Composable
private fun BoardHeader(board: HomeBoard, modifier: Modifier = Modifier) {
    Text(
        board.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
    )
}

/** Bucket title (今日 / 明天 / …) inside the 有日期 board. */
@Composable
private fun BucketHeader(bucket: HomeBucket, modifier: Modifier = Modifier) {
    Text(
        bucket.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        // 20dp 与板块标题对齐：「有日期」不显示标题后，档位就是这一层的最高标题。
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun ArchivedList(
    archived: List<Todo>,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    listState: LazyListState,
    topInset: Dp = 0.dp
) {
    val empty = archived.isEmpty()
    AnimatedVisibility(
        visible = empty,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(120)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有已归档的待办", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!empty) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + TOP_BAR_BODY_HEIGHT + 8.dp,
                bottom = 8.dp
            )
        ) {
            items(archived, key = { it.id }) { todo ->
                // Same tinted card as an active todo, so the two lists look consistent.
                Row(
                    modifier = Modifier
                        .animateItem()
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
    immersiveTopBar: Boolean,
    onSetImmersiveTopBar: (Boolean) -> Unit,
    onRescheduleReminders: () -> Unit,
    onExactAlarmSettings: () -> Unit,
    onTestNotification: () -> Unit,
    onTestTodo: () -> Unit,
    listState: LazyListState,
    topInset: Dp = 0.dp
) {
    val context = LocalContext.current
    // Used to keep at least one board visible.
    val visibleBoardCount = boardOrder.count { it !in hiddenBoards }

    // 「通知标题」的编辑草稿。三个关键点：
    // 1. 放在 LazyColumn 外面 —— item 被滚动回收时输入框内容不会丢；
    // 2. draft 是**唯一**的编辑态来源，预览也读它。v0.3 是"输入框读 state、预览读
    //    SharedPreferences"两个来源，删了 emoji 之后两边不一致的隐患就在这里；
    // 3. 保存是显式动作（一个按钮管整张卡），不再每敲一个字就写盘。
    // 首次进入设置页读一次；之后由 draft / saved 两套状态自己维护。
    // 不要每次重组都读 SP（输入模板时每个按键都会重组一次）。
    val initialStyle = remember { SettingsRepo.notificationStyle(context) }
    var notifTemplate by remember { mutableStateOf(initialStyle.titleTemplate) }
    var notifDone by remember { mutableStateOf(initialStyle.done) }
    var savedTemplate by remember { mutableStateOf(initialStyle.titleTemplate) }
    var savedDone by remember { mutableStateOf(initialStyle.done) }
    // DUE 状态语（"该办了"）不再单独暴露成设置项，但仍参与预览，让用户看到 {提醒} 展开成什么。
    val notifDueWord = initialStyle.dueWord

    // 「提醒文案」草稿。同样放在 LazyColumn 外面 —— item 被回收时输入框内容不会丢。
    // 文案条数多（默认 19 条），所以默认收起、点开才编辑，不在设置页里铺一屏输入框。
    val builtinQuotesText = remember { Quotes.defaults.joinToString("\n") }
    val initialQuotesText = remember {
        (SettingsRepo.customQuotes(context) ?: Quotes.defaults).joinToString("\n")
    }
    var quotesText by remember { mutableStateOf(initialQuotesText) }
    var savedQuotesText by remember { mutableStateOf(initialQuotesText) }
    var quotesExpanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topInset + TOP_BAR_BODY_HEIGHT + 8.dp,
            bottom = 8.dp
        )
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

        item { SectionHeader("通知标题") }
        item {
            SettingsCard {
                NotificationTitleEditor(
                    template = notifTemplate,
                    done = notifDone,
                    dueWord = notifDueWord,
                    dirty = notifTemplate != savedTemplate || notifDone != savedDone,
                    onTemplateChange = { notifTemplate = it },
                    onDoneChange = { notifDone = it },
                    onSave = {
                        // 一个按钮写两个字段：要么一起成功，要么一起不动。
                        SettingsRepo.saveNotificationTitle(context, notifTemplate, notifDone)
                        savedTemplate = notifTemplate
                        savedDone = notifDone
                        Toast.makeText(context, "通知标题已保存", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        item { SectionHeader("提醒文案") }
        item {
            SettingsCard {
                QuotesEditor(
                    text = quotesText,
                    expanded = quotesExpanded,
                    dirty = quotesText != savedQuotesText,
                    count = quotesText.lineSequence().count { it.isNotBlank() },
                    onToggle = { quotesExpanded = !quotesExpanded },
                    onTextChange = { quotesText = it },
                    onRestoreDefault = { quotesText = builtinQuotesText },
                    onSave = {
                        val normalized = quotesText.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .toList()
                        SettingsRepo.setCustomQuotes(context, normalized)
                        // 清空 = 恢复默认（SettingsRepo 会删掉键）。界面上也切回默认池，
                        // 否则"我清空了但通知里还有文案"看起来像没保存。
                        val applied = if (normalized.isEmpty()) builtinQuotesText
                        else normalized.joinToString("\n")
                        quotesText = applied
                        savedQuotesText = applied
                        Toast.makeText(
                            context,
                            if (normalized.isEmpty()) "已恢复默认文案" else "文案已保存（${normalized.size} 条）",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // v0.5：原来这里只有一行「通知与精确闹钟」的门牌号，用户点进去还是不知道要开什么。
        // 现在拆成四条可自检的状态行 —— 每一条都能回答"我现在到底缺什么"。
        item { SectionHeader("提醒可靠性") }
        item { ReliabilitySection(onExactAlarmSettings = onExactAlarmSettings) }
        if (!canScheduleExact(context)) {
            item {
                Text(
                    "未获得精确闹钟权限，提醒可能被系统延后几分钟。请在上方开启，并允许后台运行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        item { SectionHeader("后台常驻") }
        item { KeepAliveSection() }

        item { SectionHeader("测试") }
        item {
            SettingsCard {
                ListItem(
                    headlineContent = { Text("发送测试通知") },
                    // 文案统一成「动作 + 验证什么」：之前"弹一条带「完成」按钮的通知，验证权限、
                    // 渠道与动作"偏术语，普通用户读不出"我现在该看什么"。
                    supportingContent = { Text("立即弹一条提醒，确认通知能收到、「完成」按钮能点") },
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
                    supportingContent = { Text("建一条一分钟后到期的待办，确认提醒准时响") },
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
                ItemDivider()
                ToggleItem(
                    "沉浸式顶栏",
                    "向下滚动时收起标题栏，向上滚动时回来",
                    immersiveTopBar,
                    onSetImmersiveTopBar
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
                            "长按左侧三条杠上下拖动，右侧开关控制是否显示",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ItemDivider()
                ReorderableBoardList(
                    order = boardOrder,
                    hiddenBoards = hiddenBoards,
                    canToggle = { board ->
                        // 不允许把最后一个可见板块也关掉：首页会变空白，且没有入口回来。
                        board in hiddenBoards || visibleBoardCount > 1
                    },
                    onReorder = onSetBoardOrder,
                    onVisibleChange = { board, on ->
                        onSetHiddenBoards(
                            if (on) hiddenBoards - board else hiddenBoards + board
                        )
                    }
                )
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
                // 打开开关后"提醒间隔"不该让整张卡片突然长高一截
                AnimatedVisibility(
                    visible = overdueNag,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
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
        // 「关于」v0.4 起是独立的第四个导航项，设置页不再保留入口 ——
        // 两个入口指向同一页，只会让"关于到底在哪"变成一个问题。
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

/** 标题模板最多 24 个 code point —— 一个 emoji 约 1.2 个汉字宽，再多会把事件名挤没。 */
private const val TITLE_MAX_CODE_POINTS = 24

/** 完成后后缀最多 4 个 code point（与旧版"最多各两个 emoji"的取舍一致）。 */
private const val DONE_MAX_CODE_POINTS = 4

/**
 * 「通知标题」编辑区：一个区域、一个保存按钮，管「标题模板 + 完成后后缀」。
 *
 * 版式上刻意只留**两个**输入框。v0.3 是「提醒语 / 前缀装饰 / 后缀装饰 / 完成后装饰」四个，
 * 用户改一个 emoji 要动四行、还得自己脑补拼出来是什么样；现在输入什么、通知就是什么。
 *
 * `{提醒}` 是占位符不是固定文字：通知标题里的状态语随提醒类型变化，模板写死「该办了」
 * 会让"提前提醒"也说"该办了"。删掉占位符也不报错，那时四种提醒共用同一句固定标题。
 */
@Composable
private fun NotificationTitleEditor(
    template: String,
    done: String,
    dueWord: String,
    dirty: Boolean,
    onTemplateChange: (String) -> Unit,
    onDoneChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val draftStyle = NotificationStyle(titleTemplate = template, done = done, dueWord = dueWord)
    val fieldShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.fieldBorder,
        focusedContainerColor = cs.fieldSurface,
        unfocusedContainerColor = cs.fieldSurface
    )

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("标题模板", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Text(
            "接在事件名后面。{提醒} 会换成当前提醒的状态语",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        OutlinedTextField(
            value = template,
            onValueChange = { next ->
                // 按 code point 限长：emoji 是代理对，用 String.length 会把一个 emoji 劈成半个。
                // 超限整段拒绝（而不是 take(n)）—— 后者必然劈开代理对。
                if (next.codePointCount(0, next.length) <= TITLE_MAX_CODE_POINTS) {
                    onTemplateChange(next)
                }
            },
            singleLine = false,
            maxLines = 2,
            shape = fieldShape,
            colors = fieldColors,
            placeholder = {
                Text(
                    SettingsRepo.DEFAULT_NOTIF_TEMPLATE,
                    color = cs.placeholderText
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        // 快捷插入：用户删掉 {提醒} 之后还能一键放回来，不用记语法。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text(
                "插入占位符",
                style = MaterialTheme.typography.bodySmall,
                color = cs.placeholderText
            )
            TextButton(
                onClick = {
                    val next = template + SettingsRepo.NOTIF_REMINDER_TOKEN
                    if (next.codePointCount(0, next.length) <= TITLE_MAX_CODE_POINTS) {
                        onTemplateChange(next)
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    SettingsRepo.NOTIF_REMINDER_TOKEN,
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary
                )
            }
        }

        ItemDivider()
        Text(
            "完成后后缀",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "勾选完成后，标题变成「事件 + 这个」",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        OutlinedTextField(
            value = done,
            onValueChange = { next ->
                if (next.codePointCount(0, next.length) <= DONE_MAX_CODE_POINTS) onDoneChange(next)
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            placeholder = { Text(SettingsRepo.DEFAULT_NOTIF_DONE, color = cs.placeholderText) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        // ── 预览 ──
        // 三种提醒各来一行：只在"到点"这一行预览的话，用户根本看不出 {提醒}
        // 在提前提醒里会变成什么。
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cs.cardPlaceholder,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "预览",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary
                )
                PreviewLine(
                    "到点",
                    NotificationHelper.composeTitle("开组会", dueWord, false, draftStyle)
                )
                PreviewLine(
                    "提前提醒",
                    NotificationHelper.composeTitle("开组会", "提前提醒", false, draftStyle)
                )
                PreviewLine(
                    "已完成",
                    NotificationHelper.composeTitle("开组会", dueWord, true, draftStyle)
                )
                Text(
                    // 固定取第一条，别用 Quotes.random(context)：每重组一次就换一句。
                    // 现在文案池可编辑，所以这里读的是"当前生效池"的第一条。
                    Quotes.pool(context).firstOrNull() ?: Quotes.FALLBACK,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Button(
            onClick = onSave,
            enabled = dirty,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.actionContainer,
                contentColor = cs.actionContent,
                disabledContainerColor = cs.actionDisabledContainer,
                disabledContentColor = cs.actionDisabledContent
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp)
        ) {
            Text(if (dirty) "保存通知标题" else "已保存")
        }
        if (dirty) {
            Text(
                // 说清楚两件事：没保存时通知用的是什么；以及切走会丢草稿
                // （draft 是 remember 的，切 tab 会离开组合）。
                "有改动尚未保存，通知仍用上一次保存的标题；切到别的页会丢失这次修改。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.placeholderText,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** 预览里的一行：左边类型标签，右边实际标题。 */
@Composable
private fun PreviewLine(label: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.width(56.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 「提醒文案」编辑器：**默认收起**，点开才出现多行输入区。
 *
 * 为什么收起：默认池有 19 条，直接铺开会占掉大半屏设置页，用户每次进来都要滚过去。
 * 收起时只显示一行摘要（条数），要改再点开。
 *
 * 为什么是「一个多行输入框」而不是「19 行可删除的列表」：
 * - 19 行 × 删除按钮 = 一屏放不下，还得再套一层 LazyColumn（嵌套滚动，手感很差）；
 * - 多行框可以直接全选、清空、粘贴一整批，改起来比逐条删快得多；
 * - 每行一条的规则一眼就懂，不需要额外的"添加"按钮。
 */
@Composable
private fun QuotesEditor(
    text: String,
    expanded: Boolean,
    dirty: Boolean,
    count: Int,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onRestoreDefault: () -> Unit,
    onSave: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "quotesArrow"
    )
    val fieldShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.fieldBorder,
        focusedContainerColor = cs.fieldSurface,
        unfocusedContainerColor = cs.fieldSurface
    )
    // ★ 吃掉输入框滚完之后剩下的垂直滚动。
    // 多行 TextField 内部是 verticalScroll：滚到顶/底之后，剩余量会沿嵌套滚动链
    // 传给外层的设置页 LazyColumn，于是"在框里滑到底、整个设置页跟着动"。
    // onPostScroll 里把 available 全部消费掉，手指在框内滑动就只滚框内文字。
    val scrollBlocker = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (available.y != 0f) Offset(0f, available.y) else Offset.Zero
        }
    }

    Column {
        ListItem(
            headlineContent = { Text("提醒文案", style = MaterialTheme.typography.bodyLarge) },
            supportingContent = {
                Text(
                    if (expanded) "每行一条，共 $count 条" else "通知正文那一行，共 $count 条 · 点开可编辑",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrow }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onToggle)
        )
        // AnimatedVisibility 而不是 animateContentSize：后者不裁子内容，
        // 展开那 200ms 里内容会先按最终高度画出来（"内容先蹦出来、容器再长高"）。
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    "通知正文就取其中一条；同一条待办始终用同一句，点「完成 / 撤回」不会跳。"
                        + "清空后保存会恢复默认文案。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    minLines = 6,
                    maxLines = 10,
                    shape = fieldShape,
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        // 框内滚到底/顶之后不再带动外层设置页（见 scrollBlocker 注释）。
                        .nestedScroll(scrollBlocker)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$count 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.placeholderText
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onRestoreDefault,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "恢复默认",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.secondaryActionContent
                        )
                    }
                    Button(
                        onClick = onSave,
                        enabled = dirty,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.actionContainer,
                            contentColor = cs.actionContent,
                            disabledContainerColor = cs.actionDisabledContainer,
                            disabledContentColor = cs.actionDisabledContent
                        ),
                        modifier = Modifier.padding(start = 8.dp).height(40.dp)
                    ) {
                        Text(if (dirty) "保存" else "已保存")
                    }
                }
            }
        }
    }
}

private val BOARD_ROW_HEIGHT = 56.dp

/**
 * 板块排序列表：长按左侧三条杠（整行也可长按）拖动，松手才提交。
 *
 * Compose 没有内置"非 lazy 列表的拖拽排序"，这里用固定行高 + `graphicsLayer` 自己算位置：
 * - 拖动中的行跟手（`translationY = 基准 + dragDelta`，**基准不做插值**）；
 * - 其它行靠 `Animatable` + `spring` 平滑滑到新槽位；
 * - **所有位移都在 layer 层读 state**，拖动全程不重组（见下方 ★ 注释）；
 * - 交换后同步扣减 [dragDelta]，让手指和行保持贴合。
 *
 * ★ v0.4 修的抖动：旧代码对**所有**行（包括正在拖的那一行）都跑
 *   `baseY.animateTo(index * rowHeight)`。交换发生的瞬间，被拖行的 index 变了，
 *   于是 baseY 开始 spring 到新槽位，而 dragDelta 又刚被扣掉了整整一行 ——
 *   同一段位移算了两遍：动画期间行会脱离手指、随后再"追上来"，看起来就是抖一下。
 *   现在拖拽中一律 `snapTo`（位移只由 dragDelta 表达），只有**其它**行才播动画。
 *
 * 行高固定 56dp 是有意的：只有三行、单行文字，换来的是可以精确计算落点。
 */
@Composable
private fun ReorderableBoardList(
    order: List<HomeBoard>,
    hiddenBoards: Set<HomeBoard>,
    canToggle: (HomeBoard) -> Boolean,
    onReorder: (List<HomeBoard>) -> Unit,
    onVisibleChange: (HomeBoard, Boolean) -> Unit
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { BOARD_ROW_HEIGHT.toPx() }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val rowShape = remember { RoundedCornerShape(14.dp) }
    val draggingColor = MaterialTheme.colorScheme.cardDragging

    // Held in a State object rather than a plain value: the pointerInput block is only rebuilt
    // when its key changes, so a captured `order` would go stale after the first swap.
    val orderState = remember(order) { mutableStateOf(order) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    // 拖动开始时的顺序，松手时用它判断"到底有没有动过"——
    // 没动过就不提交，省掉一次写盘 + 父层重组（那会让整张设置页重画一遍）。
    var orderAtDragStart by remember { mutableStateOf<List<HomeBoard>>(emptyList()) }
    val items = orderState.value

    Box(Modifier.fillMaxWidth().height(BOARD_ROW_HEIGHT * items.size)) {
        items.forEachIndexed { index, board ->
            key(board) {
                val dragging = draggingId == board.id
                // 基准位置用 Animatable：被挤开时平滑滑到新槽位。它的值只在 graphicsLayer
                // 里读，所以动画每帧只改 RenderNode，不触发重组。
                // 被挤开的行的归位动画。
                //
                // ★ 必须是 Animatable 而不是 animateFloatAsState：拖拽中的行要"立刻"
                //   等于槽位值（见下面 snapTo），这个语义 animateFloatAsState 给不了 ——
                //   它只能在目标变化后慢慢追过去，松手那一刻如果还在半路，行就会跳一下。
                //
                // 动画本身用**临界阻尼** spring：dampingRatio < 1 的欠阻尼会"滑过头再弹回来"，
                // 快速连续交换时每一行都在过冲，那就是肉眼看到的抖。刚度 1500 让位移在
                // ~100ms 内到位，跟手但不拖沓。
                val baseY = remember { Animatable(index * rowHeightPx) }
                LaunchedEffect(index, dragging) {
                    val target = index * rowHeightPx
                    if (dragging) {
                        baseY.snapTo(target)
                    } else {
                        baseY.animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 1500f
                            )
                        )
                    }
                }
                // 阴影 0 -> 10dp 硬切是"啪"的一下，和底色突变叠在一起像闪。
                val shadow by animateFloatAsState(
                    targetValue = if (dragging) 10f else 0f,
                    animationSpec = tween(durationMillis = 140),
                    label = "boardDragShadow"
                )
                // 拖拽底色同样走过渡：透明 <-> cardDragging 直接切换会"闪一块色"。
                val tint by animateColorAsState(
                    targetValue = if (dragging) draggingColor else Color.Transparent,
                    animationSpec = tween(durationMillis = 140),
                    label = "boardDragTint"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(BOARD_ROW_HEIGHT)
                        .zIndex(if (dragging) 1f else 0f)
                        // ★ 性能关键：位移走 graphicsLayer（deferred read）。
                        // 之前用 animateFloatAsState 在组合期读 y，拖动时每帧都重组整个
                        // 列表——这就是"卡卡的"的根源。现在 translationY 只是 RenderNode
                        // 属性，拖动全程零重组，只有拖动开始/结束各重组一次（改 zIndex）。
                        .graphicsLayer {
                            val draggingNow = draggingId == board.id
                            // 拖拽中：位置 = 槽位 + 手指位移（槽位不插值，位移只由 dragDelta 表达）。
                            // 其它行：读 baseY 这个 State（deferred read，不重组）。
                            translationY =
                                if (draggingNow) index * rowHeightPx + dragDelta else baseY.value
                            shadowElevation = shadow.dp.toPx()
                            shape = rowShape
                            clip = false
                        }
                        // 拖拽态必须是同一个色相再抬/压一档，不能是中性灰 ——
                        // 旧代码用 surfaceContainerHigh，拖动瞬间卡片"褪色"，很突兀。
                        .background(tint, rowShape)
                        .drawBehind {
                            if (!dragging && index < items.lastIndex) {
                                val stroke = 0.5.dp.toPx()
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(16.dp.toPx(), size.height - stroke),
                                    end = Offset(size.width - 16.dp.toPx(), size.height - stroke),
                                    strokeWidth = stroke
                                )
                            }
                        }
                        .pointerInput(board.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = board.id
                                    dragDelta = 0f
                                    orderAtDragStart = orderState.value
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val current = orderState.value.indexOf(board)
                                    // ★ 边界 clamp。不夹住的话，拖到顶/底之后继续拖，
                                    // dragDelta 会一路累加，卡片画出容器外（clip = false），
                                    // 松手时又瞬间回到槽位 —— 这就是"松手后突然跳一下"。
                                    val minDelta = -current * rowHeightPx
                                    val maxDelta = (orderState.value.lastIndex - current) * rowHeightPx
                                    dragDelta = (dragDelta + amount.y).coerceIn(minDelta, maxDelta)

                                    val steps = (dragDelta / rowHeightPx).roundToInt()
                                    val target = current + steps
                                    if (steps != 0 && target in orderState.value.indices) {
                                        orderState.value = orderState.value.toMutableList().also {
                                            it.removeAt(current)
                                            it.add(target, board)
                                        }
                                        // 行已挪到新槽位，基准 y 变了 (target-current) 行，
                                        // 这段要从 dragDelta 里扣掉，否则下一次拖动会跳。
                                        dragDelta -= (target - current) * rowHeightPx
                                        // 槽位变了，边界也跟着变，再夹一次。
                                        val newMin = -target * rowHeightPx
                                        val newMax =
                                            (orderState.value.lastIndex - target) * rowHeightPx
                                        dragDelta = dragDelta.coerceIn(newMin, newMax)
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragDelta = 0f
                                    // 只在顺序真的变了时才提交：避免每次长按都写一次盘。
                                    if (orderState.value != orderAtDragStart) {
                                        onReorder(orderState.value)
                                    }
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragDelta = 0f
                                    // 取消 = 回到拖拽前的顺序，别把半途状态留在界面上。
                                    orderState.value = orderAtDragStart
                                }
                            )
                        }
                ) {
                    BoardRowContent(
                        board = board,
                        visible = board !in hiddenBoards,
                        canToggle = canToggle(board),
                        onVisibleChange = { on -> onVisibleChange(board, on) }
                    )
                }
            }
        }
    }
}

/** One board row: drag handle · label · visibility switch. */
@Composable
private fun BoardRowContent(
    board: HomeBoard,
    visible: Boolean,
    canToggle: Boolean,
    onVisibleChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Menu,
            contentDescription = "长按拖动排序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp).size(20.dp)
        )
        Text(
            board.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = visible, onCheckedChange = onVisibleChange, enabled = canToggle)
    }
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

/**
 * 右滑多少像素触发归档。96dp 大约是屏宽的四分之一，够明确又不至于滑不动。
 *
 * ★ 只按**累计位移**判定，不加"甩动速度"：速度没有方向绑定，斜着快速滑动列表时
 *   横向分量也能过阈值 —— 实测出现过"向左下滑动也归档"。归档是不可逆入口，
 *   宁可要求用户滑够距离，也不要误触发。
 */
private val SWIPE_ARCHIVE_THRESHOLD = 96.dp

/** 右滑时卡片最多跟手多远（再远就只给视觉反馈，不无限拖）。 */
private val SWIPE_MAX_OFFSET = 160.dp

/**
 * 一条待办。
 *
 * 两个状态都**不归它自己管**：
 * - `checked`（正在归档）来自 `MainViewModel.pendingArchive`，不是 `remember`。
 *   这是 v0.3"点圆圈后快速滑动，归档丢失"的根因修复：旧代码把 `delay(650)` 挂在
 *   行自己的 `rememberCoroutineScope()` 上，行被 LazyColumn 回收时协程一并取消，
 *   归档调用永远不执行；行再滑回来时 `remember` 也已重置，勾选态"闪回"成未勾选。
 * - 右滑的 offset 只在回弹动画里用，不做任何持久化。
 */
@Composable
private fun TodoRow(
    todo: Todo,
    overdue: Boolean,
    checked: Boolean,
    onCircleTap: () -> Unit,
    onSwipeArchive: () -> Unit,
    onTodoEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 填充弧线时长必须和 ViewModel 里的 delay 同值，否则会出现"还没填满就消失"
    // 或"填满了干等一会儿"。
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = CIRCLE_FILL_MILLIS.toInt(), easing = FastOutSlowInEasing)
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 0.92f else 1f,
        animationSpec = tween(durationMillis = CIRCLE_FILL_MILLIS.toInt(), easing = FastOutSlowInEasing)
    )

    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_ARCHIVE_THRESHOLD.toPx() }
    val maxOffsetPx = with(density) { SWIPE_MAX_OFFSET.toPx() }
    val resistPx = with(density) { 12.dp.toPx() }
    // ★ 用普通 float state，**不能用 Animatable + scope.launch { snapTo }**。
    // 那样每次拖动都要投递一个协程，协程调度有延迟：
    //   - 连续快速滑动时，`offsetX.value` 还是上一帧的旧值，`+ dragAmount` 等于把前一次的
    //     位移丢掉，卡片比手指慢一截（"必须慢慢滑才生效"就是这么来的）；
    //   - 松手瞬间 `onDragEnd` 读到的也可能是旧值，直接判定没到阈值。
    // 普通 state 是同步写入，读到的一定是最新值。
    var dragX by remember(todo.id) { mutableFloatStateOf(0f) }
    // 累计位移（**不 clamp**），作为归档判定的唯一依据。
    // 用 dragX 判定会有歧义：它被 coerceIn 夹过、还可能被回弹动画写过。
    var totalDx by remember(todo.id) { mutableFloatStateOf(0f) }
    // 回弹只负责"把 dragX 推回 0"，不承载任何判定逻辑。
    // 用 Job 引用是为了让步回弹中途再来一次拖动时能被打断 ——
    // 否则旧动画会继续写 dragX，手指和卡片互相拉扯。
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val settleScope = rememberCoroutineScope()

    fun settleBack() {
        settleJob.value?.cancel()
        settleJob.value = settleScope.launch {
            animate(
                initialValue = dragX,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f)
            ) { value, _ -> dragX = value }
        }
    }

    val cs = MaterialTheme.colorScheme
    val cardColor = cs.cardBackground
    val hintContainer = cs.secondaryContainer
    val hintContent = cs.onSecondaryContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        // 底层：右滑时露出的「归档」提示。alpha 跟着滑动进度走，在 graphicsLayer 里读
        // dragX 属于"延迟读取"——每帧只改图层属性，不重组。
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(hintContainer)
                .graphicsLayer {
                    val progressToThreshold = (dragX / thresholdPx).coerceIn(0f, 1f)
                    alpha = progressToThreshold
                    scaleX = 0.94f + 0.06f * progressToThreshold
                    scaleY = 1f
                }
                .padding(start = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = hintContent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "归档",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = hintContent,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragX
                    if (checked) {
                        scaleX = scale
                        scaleY = scale
                    }
                }
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                // 右滑：水平方向独立抢占，垂直滚动仍然是 LazyColumn 的
                // （detectHorizontalDragGestures 只在水平超过 touch slop 后消费事件）。
                .pointerInput(todo.id, checked) {
                    if (checked) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 打断还在跑的回弹，否则动画会和手指抢 dragX。
                            settleJob.value?.cancel()
                            totalDx = 0f
                        },
                        onDragEnd = {
                            // 只看向右累计位移：向左的拖拽 totalDx 为负，永远不会触发。
                            if (totalDx >= thresholdPx) onSwipeArchive()
                            settleBack()
                        },
                        onDragCancel = { settleBack() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // 同步累加（不能用协程，否则快滑会丢位移）。
                            totalDx += dragAmount
                            dragX = (dragX + dragAmount).coerceIn(-resistPx, maxOffsetPx)
                        }
                    )
                }
                .combinedClickable(onClick = {}, onLongClick = onTodoEdit)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckCircle(
                progress = progress,
                overdue = overdue,
                onClick = { if (!checked) onCircleTap() },
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
                        checked -> cs.outline
                        overdue -> cs.error
                        else -> cs.onSurface
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
                        color = if (overdue) cs.error.copy(alpha = 0.85f) else cs.onSurfaceVariant
                    )
                }
                // Weekday + time stay here, under the title.
                todo.dueAtMillis?.let { due ->
                    Text(
                        Format.weekdayTime(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) cs.error else cs.onSurfaceVariant
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
                        color = if (overdue) cs.error else cs.onSurfaceVariant
                    )
                }
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

    // 触摸反馈必须裁成圆，颜色必须跟填充弧线一致：
    // clickable 的 ripple 是「有界」的，边界就是节点本身 —— 这里是 26dp 的**方形**，
    // 而里面画的圆只有 22dp，不裁的话按下时是一个套在圆外面的灰色方块（视觉上像块脏底）。
    // 染色成 fillColor 是为了让涟漪和随后的 600ms 填充是同一个颜色，
    // 读起来是"填充从按下处开始"，而不是"多了一下闪光"。
    // 注意 remember：ripple() 每次调用都返回新实例，不缓存会把涟漪状态重建一遍。
    val indication = remember(fillColor) { ripple(color = fillColor) }

    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                onClick = onClick
            ),
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
// ─────────────────────────────────────────────────────────────────────────────
// 设置页：提醒可靠性 / 后台常驻（v0.5）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 「提醒可靠性」—— 设置页里唯一需要读**系统状态**的一节。
 *
 * 四条状态行，每条都回答一个具体问题：
 *   1. 通知权限有吗？（没有 = 提醒发出来也看不见）
 *   2. 精确闹钟有吗？（没有 = 系统可以把提醒延后几分钟到几小时）
 *   3. 在电池优化白名单里吗？（不在 = Doze 下闹钟被节流）
 *   4. 自启动 / 后台高耗电放行了吗？（厂商私有开关，**无法检测**，只能给入口）
 *
 * ★ 第 4 条的状态文案故意写成"需确认"而不是"未开启"：那是厂商在系统设置里加的
 *   非 AOSP 开关，没有任何标准 API 能读到它的值。假装知道答案只会骗用户。
 *
 * 刷新机制：用户去系统设置改完回来，`ON_RESUME` 自增 tick，三个 `remember(tick)` 重读。
 * 不用"每帧读一次 PowerManager"—— 那会让整页在滚动时反复查系统服务。
 */
@Composable
private fun ReliabilitySection(onExactAlarmSettings: () -> Unit) {
    val context = LocalContext.current

    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsOk = remember(tick) { DeviceCompat.canPostNotifications(context) }
    val exactOk = remember(tick) { DeviceCompat.canScheduleExactAlarms(context) }
    val vendorHint = remember { DeviceCompat.vendorHint() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        tick++
        if (!granted) {
            Toast.makeText(
                context, "没有通知权限，提醒弹不出来（可稍后在系统设置里开启）", Toast.LENGTH_LONG
            ).show()
        }
    }

    SettingsCard {
        ReliabilityRow(
            title = "允许通知",
            sub = "提醒是通过通知发出的，关掉就什么都看不到",
            ok = notificationsOk,
            okText = "已允许",
            actionText = "去开启",
            onClick = {
                if (!notificationsOk && Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    DeviceCompat.openAppDetail(context)
                }
            }
        )
        ItemDivider()
        ReliabilityRow(
            title = "精确闹钟",
            sub = "没有它，系统可以把提醒延后几分钟到几小时",
            ok = exactOk,
            okText = "已允许",
            actionText = "去开启",
            onClick = onExactAlarmSettings
        )
        ItemDivider()
        ReliabilityRow(
            title = "系统电池优化",
            // ★ 必须说清"这不是厂商的那个开关"：vivo/OPPO/小米 设置里的「后台高耗电」
            //   是厂商私有开关，与 AOSP 的"忽略电池优化"白名单是**两个独立的东西**。
            sub = "系统的「忽略电池优化」白名单。厂商设置里的「后台高耗电 / 允许后台活动」是另一个开关，见下一条",
            // 不显示"已加入"这类状态：点了就去对应的系统页面，状态让系统自己展示。
            ok = null,
            actionText = "前往",
            onClick = {
                val opened = DeviceCompat.openBatteryOptimizationSettings(context)
                if (!opened) {
                    Toast.makeText(context, "没有找到电池优化页面，已打开应用详情", Toast.LENGTH_LONG).show()
                }
                tick++
            }
        )
        ItemDivider()
        ReliabilityRow(
            title = "自启动 / 后台运行",
            sub = vendorHint,
            // 厂商开关读不到，所以不做状态判断 —— 只负责把用户送到该去的地方。
            ok = null,
            actionText = "前往",
            onClick = {
                val jumped = DeviceCompat.openAutoStartSettings(context)
                if (!jumped) {
                    Toast.makeText(
                        context, "已打开应用详情，请在那里找到「自启动 / 后台运行」", Toast.LENGTH_LONG
                    ).show()
                }
                tick++
            }
        )
    }
}

/**
 * 一条「说明 + 跳转」行。
 *
 * @param ok 三态，别用 Boolean 硬凑：
 *   - `true`  → 显示 [okText]（能检测、且已放行）
 *   - `false` → 显示 [actionText]（能检测、但没放行 → 标红）
 *   - `null`  → 只显示 [actionText]（**检测不到**，如厂商自启动；不表态，只负责跳转）
 *
 *   这个三态是刻意的：厂商开关读不到，如果硬显示"已确认/已加入"，用户一旦去系统里
 *   发现实际没开，就会认定这个页面在骗人。
 */
@Composable
private fun ReliabilityRow(
    title: String,
    sub: String,
    ok: Boolean?,
    okText: String = "已允许",
    actionText: String = "前往",
    onClick: () -> Unit
) {
    val statusText = if (ok == true) okText else actionText
    val statusColor = when (ok) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * 「后台常驻」—— 唯一一个会改变进程行为的开关，所以文案必须把代价讲清楚。
 *
 * 三句话缺一不可：
 *   1. 它做了什么（进程更晚被回收，通知栏多一条常驻通知）；
 *   2. 它**不**做什么（挡不住"强行停止"和一键清理）；
 *   3. 不做它行不行（提醒由系统闹钟触发，本来就不依赖进程）。
 *
 * 第 3 句是这一节存在的意义：绝大多数用户不需要打开它。
 */
@Composable
private fun KeepAliveSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsRepo.keepAliveEnabled(context)) }

    SettingsCard {
        ToggleItem(
            label = "后台常驻",
            sub = "会显示一条常驻通知；让通知栏操作响应更快",
            checked = enabled,
            onChecked = { on ->
                val ok = KeepAlive.setEnabled(context, on)
                enabled = ok
                if (on && !ok) {
                    Toast.makeText(context, "系统拒绝了后台常驻（可稍后重试）", Toast.LENGTH_LONG).show()
                }
            }
        )
        Text(
            "提醒本身由系统闹钟触发：应用被划掉、被系统回收都不会漏；" +
                "只有「强行停止」或一键清理会清掉闹钟，下次打开应用会自动恢复。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)
        )
    }
}
