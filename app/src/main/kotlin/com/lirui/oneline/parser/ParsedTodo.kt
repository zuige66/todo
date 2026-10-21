package com.lirui.oneline.parser

import java.time.LocalDate
import java.time.LocalTime

/**
 * Result of parsing one input sentence.
 *
 * @param title  the description with date/time tokens removed (trimmed)
 * @param dueDate  the parsed due date; null if no date token was given (=> no-date group)
 * @param forceDateError  when true, a date token was recognized but resolved to a past date
 *                        and should not be used. This is an *error* per DESIGN §3.2 / user decision.
 * @param dueTime  the parsed time-of-day; null if only a date was given (=> default 09:00)
 * @param repeatDays  repeat step in days when the item is completed. null/0 = no repeat.
 *                    1 = daily, 7 = weekly, 28/30 ≈ monthly. Driven by 每天/每周/每月/每周X.
 * @param detail  the free text after the event, i.e. everything after the first space in the
 *                remainder ("组会 要带笔记本去" -> title 组会, detail 要带笔记本去).
 */
data class ParsedTodo(
    val title: String,
    val dueDate: LocalDate?,       // null => no-date item
    val dueTime: LocalTime?,       // null => no explicit time
    val repeatDays: Int = 0,       // 0 = no repeat
    val timeError: Boolean = false, // true if a recognizable "time-like" token was past/impossible
    val dateError: Boolean = false,  // true if a date was recognized but already past
    val detail: String? = null      // free-text detail after the event title
) {
    /** The sanitized remainder after removing recognized tokens. */
    val cleanTitle: String get() = title.trim()

    val hasExactTime: Boolean get() = dueTime != null
}