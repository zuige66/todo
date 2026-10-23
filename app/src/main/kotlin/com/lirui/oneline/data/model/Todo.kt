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
 * - [deletedAt]: 删除是**软删除** —— 落一个时间戳进回收站，而不是从表里抹掉。
 *   null = 没删。所有列表查询都必须带 `deletedAt IS NULL`，否则回收站里的东西会
 *   重新出现在活跃列表里（见 [com.lirui.oneline.data.TodoDao]）。
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
    /** 进回收站的时间戳；null = 不在回收站。 */
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null
)