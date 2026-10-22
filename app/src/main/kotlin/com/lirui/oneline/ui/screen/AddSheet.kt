package com.lirui.oneline.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lirui.oneline.util.Format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Boolean) -> Unit,
    onPreview: (String) -> Unit,
    parses: List<ParsedTodo>,
    initialText: String = "",
    editing: Boolean = false,
    initialRepeatDays: Int = 0
) {
    val sheetState = rememberModalBottomSheetState()
    // Plain remember (not rememberSaveable): the sheet leaves composition on dismiss, and a
    // saveable state would resurrect the previous sentence on the next open.
    var text by remember(initialText) { mutableStateOf(initialText) }
    // Only meaningful while editing an existing repeating todo: lets the user turn the
    // "每天/每周/每月" plan off without rewriting the sentence.
    var repeatOn by remember(initialRepeatDays) { mutableStateOf(initialRepeatDays > 0) }

    // Re-parse on open so the shared ViewModel preview is never a stale result from the
    // previously added todo (that caused a phantom "10月23日" chip on an empty field).
    LaunchedEffect(initialText) { onPreview(initialText) }

    val submit = { onAdd(text, if (editing) repeatOn else true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
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
            if (text.isNotBlank()) {
                PreviewCard(parses)
            }

            Button(
                onClick = { submit() },
                enabled = text.isNotBlank(),
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
    }
}

@Composable
private fun PreviewCard(parses: List<ParsedTodo>) {
    val items = parses.filter {
        it.dueDate != null || it.dueTime != null || it.repeatDays > 0 || it.cleanTitle.isNotBlank()
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
            items.forEachIndexed { index, p ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                PreviewItem(p)
            }
        }
    }
}

@Composable
private fun PreviewItem(p: ParsedTodo) {
    val hasChips = p.dueDate != null || p.dueTime != null || p.repeatDays > 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasChips) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.dueDate?.let { InfoChip(Format.dateShort(it)) }
                p.dueTime?.let { InfoChip(Format.timeLabel(it)) }
                when (p.repeatDays) {
                    1 -> InfoChip("每天")
                    7 -> InfoChip("每周")
                    in 1..Int.MAX_VALUE -> InfoChip("每${p.repeatDays}天")
                }
            }
        }
        Text(
            p.cleanTitle.ifBlank { "（没有事件内容）" },
            style = MaterialTheme.typography.titleMedium
        )
        p.detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 每天 / 每周 / 每月 / 每 N 天, for the repeat switch subtitle. */
private fun repeatText(days: Int): String = when {
    days == 1 -> "每天"
    days == 7 -> "每周"
    days >= 28 -> "每月"
    else -> "每${days}天"
}
