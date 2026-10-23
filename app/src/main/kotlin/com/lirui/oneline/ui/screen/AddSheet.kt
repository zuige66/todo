package com.lirui.oneline.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lirui.oneline.parser.ParsedTodo
import com.lirui.oneline.ui.theme.actionContainer
import com.lirui.oneline.ui.theme.actionContent
import com.lirui.oneline.ui.theme.actionDisabledContainer
import com.lirui.oneline.ui.theme.actionDisabledContent
import com.lirui.oneline.ui.theme.cardBackground
import com.lirui.oneline.ui.theme.fieldBorder
import com.lirui.oneline.ui.theme.fieldSurface
import com.lirui.oneline.ui.theme.placeholderText
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.Format

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    /** 提交合并后的解析结果（含手动选的日期/时间覆盖）。 */
    onAdd: (List<ParsedTodo>, Boolean) -> Unit,
    onPreview: (String) -> Unit,
    parses: List<ParsedTodo>,
    initialText: String = "",
    editing: Boolean = false,
    initialRepeatDays: Int = 0
) {
    // skipPartiallyExpanded = true：弹层**一次就完全展开**。
    //
    // 默认（false）会先停在 half-expanded —— 高度按**整块窗口**的一半算。而 M3 的弹层窗口
    // 在 SDK 30+ 用的是 SOFT_INPUT_ADJUST_NOTHING（窗口本身不随键盘缩小），键盘弹起时
    // 窗口只在外层 Box 上叠了 imePadding 把 sheet 整体顶上去，半屏高度仍按老窗口算 →
    // 结果就是 sheet 只剩键盘上方一条窄缝，输入框正好被压在键盘边缘（真机盲打）。
    // 全展开后 sheet 高度直接用「窗口高度 - 键盘」，输入框一定在键盘之上。
    // Plain remember (not rememberSaveable): the sheet leaves composition on dismiss, and a
    // saveable state would resurrect the previous sentence on the next open.
    var text by remember(initialText) { mutableStateOf(initialText) }
    // Only meaningful while editing an existing repeating todo: lets the user turn the
    // "每天/每周/每月" plan off without rewriting the sentence.
    var repeatOn by remember(initialRepeatDays) { mutableStateOf(initialRepeatDays > 0) }

    // ── 手动覆盖（v0.7）──
    // 预览卡片的日期/时间 chip 可以直接点，弹出系统选择器改；标题与详情同理（v0.7.1 起）。
    // 规则（zuige 拍板）：
    //   1. 手动选的**优先于**句子解析值；
    //   2. 没有"清除时间"—— 时间一旦设了就在（可改不可清）；日期可以改成「无日期」；
    //   3. 多日期待办**每条独立**覆盖（"明天，后天 开会" → 两个 chip 组各点各的）；
    //   4. 编辑模式初始显示的就是该待办当前的日期时间（previewExisting 已反推）。
    //
    // 键 = 预览项下标。dateOverrides 的**键存在**即"用户干预过"，值为 null 表示改成无日期；
    // timeOverrides 只有"设过"才会进表（不可清）；title/detail 的 override 键存在即干预过。
    var dateOverrides by remember { mutableStateOf(mapOf<Int, java.time.LocalDate?>()) }
    // 时间段的开始日覆盖（v0.8.4）：键存在即"用户干预过"；值为 null 表示取消开始日（退回单日）。
    var startDateOverrides by remember { mutableStateOf(mapOf<Int, java.time.LocalDate?>()) }
    var timeOverrides by remember { mutableStateOf(mapOf<Int, java.time.LocalTime>()) }
    var titleOverrides by remember { mutableStateOf(mapOf<Int, String>()) }
    var detailOverrides by remember { mutableStateOf(mapOf<Int, String?>()) }
    var pickingIndex by remember { mutableIntStateOf(-1) }
    var showDatePicker by remember { mutableStateOf(false) }
    // 时间段（有开始日）走"起止同屏"的区间选择器，不再分两个日历（v0.8.9）。
    var showRangePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTitleEditor by remember { mutableStateOf(false) }
    var showDetailEditor by remember { mutableStateOf(false) }
    // 弹层存活期内的"现在"，用于判断手动选的时刻今天是否已过。
    val nowMillis = remember { AppTime.nowMillis() }
    val today = remember { AppTime.today() }
    // 日历上「今天」的描边圈去掉（zuige：24 号是选中的实心、23 号还有个圈，容易误读成"选了两天"）。
    // 只去掉描边，选中态与区间底色不受影响。
    val pickerColors = DatePickerDefaults.colors(todayDateBorderColor = Color.Transparent)

    /** 句子解析 + 手动覆盖 合并后的最终预览（提交也用它）。 */
    fun effectiveParses(): List<ParsedTodo> = parses.mapIndexed { index, p ->
        val date = if (dateOverrides.containsKey(index)) dateOverrides[index] else p.dueDate
        var start = if (startDateOverrides.containsKey(index)) startDateOverrides[index] else p.startDate
        val time = timeOverrides[index] ?: p.dueTime
        val title = titleOverrides[index] ?: p.title
        val detail = if (detailOverrides.containsKey(index)) detailOverrides[index] else p.detail
        // 开始晚于结束时自动对调，保证区间永远有效。
        var end = date
        if (start != null && end != null && start.isAfter(end)) {
            val t = start; start = end; end = t
        }
        // 只有开始日没有结束日的"区间"没有意义（而且会因为 dueAtMillis 为空在列表里消失），
        // 用户在结束日上选了「无日期」= 整条都不要日期，开始日一并清掉。
        if (end == null) start = null
        if (end == null && time == null && start == null &&
            !dateOverrides.containsKey(index) && !startDateOverrides.containsKey(index) &&
            !titleOverrides.containsKey(index) && !detailOverrides.containsKey(index)
        ) {
            p
        } else {
            p.copy(
                title = title,
                dueDate = end,
                dueTime = time,
                detail = detail,
                startDate = start,
                // hasExactTime 是计算属性（dueTime != null）：手动选了时刻自动就是精确提醒，
                // 不需要也不能在 copy 里传。
                // 覆盖后重新判断"今天已过"：用户把日期改到未来就不该再报 dateError。
                dateError = false,
                timeError = time != null && end == today && time.isBefore(
                    java.time.Instant.ofEpochMilli(nowMillis).atZone(AppTime.ZONE).toLocalTime()
                )
            )
        }
    }
    // 标题被手动清空时不能提交（VM 会判 Blank，这里提前禁掉按钮并说明原因）。
    val anyTitleBlank = effectiveParses().none { it.cleanTitle.isNotBlank() }

    // 添加模式开局必须重新解析一次：ViewModel 里的预览可能还是上一句话的结果
    // （曾导致空输入框挂着一条「10月23日」的幽灵 chip）。
    // **编辑模式不重新解析**：parses 已经由 vm.previewExisting(todo) 按库里的 epoch 精确反推好了。
    // 再拿回填句子解析一遍是"二手换算"——句子不写年份，一条已经过去的时间段/单日会被顺延到
    // 下一个周期，于是"点开编辑、什么都没改、点保存"就把日期改掉了。
    LaunchedEffect(initialText) { if (!editing) onPreview(initialText) }

    val submit = { onAdd(effectiveParses(), if (editing) repeatOn else true) }

    // ★★ 不用 ModalBottomSheet（v0.9.1 补丁，这一处改了三轮，前两轮都没解决）。
    //
    // ModalBottomSheet 的"弹层"其实是一个**独立的 Dialog 窗口**，而 M3 给这个窗口设的是
    // SDK 30+ 的 SOFT_INPUT_ADJUST_NOTHING：窗口自己不随键盘缩小，只能靠 ime insets
    // 把它顶上去。这条链路在真机 + 各家输入法上并不可靠 —— 实测换了输入法之后，
    // sheet 就只剩「圆角把手 + 半行标题」一条缝，调 skipPartiallyExpanded 也没救。
    //
    // 现在改成**应用内覆盖层**：它和列表在同一个窗口里，键盘避让走主窗口的 ime insets。
    // 设置页那几个输入框一直正常，说明主窗口这条路是可靠的。
    Box(Modifier.fillMaxSize()) {
        // 遮罩：点空白关闭。indication = null —— 整屏涟漪太吓人。
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
        // imePadding 加在这一层：键盘弹起时整块面板贴到键盘上沿，
        // 面板可用高度自动变成「屏高 − 键盘」，内部 verticalScroll 消化超长内容。
        Box(
            Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
        // ★ 必须用 Surface，不能只靠 Box + background。
        //   `LocalContentColor` 的默认值是 **Color.Black** —— 只有 Surface / Scaffold 这类
        //   容器会把它设成 contentColorFor(containerColor)。上一版把 ModalBottomSheet 换成
        //   Box + background 后没人设它，换个深色配色之后标题「添加待办」就成了黑字，看不见。
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (editing) "编辑待办" else "添加待办",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t; onPreview(t) },
                // 占位文字调浅：它是"示例"，不该和已输入的内容抢注意力。
                placeholder = {
                    Text("周五下午3点 交周报", color = MaterialTheme.colorScheme.placeholderText)
                },
                singleLine = false,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.fieldBorder,
                    focusedContainerColor = MaterialTheme.colorScheme.fieldSurface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.fieldSurface
                ),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )

            // Repeating plan switch (edit mode only): turn "每天/每周/每月" off.
            if (editing && initialRepeatDays > 0) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.fieldSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("重复", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (repeatOn) "${repeatText(initialRepeatDays)}重复"
                                else "只做一次（关闭重复计划）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = repeatOn, onCheckedChange = { repeatOn = it })
                    }
                }
            }

            // Short, action-oriented errors.
            val err = when {
                parses.any { it.dateError } -> "日期已过，换个未来的日期"
                parses.any { it.timeError } -> "时间已过，换个未来的时间"
                else -> null
            }
            if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Preview card: date / time / event / detail. Multiple dates -> multiple rows.
            // v0.7：日期/时间 chip 可点，弹出选择器手动改（覆盖句子解析值）；
            // v0.7.1：标题（事件）与详情同样可点，弹对话框编辑。
            if (text.isNotBlank()) {
                PreviewCard(
                    parses = parses,
                    dateOverrides = dateOverrides,
                    startDateOverrides = startDateOverrides,
                    timeOverrides = timeOverrides,
                    titleOverrides = titleOverrides,
                    detailOverrides = detailOverrides,
                    onPickDate = { index ->
                        pickingIndex = index
                        showDatePicker = true
                    },
                    onPickRange = { index ->
                        pickingIndex = index
                        showRangePicker = true
                    },
                    onPickTime = { index ->
                        pickingIndex = index
                        showTimePicker = true
                    },
                    onEditTitle = { index ->
                        pickingIndex = index
                        showTitleEditor = true
                    },
                    onEditDetail = { index ->
                        pickingIndex = index
                        showDetailEditor = true
                    }
                )
            }

            Button(
                onClick = { submit() },
                // 标题被手动清空时禁用：空标题的待办没有意义（VM 也会判 Blank）。
                enabled = text.isNotBlank() && !anyTitleBlank,
                shape = CircleShape,
                // 「添加」是主按钮，但不需要一块高饱和色块贴在页面底部：
                // actionContainer 是"种子色低透明度叠 surface"的结果，和卡片同族；
                // 禁用态用 M3 标准比例，保证"看得出能点 / 点不了"。
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.actionContainer,
                    contentColor = MaterialTheme.colorScheme.actionContent,
                    disabledContainerColor = MaterialTheme.colorScheme.actionDisabledContainer,
                    disabledContentColor = MaterialTheme.colorScheme.actionDisabledContent
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (editing) "保存" else "添加")
            }
        }

        // ── 日期选择器（v0.7：预览卡片日期 chip 点击弹出）──
        if (showDatePicker && pickingIndex in parses.indices) {
            val current = dateOverrides[pickingIndex] ?: parses[pickingIndex].dueDate
            val pickerState = rememberDatePickerState(
                // ★ 必须是「UTC 当日 00:00」（见 AppTime.pickerMillis 的说明）。
                //   以前传本地午夜，日历按 UTC 读出前一天 → 用户看到"日期比实际小 1"。
                initialSelectedDateMillis = AppTime.pickerMillis(current ?: AppTime.today()),
                // 不能选过去的日期：提醒排在过去的时刻没有意义。
                selectableDates = TodayOrLater
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    Row {
                        // 只有"本来有日期"时才给「无日期」入口 —— 选错了能回头，
                        // 但「清除时间」故意不做（zuige：时间设了就在，可改不可清）。
                        if (current != null) {
                            TextButton(onClick = {
                                dateOverrides = dateOverrides + (pickingIndex to null)
                                showDatePicker = false
                            }) { Text("无日期") }
                        }
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                // DatePicker 给的是 UTC 当天 00:00，按 UTC 解回用户选的那天。
                                dateOverrides = dateOverrides + (pickingIndex to AppTime.pickerDate(millis))
                            }
                            showDatePicker = false
                        }) { Text("确定") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            ) { DatePicker(state = pickerState, colors = pickerColors) }
        }

        // ── 时间段：起止日期在同一个日历里选（v0.8.9）──
        // 以前是「开始 / 结束」两个 chip 各弹一个单日日历，改一条区间要开两次、还得自己保证先后顺序。
        // 现在合成一个 chip，点开就是 DateRangePicker：一次点起点、一次点终点，中间自动涂区间底色。
        if (showRangePicker && pickingIndex in parses.indices) {
            val start = startDateOverrides[pickingIndex] ?: parses[pickingIndex].startDate
            val end = dateOverrides[pickingIndex] ?: parses[pickingIndex].dueDate
            val rangeState = rememberDateRangePickerState(
                // 同单日选择器：UTC 当日 00:00 口径（见 AppTime.pickerMillis），否则整段少一天。
                initialSelectedStartDateMillis = start?.let { AppTime.pickerMillis(it) },
                initialSelectedEndDateMillis = end?.let { AppTime.pickerMillis(it) }
                // ★ 不传 selectableDates：区间允许选过去的日期。
                //   进行中 / 已开始的时间段（比如今天在改 10/21–10/26）本来就落在过去，
                //   沿用单日选择器的「今天起」会让开始日根本改不了。
            )
            DatePickerDialog(
                onDismissRequest = { showRangePicker = false },
                confirmButton = {
                    Row {
                        // 取消开始日 = 退回单日：结束日留着，开始日清掉。
                        // 只给一个次要按钮，避免确认区被三个文字按钮挤爆；
                        // 想彻底去掉日期，先退回单日再从单日 chip 上选「无日期」。
                        if (start != null && end != null) {
                            TextButton(onClick = {
                                startDateOverrides = startDateOverrides + (pickingIndex to null)
                                showRangePicker = false
                            }) { Text("取消开始日") }
                        }
                        TextButton(onClick = {
                            val s = rangeState.selectedStartDateMillis
                            val e = rangeState.selectedEndDateMillis
                            if (s != null && e != null) {
                                startDateOverrides =
                                    startDateOverrides + (pickingIndex to AppTime.pickerDate(s))
                                dateOverrides = dateOverrides + (pickingIndex to AppTime.pickerDate(e))
                            } else if (s != null) {
                                // 只点了第一天（还没点终点）= 用户其实只想要一天 → 退回单日。
                                startDateOverrides = startDateOverrides + (pickingIndex to null)
                                dateOverrides = dateOverrides + (pickingIndex to AppTime.pickerDate(s))
                            }
                            showRangePicker = false
                        }) { Text("确定") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRangePicker = false }) { Text("取消") }
                }
            ) {
                // weight 是必须的：DatePickerDialog 的内容槽是 Column，区间日历自己带滚动，
                // 不给权重它会按内容高度撑，月份的滚动区就被压没了。
                DateRangePicker(
                    state = rangeState,
                    colors = pickerColors,
                    // ★ 默认 headline 是「2026年10月25日 - 2026年10月31日」一整行，
                    //   在手机上被挤成一列竖排（「202」/「6年」/「10」/「月」…，一个字一行）。
                    //   自己给一个**两行**的 headline：上行开始日、下行「至 结束日」，
                    //   每行只有 6~9 个字，槽再窄也不会竖向折行。
                    headline = {
                        val s = rangeState.selectedStartDateMillis?.let { AppTime.pickerDate(it) }
                        val e = rangeState.selectedEndDateMillis?.let { AppTime.pickerDate(it) }
                        Column {
                            Text(
                                s?.let { pickerDayLabel(it) } ?: "选择开始日期",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                e?.let { "至 ${pickerDayLabel(it)}" } ?: "至 选择结束日期",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 时间选择器 ──
        if (showTimePicker && pickingIndex in parses.indices) {
            val current = timeOverrides[pickingIndex] ?: parses[pickingIndex].dueTime
            val timeState = rememberTimePickerState(
                initialHour = current?.hour ?: 9,
                initialMinute = current?.minute ?: 0,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        timeOverrides = timeOverrides + (
                            pickingIndex to java.time.LocalTime.of(timeState.hour, timeState.minute)
                            )
                        showTimePicker = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                },
                text = { TimePicker(state = timeState) }
            )
        }

        // ── 标题（事件）编辑（v0.7.1）──
        if (showTitleEditor && pickingIndex in parses.indices) {
            val current = titleOverrides[pickingIndex] ?: parses[pickingIndex].title
            var draft by remember(pickingIndex) { mutableStateOf(current) }
            AlertDialog(
                onDismissRequest = { showTitleEditor = false },
                title = { Text("编辑事件") },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text("事件") },
                        placeholder = { Text("开会") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    // 空标题不给保存：保存了待办就没有名字，且提交按钮会被禁用，用户会卡住。
                    TextButton(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            titleOverrides = titleOverrides + (pickingIndex to draft.trim())
                            showTitleEditor = false
                        }
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showTitleEditor = false }) { Text("取消") }
                }
            )
        }

        // ── 详情编辑（v0.7.1）──
        if (showDetailEditor && pickingIndex in parses.indices) {
            val current = detailOverrides[pickingIndex] ?: parses[pickingIndex].detail.orEmpty()
            var draft by remember(pickingIndex) { mutableStateOf(current) }
            AlertDialog(
                onDismissRequest = { showDetailEditor = false },
                title = { Text("编辑详情") },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 3,
                        maxLines = 5,
                        label = { Text("详情") },
                        placeholder = { Text("要带笔记本") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        // 详情可以为空：清空 = 没有详情（override 记空串）。
                        detailOverrides = detailOverrides + (pickingIndex to draft.trim())
                        showDetailEditor = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showDetailEditor = false }) { Text("取消") }
                }
            )
        }
        }
        }
    }
    // 返回键关闭：覆盖层不再是独立窗口，系统不会自动处理返回。
    BackHandler { onDismiss() }
}

