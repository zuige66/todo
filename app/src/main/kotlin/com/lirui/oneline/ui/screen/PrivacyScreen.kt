@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lirui.oneline.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/**
 * 隐私政策（v1.0.0 新增）。
 *
 * ★ 为什么是**本地文本**而不是 WebView / 外链：
 *   1. 本应用不申请 INTERNET 权限，WebView 根本加载不了任何页面；
 *   2. 商店审核会点开隐私政策入口 —— 外链打不开（域名海外、审核网络环境差异）会被判
 *      「隐私政策无法访问」，这是常见的拒审理由。文本内嵌，永远打得开。
 *
 * 正文必须与 docs/PRIVACY.md 保持一致（那份用于发布到网站，商店后台填的是网址）。
 * 改内容时两边一起改，并递增 [com.lirui.oneline.util.SettingsRepo.PRIVACY_POLICY_VERSION]。
 */

private data class PolicySection(val title: String, val body: String)

private val POLICY_SECTIONS = listOf(
    PolicySection(
        "一、我们不收集任何个人信息",
        "本应用没有账号体系，不要求注册、登录，也不需要手机号、邮箱或任何身份信息。\n\n" +
            "具体而言，本应用不收集设备标识符（IMEI、Android ID、OAID、MAC 地址等），" +
            "不收集位置信息，不读取通讯录、短信、通话记录，不访问相机、麦克风、相册，" +
            "不收集使用行为与崩溃日志，也不集成任何第三方统计、广告、推送或社交 SDK。\n\n" +
            "本应用的全部功能均可离线使用。"
    ),
    PolicySection(
        "二、权限清单及用途",
        "本应用仅申请实现核心功能所必需的权限：\n\n" +
            "· 通知：在待办到期时发送提醒。拒绝后收不到提醒，其余功能正常。\n\n" +
            "· 精确闹钟：让提醒在你设定的精确时刻响铃。系统非精确闹钟可能延迟数分钟。\n\n" +
            "· 开机自启：设备重启后重建已设定的提醒，否则重启会丢失全部提醒。\n\n" +
            "· 忽略电池优化：仅在你主动点击「提醒可靠性」页的按钮时跳转系统白名单页。" +
            "本应用不会在启动时自动弹出该请求。\n\n" +
            "· 前台服务：可选的「后台常驻通知」，默认开启，用于在通知栏直接完成或撤回待办，" +
            "你可随时在设置中关闭。\n\n" +
            "本应用不申请位置、通讯录、短信、通话记录、相机、麦克风、存储读写、设备标识等权限，" +
            "也不申请网络权限。"
    ),
    PolicySection(
        "三、网络请求说明",
        "本应用不申请网络权限（INTERNET），不会向任何服务器发送数据，也不存在任何自动联网行为。\n\n" +
            "关于页里的 GitHub、博客链接在点击后会交给系统浏览器打开 —— 联网的是浏览器，" +
            "不是本应用，本应用不参与该过程，也不获取任何结果。"
    ),
    PolicySection(
        "四、你的数据存储在哪里",
        "你的全部待办数据（标题、详情、日期、时间、重复规则、完成状态、设置项）均存储于" +
            "你设备本地的应用私有目录（Android 应用沙盒内），其他应用无法读取。\n\n" +
            "数据不会上传到任何服务器，不会同步到云端。本应用已关闭系统自动备份，" +
            "数据不会被备份到云端。\n\n" +
            "删除方式：在应用内删除单条待办，该条数据立即从本地数据库删除；" +
            "在系统设置中「清除数据」，全部待办与设置立即删除；卸载本应用，" +
            "全部数据随应用一并删除且不可恢复。"
    ),
    PolicySection(
        "五、第三方共享",
        "我们不会向任何第三方提供、共享、转让或公开披露你的个人信息，因为我们根本不收集。\n\n" +
            "本应用不包含任何第三方 SDK。所用开源组件（AndroidX、Jetpack Compose、Room、" +
            "Kotlin 协程等）均为本地运行的代码库，不采集数据、不联网。"
    ),
    PolicySection(
        "六、未成年人保护",
        "本应用为通用工具类软件，不设年龄限制，不包含任何可能影响未成年人身心健康的内容。\n\n" +
            "由于本应用不收集任何个人信息，不涉及未成年人个人信息的处理。" +
            "若你是未满 14 周岁的未成年人，请在监护人指导下使用本应用。"
    ),
    PolicySection(
        "七、你的权利",
        "由于本应用不收集个人信息，你无需行使查询、更正、删除个人信息的权利 —— 数据始终只在你手中。\n\n" +
            "你随时可以在系统设置中撤销本应用的任何权限、在应用内关闭提醒功能，" +
            "或卸载本应用以删除全部数据。"
    ),
    PolicySection(
        "八、政策变更",
        "若本应用未来新增需要处理个人信息的功能，我们会在功能上线前更新本政策，" +
            "并在应用内以弹窗方式重新征得你的同意。\n\n" +
            "本政策的更新版本将发布在同一网址，并标注新的生效日期。"
    ),
    PolicySection(
        "九、联系我们",
        "对本政策有任何疑问、意见或投诉，可通过以下方式联系开发者：\n\n" +
            "· 邮箱：lirui.dev@outlook.com\n" +
            "· 博客：blog.zuiges.com\n\n" +
            "我们将在收到你的问题后 15 个工作日内答复。"
    )
)

/** 隐私政策的生效日期。改内容时一起改。 */
private const val POLICY_EFFECTIVE_DATE = "2026 年 10 月 25 日"

/**
 * 隐私政策全屏页。作为覆盖层使用（不进导航栈），返回键 / 左上角箭头都能退出。
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    // 覆盖层没有导航栈，返回键必须自己接住，否则会直接退出应用。
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = cs.onSurface
                    )
                }
                Text(
                    "隐私政策",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        "一句话待办",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "生效日期：$POLICY_EFFECTIVE_DATE",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    )
                    Text(
                        "本应用深知个人信息对你的重要性，并承诺不收集、不上传、不共享你的任何个人信息。" +
                            "本政策说明本应用如何处理你的数据，以及你享有的权利。",
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = cs.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(POLICY_SECTIONS) { section ->
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(
                            section.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                        Text(
                            section.body,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = cs.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                item {
                    Text(
                        "X ICP 备 XXXXXXXX 号-XX",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
}

/**
 * 首次启动的隐私政策同意弹窗。
 *
 * ★ 三条不可妥协的约束（商店审核逐条对照）：
 *   1. 点遮罩、按返回键都**不能**关闭 —— 必须显式选择「同意」或「不同意」；
 *   2. 「不同意」必须真的退出应用，不能只是关掉弹窗继续用；
 *   3. 弹窗出现在**任何权限申请之前**（见 MainActivity：同意之后才申请通知权限）。
 */
@Composable
fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    onViewPolicy: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = {},
        // 两条都必须显式关掉：默认点遮罩 / 按返回键就能消失，等于用户可以绕过同意。
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text("欢迎使用一句话待办", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "本应用不收集、不上传任何个人信息，待办数据仅保存在本机。",
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(12.dp))
                Text("使用前请阅读并同意以下政策：", fontSize = 15.sp, lineHeight = 22.sp)
                Text(
                    "《隐私政策》",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    color = cs.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onViewPolicy
                        )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("同意并继续", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) {
                Text("不同意并退出", color = cs.onSurfaceVariant)
            }
        }
    )
}
