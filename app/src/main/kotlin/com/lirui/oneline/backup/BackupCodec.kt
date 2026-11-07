package com.lirui.oneline.backup

import android.content.Context
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.data.model.FocusSession
import com.lirui.oneline.data.model.Todo
import org.json.JSONArray
import org.json.JSONObject

/** 一份备份在内存里的样子。设置值保留原始类型，写回时才能 putBoolean / putInt 各归各位。 */
data class BackupData(
    val exportedAtMillis: Long,
    val todos: List<Todo>,
    val sessions: List<FocusSession>,
    val settings: Map<String, Any?>,
    val focusSettings: Map<String, Any?>
)

/**
 * 备份文件的编解码。用 Android 平台自带的 `org.json`：
 * 不引第三方序列化库、不需要网络权限，也没有反射模型类 —— R8 全开的 release 包安全。
 *
 * 文件结构（`toString(2)` 缩进，人能直接读）：
 * ```
 * {
 *   "format": "oneline-backup", "version": 1,
 *   "exportedAtMillis": ..., "dbVersion": 7,
 *   "todos": [ { id, title, detail, dueAtMillis, startAtMillis, endAtMillis,
 *                hasExactTime, repeatDays, archived, createdAt, completedAt, deletedAt } ],
 *   "focusSessions": [ { id, label, startedAtMillis, endedAtMillis,
 *                        plannedSeconds, actualSeconds, completed, createdAt } ],
 *   "settings":      { "<key>": { "t": "b|i|l|d|s", "v": ... } },
 *   "focusSettings": { 同上，只有 last_minutes / last_count_up }
 * }
 * ```
 */
object BackupCodec {

    suspend fun exportJson(context: Context): String {
        val db = AppDatabase.get(context)
        val todos = db.todoDao().getAllIncludingArchived()
        val sessions = db.focusDao().getAll()

        val root = JSONObject()
            .put("format", BackupRules.FORMAT)
            .put("version", BackupRules.VERSION)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("dbVersion", BackupRules.DB_VERSION)

        val todoArray = JSONArray()
        todos.forEach { todoArray.put(encodeTodo(it)) }
        root.put("todos", todoArray)

        val sessionArray = JSONArray()
        sessions.forEach { sessionArray.put(encodeSession(it)) }
        root.put("focusSessions", sessionArray)

        root.put(
            "settings",
            encodePrefs(context, BackupRules.SETTINGS_PREF, BackupRules::importableSetting)
        )
        root.put(
            "focusSettings",
            encodePrefs(context, BackupRules.FOCUS_PREF, BackupRules::exportableFocusKey)
        )
        return root.toString(2)
    }