/** 日期选择器的可选范围：今天起（毫秒按 UTC 口径解回日期，避免 UTC 边界差一天）。 */
private val TodayOrLater = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !AppTime.pickerDate(utcTimeMillis).isBefore(AppTime.today())
}

@Composable
private fun PreviewCard(
    parses: List<ParsedTodo>,
    dateOverrides: Map<Int, java.time.LocalDate?>,
    startDateOverrides: Map<Int, java.time.LocalDate?>,
    timeOverrides: Map<Int, java.time.LocalTime>,
    titleOverrides: Map<Int, String>,
    detailOverrides: Map<Int, String?>,
    onPickDate: (Int) -> Unit,
    onPickRange: (Int) -> Unit,
    onPickTime: (Int) -> Unit,
    onEditTitle: (Int) -> Unit,
    onEditDetail: (Int) -> Unit
) {
    val items = parses.mapIndexed { index, p ->
        // 有覆盖就用覆盖值渲染，否则用句子解析值。
        val date = if (dateOverrides.containsKey(index)) dateOverrides[index] else p.dueDate
        val start = if (startDateOverrides.containsKey(index)) startDateOverrides[index] else p.startDate
        val time = timeOverrides[index] ?: p.dueTime
        val title = titleOverrides[index] ?: p.title
        val detail = if (detailOverrides.containsKey(index)) detailOverrides[index] else p.detail
        PreviewRow(index, p, start, date, time, title, detail)
    }.filter { row ->
        row.date != null || row.time != null || row.start != null ||
            row.p.repeatDays > 0 || row.title.isNotBlank()
    }
    if (items.isEmpty()) return

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        // 与设置页卡片、待办行同一套卡片色（Tokens.cardBackground），
        // 不再用 surfaceContainerHigh —— 那是中性灰，和主题配色对不上。
        color = MaterialTheme.colorScheme.cardBackground,
        // ★ 与输入框/按钮严格同宽：三者都在同一个 Column 里 fillMaxWidth，
        //   任何一方用 wrapContentWidth 或额外的水平 padding 都会破坏对齐。
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            // 12dp 而不是 16dp：预览卡片是"辅助信息"，不该比输入框还厚重
            // （用户反馈它"往下突出、压不住"，主要就是这块内边距太肥）。
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.size > 1) {
                Text(
                    "将创建 ${items.size} 条待办",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items.forEachIndexed { i, row ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                PreviewItem(
                    p = row.p,
                    start = row.start,
                    date = row.date,
                    time = row.time,
                    title = row.title,
                    detail = row.detail,
                    onPickDate = { onPickDate(row.index) },
                    onPickRange = { onPickRange(row.index) },
                    onPickTime = { onPickTime(row.index) },
                    onEditTitle = { onEditTitle(row.index) },
                    onEditDetail = { onEditDetail(row.index) }
                )
            }
        }
    }
}

