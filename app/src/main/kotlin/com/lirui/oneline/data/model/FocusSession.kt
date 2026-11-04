package com.lirui.oneline.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一次已经结束的专注记录。
 *
 * ★ 为什么单独一张表，而不是往 `todos` 里加一列累计时长：
 *   专注功能是**完全独立**的（不绑定任何待办，见 DESIGN.md「专注」一节），
 *   把时长挂到待办上会带来两个死结 —— 删掉待办连历史时长一起没了；
 *   同一件事专注三次也不知道该记在哪一条上。历史是历史，待办是待办。
 *
 * ★ 存 `startedAtMillis` 而不是只存日期：跨午夜的那次专注（23:50 → 00:20）
 *   必须整段算在**开始那天**，否则热力图和「今日时长」会在午夜前后对不上。
 *   统计侧统一按 [com.lirui.oneline.util.AppTime.ZONE] 折算成日期，见 FocusStats。
 *
 * @param label          专注时填的说明，空串 = 没写（UI 显示「专注」）
 * @param plannedSeconds 开始前选的计划时长（25 分钟 = 1500）
 * @param actualSeconds  真正走了多久。走完 = plannedSeconds；
 *                       提前结束 = 实际经过的秒数（≥ 取消阈值才落库，见 FocusRules）
 * @param completed      是否自然走完。false = 中途结束
 */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String = "",
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val completed: Boolean,
    val createdAt: Long
)
