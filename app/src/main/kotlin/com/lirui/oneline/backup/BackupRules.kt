package com.lirui.oneline.backup

import com.lirui.oneline.util.AppTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 备份的「规则层」：纯 Kotlin + java.time，**不碰 Android / org.json**。
 *
 * ★ 为什么单独拆出来：`org.json` 是 android.jar 里的类，本地单测跑的是 stub
 *   （`unitTests.isReturnDefaultValues = true`），JSON 读写在 JVM 单测里根本执行不了。
 *   把「什么算合法备份」「哪些键能进出」「文件名长什么样」这些**判定**放到这里，
 *   就能正常补单测；真正的流读写靠真机导入导出验收。
 *
 * ★ 格式是**明文 JSON**（见 BackupCodec），人能读懂、能手工改，不是私有二进制。
 *   代价是谁拿到文件谁就能读 —— 这一点必须写在恢复弹窗和隐私政策里，不能藏着。
 */
object BackupRules {

    const val FORMAT = "oneline-backup"
    const val VERSION = 1
    const val MIN_SUPPORTED_VERSION = 1
    /** 与 AppDatabase 的 `@Database(version = 7)` 对齐；加迁移时两边一起改。 */
    const val DB_VERSION = 7
    const val MIME_TYPE = "application/json"

    const val SETTINGS_PREF = "oneline_settings"
    const val FOCUS_PREF = "oneline_focus"

    /**
     * 恢复模式（v1.0.11，zuige 要求给三个选项让用户自己选）。
     *
     * - [REPLACE]：清空当前记录，只留备份里的 —— 语义最干净，但会丢现有数据。
     * - [KEEP_AND_IMPORT]：现有数据不动，备份里的记录全部追加（id 重新生成，可能重复）。
     * - [DEDUPLICATE]：现有数据不动，只补进备份里 id 不重复的记录（靠 INSERT OR IGNORE）。
     */
    enum class RestoreMode(val label: String, val detail: String) {
        REPLACE("整份覆盖", "清空当前待办与专注记录，只保留备份里的内容"),
        KEEP_AND_IMPORT("保留并导入", "现有数据不动，把备份里的记录全部追加进来（可能重复）"),
        DEDUPLICATE("去重导入", "现有数据不动，只补进 id 不重复的记录")
    }

    /**
     * 隐私政策同意版本是「本机当前状态」，不是用户配置。
     * 导出旧备份再导入不该让用户重新弹同意，更不该被旧值绕过当前版本的同意。
     */
    private const val KEY_PRIVACY_VERSION = "privacy_accepted_version"

    /** `oneline_focus` 里只有这两个是设置；其余全是「进行中的专注」运行态，不进备份。 */
    private val FOCUS_SETTING_KEYS = setOf("last_minutes", "last_count_up")

    /** 版本不认就明确拒绝，而不是猜着读 —— 读坏一份备份比不读更糟。 */
    fun isSupported(format: String?, version: Int): Boolean =
        format == FORMAT && version in MIN_SUPPORTED_VERSION..VERSION

    fun importableSetting(key: String): Boolean = key != KEY_PRIVACY_VERSION

    fun exportableFocusKey(key: String): Boolean = key in FOCUS_SETTING_KEYS

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)
    private val DISPLAY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    /** 文件名只用数字和连字符，避免厂商文件选择器对中文/空格做奇怪的转义。 */
    fun suggestedFileName(epochMillis: Long): String =
        "oneline-backup-" + Instant.ofEpochMilli(epochMillis).atZone(AppTime.ZONE)
            .format(FILE_STAMP) + ".json"

    /** 恢复弹窗里显示的导出时间，统一北京时间（项目红线：不用 systemDefault / 无参 now）。 */
    fun formatTime(epochMillis: Long): String =
        if (epochMillis <= 0L) "未知" else Instant.ofEpochMilli(epochMillis)
            .atZone(AppTime.ZONE).format(DISPLAY_STAMP)

    fun describe(todos: Int, sessions: Int): String = "待办 $todos 条 · 专注记录 $sessions 条"
}
