package com.lirui.oneline.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirui.oneline.update.AppUpdate
import com.lirui.oneline.ui.theme.actionContainer
import com.lirui.oneline.ui.theme.actionContent
import com.lirui.oneline.ui.theme.placeholderText
import com.lirui.oneline.ui.theme.secondaryActionContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

/**
 * 发现新版本的弹窗。
 *
 * 版式借鉴 Hyper MeloLock 的 `UpdateDialogHost`：当前版本 / 最新版本 / changelog 三块，
 * 取消 + 下载两个按钮。差别是本项目主源 GitHub、blog 静态源仅在 GitHub 被墙/限流时兜底，
 * 没有"必须下载"的需求，所以按钮文案会随 `apkUrl` 是否为空切换成「打开发布页」。
 */
@Composable
fun UpdateAvailableDialog(
    update: AppUpdate,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenPage: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val hasApk = update.apkUrl.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("发现新版本", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("当前版本：v$currentVersion", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "最新版本：v${update.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (update.changelog.isNotBlank()) {
                    Text(
                        "更新内容",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    // changelog 是 release 的 markdown 原文，逐行渲染即可 ——
                    // 上 LazyColumn 是为了长日志能在弹窗里滚动，而不是把弹窗撑出屏幕。
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(update.changelog.trim().lines()) { raw ->
                            val line = raw.trim()
                            if (line.isEmpty()) return@items
                            val clean = line.trimStart('#').trim().replace("**", "")
                            when {
                                line.startsWith("- ") || line.startsWith("* ") -> Text(
                                    "• ${clean.drop(2).trim()}",
                                    fontSize = 13.sp,
                                    color = cs.onSurfaceVariant
                                )
                                line.startsWith("#") -> Text(
                                    clean,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface
                                )
                                else -> Text(
                                    clean,
                                    fontSize = 13.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (hasApk) onDownload else onOpenPage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.actionContainer,
                    contentColor = cs.actionContent
                )
            ) {
                Text(if (hasApk) "下载更新" else "打开发布页")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("以后再说", color = cs.secondaryActionContent)
            }
        }
    )
}

/** 检查失败的弹窗。文案直接复用 [UpdateCheck.Failed.reason]，不再二次加工。 */
@Composable
fun UpdateFailedDialog(reason: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("检查更新失败", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reason, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "也可以直接到 GitHub Releases 手动下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.placeholderText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.actionContainer,
                    contentColor = cs.actionContent
                )
            ) {
                Text("知道了")
            }
        }
    )
}