/**
 * chip 的水平内边距。**标题要靠它左对齐**：chip 的背景从卡片 padding 起算，
 * 但里面的文字还有一个内边距 —— 标题若只对齐到背景，视觉上会比日期文字靠左
 * （用户反馈"开会太靠左下角，与上面日期对齐"）。抽成常量让两边永远一致。
 */
private val PREVIEW_CHIP_HPAD = 12.dp

/** 一条预览行的合并后视图（句子解析 + 手动覆盖）。 */
private data class PreviewRow(
    val index: Int,
    val p: ParsedTodo,
    val start: java.time.LocalDate?,
    val date: java.time.LocalDate?,
    val time: java.time.LocalTime?,
    val title: String,
    val detail: String?
)

/**
 * 一条预览。日期/时间 chip 可点（v0.7）：
 * - 有日期 → 日期 chip 可点，弹日历改；选择器里可改成「无日期」
 * - 有时间 → 时间 chip 可点，弹时间滚轮改（**不可清除**，zuige 要求）
 * - 缺哪个维度 → 补一个「+ 日期」/「+ 时间」的次要 chip，点了加上
 *
 * 标题（事件）与详情也可点（v0.7.1）：弹对话框编辑，同样是覆盖句子解析值。
 * 行尾的小铅笔表示"这一行可以手动改"。
 */