    /** @throws IllegalArgumentException 文件不是本应用的备份 / 版本不兼容，消息可直接给用户看。 */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("这不是有效的备份文件（不是 JSON）")
        }
        val format = root.optString("format", "")
        val version = root.optInt("version", -1)
        if (!BackupRules.isSupported(format, version)) {
            throw IllegalArgumentException(
                "备份格式或版本不兼容（读到 format=$format / version=$version）"
            )
        }

        val todoArray = root.optJSONArray("todos") ?: JSONArray()
        val todos = (0 until todoArray.length()).mapNotNull { i ->
            val o = todoArray.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title", "")
            // 空标题的脏行直接跳过：写进去会在列表里出现一条看不见也删不掉的待办。
            if (title.isBlank()) return@mapNotNull null
            Todo(
                id = o.optLong("id", 0L),
                title = title,
                detail = o.optString("detail", ""),
                dueAtMillis = o.longOrNull("dueAtMillis"),
                startAtMillis = o.longOrNull("startAtMillis"),
                endAtMillis = o.longOrNull("endAtMillis"),
                hasExactTime = o.optBoolean("hasExactTime", false),
                repeatDays = o.optInt("repeatDays", 0),
                archived = o.optBoolean("archived", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                completedAt = o.longOrNull("completedAt"),
                deletedAt = o.longOrNull("deletedAt")
            )
        }

        val sessionArray = root.optJSONArray("focusSessions") ?: JSONArray()
        val sessions = (0 until sessionArray.length()).mapNotNull { i ->
            val o = sessionArray.optJSONObject(i) ?: return@mapNotNull null
            val actual = o.optInt("actualSeconds", 0)
            val started = o.optLong("startedAtMillis", 0L)
            // 与 FocusRepository.record 的口径一致：0 秒 / 无开始时间都是脏行。
            if (actual <= 0 || started <= 0L) return@mapNotNull null
            FocusSession(
                id = o.optLong("id", 0L),
                label = o.optString("label", ""),
                startedAtMillis = started,
                endedAtMillis = o.optLong("endedAtMillis", started + actual * 1000L),
                plannedSeconds = o.optInt("plannedSeconds", 0),
                actualSeconds = actual,
                completed = o.optBoolean("completed", false),
                createdAt = o.optLong("createdAt", started)
            )
        }

        return BackupData(
            exportedAtMillis = root.optLong("exportedAtMillis", 0L),
            todos = todos,
            sessions = sessions,
            settings = decodePrefs(root.optJSONObject("settings"), BackupRules::importableSetting),
            focusSettings = decodePrefs(
                root.optJSONObject("focusSettings"), BackupRules::exportableFocusKey
            )
        )
    }

    /**
     * 把设置写回 SharedPreferences。
     *
     * ★★ 绝对不能 `clear()`：
     *   - `oneline_settings` 里有 `privacy_accepted_version`，清掉会让老用户重新弹同意；
     *   - `oneline_focus` 里有正在跑的专注 run，清掉计时会凭空消失、通知却还在走。
     *   做法是「把可导出的键里、备份没有的删掉；备份有的逐个写回」，
     *   这样既等价于恢复该文件的设置快照，又不碰那两类运行态。
     *
     * 用 `commit()`（同步落盘）而不是 `apply()`：紧接着就要重排提醒、重建 Activity，
     * 不能让读设置在另一个线程上看到半旧半新的值。本函数只在 IO 协程里调用。
     */
    fun applySettings(context: Context, data: BackupData) {
        applyOne(context, BackupRules.SETTINGS_PREF, data.settings, BackupRules::importableSetting)
        applyOne(
            context, BackupRules.FOCUS_PREF, data.focusSettings, BackupRules::exportableFocusKey
        )
    }

    private fun applyOne(
        context: Context,
        name: String,
        values: Map<String, Any?>,
        filter: (String) -> Boolean
    ) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { filter(it) && it !in values }.forEach { editor.remove(it) }
        for ((key, value) in values) {
            if (!filter(key)) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.commit()
    }

    // ---------- JSON 细节 ----------

    private fun encodeTodo(t: Todo): JSONObject = JSONObject()
        .put("id", t.id)
        .put("title", t.title)
        .put("detail", t.detail)
        .putNullableLong("dueAtMillis", t.dueAtMillis)
        .putNullableLong("startAtMillis", t.startAtMillis)
        .putNullableLong("endAtMillis", t.endAtMillis)
        .put("hasExactTime", t.hasExactTime)
        .put("repeatDays", t.repeatDays)
        .put("archived", t.archived)
        .put("createdAt", t.createdAt)
        .putNullableLong("completedAt", t.completedAt)
        .putNullableLong("deletedAt", t.deletedAt)

    private fun encodeSession(s: FocusSession): JSONObject = JSONObject()
        .put("id", s.id)
        .put("label", s.label)
        .put("startedAtMillis", s.startedAtMillis)
        .put("endedAtMillis", s.endedAtMillis)
        .put("plannedSeconds", s.plannedSeconds)
        .put("actualSeconds", s.actualSeconds)
        .put("completed", s.completed)
        .put("createdAt", s.createdAt)

    /** 返回 this，好让 encodeTodo 的 `.put(...)` 链继续接下去。 */
    private fun JSONObject.putNullableLong(key: String, value: Long?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key, 0L)

    /**
     * 设置值带类型标签，而不是直接塞进 JSONObject：
     * `getInt` / `getLong` / `getString` 读错类型会抛异常或悄悄截断，
     * 一个坏键不该让整份备份恢复失败。
     */
    private fun encodePrefs(
        context: Context,
        name: String,
        filter: (String) -> Boolean
    ): JSONObject {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            if (!filter(key)) continue
            val cell = JSONObject()
            when (value) {
                is Boolean -> cell.put("t", "b").put("v", value)
                is Int -> cell.put("t", "i").put("v", value)
                is Long -> cell.put("t", "l").put("v", value)
                is Float -> cell.put("t", "d").put("v", value.toDouble())
                is String -> cell.put("t", "s").put("v", value)
                // 项目里没有 StringSet 设置（分组顺序等都是逗号字符串），遇到就跳过。
                else -> continue
            }
            out.put(key, cell)
        }
        return out
    }

    private fun decodePrefs(obj: JSONObject?, filter: (String) -> Boolean): Map<String, Any?> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!filter(key)) continue
            val cell = obj.optJSONObject(key) ?: continue
            val value: Any? = when (cell.optString("t", "")) {
                "b" -> cell.optBoolean("v", false)
                "i" -> cell.optInt("v", 0)
                "l" -> cell.optLong("v", 0L)
                "d" -> cell.optDouble("v", 0.0)
                "s" -> cell.optString("v", "")
                else -> null
            }
            if (value != null) out[key] = value
        }
        return out
    }
}
