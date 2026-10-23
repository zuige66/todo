package com.lirui.oneline.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single reminder todo item.
 *
 * - [title]: the parsed description (time/date tokens removed).
 * - [detail]: optional free-text detail typed after the event title (everything after the
 *   first space of the remainder), e.g. "组会 要带笔记本去" -> title 组会, detail 要带笔记本去.
 * - [dueAtMillis]: the concrete due timestamp (in the device's timezone), or null for a
 *   "no-date" item that never fires a reminder.
 * - [hasExactTime]: whether the user gave an exact minute time (e.g. "15:30" or "下午3点").
 *   Drives the extra T-1h reminder.
 * - [repeatDays]: repeat step in days when completed. null/0 = no repeat.
 *   1 = daily, 7 = weekly, 30 = monthly (approx). On completion such a todo rolls forward
 *   to its next occurrence instead of being archived.
 * - [archived]: completed tasks are archived (hidden from the active list).
 * - [deletedAt]: **目前未使用**。0.9.3 一度把删除做成"回收站"（软删除 + 30 天保留），
 *   zuige 拍板不要——删除只要一次二次确认就够了。列和 DB 版本号保留在这里，
 *   是为了避免已经装过那一版 0.9.3 的设备发生**数据库降级**（Room 会直接抛异常）。
 *   代码里没有任何地方写它，恒为 null。
 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    @ColumnInfo(defaultValue = "") val detail: String = "",
    val dueAtMillis: Long? = null,
    /**
     * 时间段的开始日（epoch 毫秒，按 AppTime.ZONE 落在当天）。
     * null = 单日待办，行为与旧版完全一致；非 null 时 [dueAtMillis] 的语义是「最后一天」，
     * 逾期判定仍只看 [dueAtMillis]（见 Format.isOverdue）。
     */
    val startAtMillis: Long? = null,
    val hasExactTime: Boolean = false,

    @ColumnInfo(defaultValue = "0") val repeatDays: Int = 0,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val completedAt: Long? = null,
    /** 见类注释：未使用的历史列，只为保住 DB 版本号。 */
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null
)