@Composable
private fun PreviewItem(
    p: ParsedTodo,
    start: java.time.LocalDate?,
    date: java.time.LocalDate?,
    time: java.time.LocalTime?,
    title: String,
    detail: String?,
    onPickDate: () -> Unit,
    onPickRange: () -> Unit,
    onPickTime: () -> Unit,
    onEditTitle: () -> Unit,
    onEditDetail: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (start != null) {
                // 时间段：起止**合成一个 chip**（v0.8.9），点开是同一个日历里的区间选择。
                // 以前是两个 chip 各弹一个单日日历，改一条区间要开两次、还得自己保证先后。
                InfoChip(
                    if (date != null) rangeLabel(start, date) else "开始 ${Format.dateShort(start)}",
                    onClick = onPickRange
                )
            } else if (date != null) {
                InfoChip(Format.dateShort(date), onClick = onPickDate)
            } else {
                InfoChip("+ 日期", onClick = onPickDate)
            }
            if (time != null) {
                InfoChip(Format.timeLabel(time), onClick = onPickTime)
            } else {
                InfoChip("+ 时间", onClick = onPickTime)
            }
            when (p.repeatDays) {
                1 -> InfoChip("每天")
                7 -> InfoChip("每周")
                in 1..Int.MAX_VALUE -> InfoChip("每${p.repeatDays}天")
            }
        }
        // 标题行：可点（编辑事件）。不加图标 —— zuige：右边的编辑图标不需要，
        // 可点性用点击反馈表达，不靠视觉装饰。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEditTitle)
                .padding(start = PREVIEW_CHIP_HPAD, top = 2.dp, bottom = 2.dp)
        ) {
            Text(
                title.trim().ifBlank { "（没有事件内容）" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        // 详情行：可点（编辑详情）。没有详情时显示「+ 详情」。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEditDetail)
                .padding(start = PREVIEW_CHIP_HPAD, top = 2.dp, bottom = 2.dp)
        ) {
            Text(
                detail?.takeIf { it.isNotBlank() } ?: "+ 详情",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 预览里的小 chip。**所有 chip 同一种样式**（实底 secondaryContainer）——
 * v0.7.1 曾给「+ 日期 / + 时间」用空心描边区分"补充入口"，结果同一张卡片里
 * 两种 chip 样式，用户反馈"图标不统一"。现在一律实底，可点的加点击涟漪。
 */
@Composable
private fun InfoChip(
    text: String,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = PREVIEW_CHIP_HPAD, vertical = 6.dp)
        )
    }
}

/**
 * 区间日历 headline 里的一天：「10月25日」，不是今年才带年份。
 * 不用 `Format.dateShort`（带周几，太长，正是它把 headline 挤成竖排的）。
 */
private fun pickerDayLabel(d: java.time.LocalDate): String =
    if (d.year == AppTime.today().year) Format.monthDay(d)
    else "${d.year}年${Format.monthDay(d)}"

/**
 * 时间段 chip 的文案：「10月21日 – 10月26日」，跨年才补年份。
 * 不用 `Format.dateShort`（它带周几，两段拼起来太长，会把 chip 撑到换行）。
 */
private fun rangeLabel(start: java.time.LocalDate, end: java.time.LocalDate): String {
    val a = Format.monthDay(start)
    val b = Format.monthDay(end)
    return if (start.year == end.year) "$a – $b"
    else "${start.year}年$a – ${end.year}年$b"
}

/** 每天 / 每周 / 每月 / 每 N 天, for the repeat switch subtitle. */
private fun repeatText(days: Int): String = when {
    days == 1 -> "每天"
    days == 7 -> "每周"
    days >= 28 -> "每月"
    else -> "每${days}天"
}
