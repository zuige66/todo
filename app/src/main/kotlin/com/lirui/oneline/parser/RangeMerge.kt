package com.lirui.oneline.parser

import java.time.LocalDate

/**
 * 把「一句多日期」解析出的多条待办合并成**一条时间段待办**。
 *
 * 背景（zuige 2026-10-24）：输入「24号25号 出差」默认解析成两条单日待办，
 * 这符合"每天都要被提醒"的语义；但用户想要的常常是"这一段时间都在出差"。
 * 在输入面板加「合并为时间段」开关，合并逻辑全部收敛在这里 —— UI 只负责切换。
 *
 * 规则：
 * - 至少两条、且**每条都有日期**才允许合并（[canMerge]）。有无日期的条目混在中间时
 *   不合并：时间段的起止必须都明确，硬合并会造出没有 dueAtMillis 的待办（列表里直接消失）。
 * - 起止取所有日期的**最小/最大**，不按出现顺序 —— 「25号 24号」也要得到 24→25，
 *   而不是 start > due 的倒挂区间（[com.lirui.oneline.data.TodoRepository] 的
 *   normalizeRange 会再对调一次，但起点就对比这里更好）。
 * - 时间/详情/重复取**第一条**的：一句话里写多个时刻本就歧义，第一条是主句。
 * - dateError / timeError 用 any 传递：任一条踩线，合并后照样报错，不让合并成为
 *   「绕过已过日期校验」的后门。
 */
object RangeMerge {

    /** 是否显示/允许「合并为时间段」。 */
    fun canMerge(list: List<ParsedTodo>): Boolean =
        list.size >= 2 && list.all { it.dueDate != null }

    /**
     * 合并成一条时间段待办。
     *
     * 不满足 [canMerge] 时**原样返回**（不是抛异常）—— 调用方（UI）只在
     * canMerge 为真时才调用，这里是防御性兜底。
     */
    fun merge(list: List<ParsedTodo>): List<ParsedTodo> {
        if (!canMerge(list)) return list
        val dates: List<LocalDate> = list.mapNotNull { it.dueDate }.sorted()
        val base = list.first()
        val merged = base.copy(
            dueDate = dates.last(),
            startDate = dates.first(),
            dateError = list.any { it.dateError },
            timeError = list.any { it.timeError }
        )
        return listOf(merged)
    }
}
