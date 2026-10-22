package com.lirui.oneline.parser

import com.lirui.oneline.util.AppTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.regex.Pattern

/**
 * Offline, rule-based natural-language parser for one Chinese sentence.
 *
 * Chinese "one-sentence" input is mostly space-less, so date/time expressions are
 * extracted as *leading* prefixes of each whitespace/punct-delimited token and then
 * stripped from the resulting title (e.g. `今天开会` -> dueDate=today, title=开会;
 * `明天下午4点 交周报` -> date/tomorrow + time/16:00, title=交周报).
 *
 * Decision set (DESIGN §3.2 / §9):
 * - current-week weekday already past this week => dateError
 * - 下周X / 下个星期X / 下礼拜X => next week
 * - numeric short dates M.D / YY.M.D / YYYY.M.D accept . - / separators; 2-digit year -> 20yy
 * - any recognized date resolved to the past => dateError
 * - year/month keywords: 今年 / 明年 / 下一年 / 从这个月 (本月/下个月) (+ optional X月/X日)
 * - times: HH:mm, N点, N点半, N点一刻, N点三刻, N点M(分), with day-period prefixes
 * - night words (今晚/明晚/后晚/今夜...) imply a 12h->24h shift for a following bare hour
 * - **a time with no date** ("早上开会" / "3点开会") resolves to today when that clock is still
 *   ahead, otherwise tomorrow. Without this it stayed dateless and landed under 无期.
 * - a time that is already *past* **and explicitly dated today** => timeError
 *   (an explicit "今天" is never silently moved; only the implicit case rolls forward)
 */
class Parser(
    /** Optional fixed reference date for deterministic tests; null = read fresh each parse. */
    private val fixedToday: LocalDate? = null,
    /** Optional fixed "now" time for deterministic tests; null = read fresh each parse. */
    private val fixedNow: LocalTime? = null
) {
    // Read on demand so a long-lived Parser (reused by the ViewModel all session) always
    // compares against the *current* wall clock, not the moment it was constructed.
    // Otherwise "今晚8点" typed after 8pm wouldn't flag timeError because `now` is stale.
    private var today: LocalDate = fixedToday ?: AppTime.today()
    private var now: LocalTime = fixedNow ?: AppTime.now()

    // ---------- date ----------

    private val relative = mapOf(
        "今天" to 0L, "今日" to 0L, "今儿" to 0L, "今晚" to 0L, "今夜" to 0L,
        "明天" to 1L, "明日" to 1L, "明早" to 1L, "明晚" to 1L,
        "后天" to 2L, "后日" to 2L, "后晚" to 2L, "大后天" to 3L
    )

    /** Relative day words whose meaning is "this/that night" => a bare hour after it is PM. */
    private val nightRelatives = setOf("今晚", "今夜", "明晚", "后晚")

    // current-week weekday literals -> DayOfWeek
    private val weekdayLiterals: List<Pair<String, DayOfWeek>> = run {
        val sets = listOf(
            listOf("周一", "星期一", "礼拜一", "周1"),
            listOf("周二", "星期二", "礼拜二", "周2"),
            listOf("周三", "星期三", "礼拜三", "周3"),
            listOf("周四", "星期四", "礼拜四", "周4"),
            listOf("周五", "星期五", "礼拜五", "周5"),
            listOf("周六", "星期六", "礼拜六", "周6"),
            listOf("周天", "周日", "星期天", "礼拜天", "周7")
        )
        sets.flatMapIndexed { i, forms -> forms.map { it to DayOfWeek.of(i + 1) } }
            .distinctBy { it.first }
            .sortedByDescending { it.first.length }
    }

    // next-week prefixes, longest first
    private val nextWeekPrefixes = listOf(
        "下下周", "下下个星期", "下个星期", "下星期", "下礼拜", "下个礼拜", "下周", "下个周"
    )

    // year / month anchor keywords, longest first
    private val yearMonthKeywords = listOf("下一年", "下个月", "明年", "今年", "本月")

    // numeric date prefixes (lookingAt; no trailing anchor so a trailing title stays)
    // `(?:[日号])?` is required: "2026年10月24日开会" would otherwise leave a stray "日"
    // at the head of the title ("日开会").
    private val num3Re = Pattern.compile("^((?:19|20)?\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})(?:[日号])?")
    private val absYearRe =
        Pattern.compile("^((?:19|20)\\d{2})[年\\-/.](\\d{1,2})[月\\-/.](\\d{1,2})(?:[日号])?")
    private val monthDayRe = Pattern.compile("^(\\d{1,2})[月\\-/.](\\d{1,2})(?:日|号)?")
    private val dayOnlyRe = Pattern.compile("^(\\d{1,2})(?:日|号)")
    private val monthOnlyRe = Pattern.compile("^(\\d{1,2})月")

    // ---------- time ----------

    /**
     * Day-period words used as a *bare* time expression (no "点"), mapped to the
     * conventional default hour: 早上/上午 = 08:00, 中午 = 12:00, 下午 = 14:00,
     * 晚上/傍晚 = 18:00. e.g. "周日早上组会" -> 08:00 + title 组会.
     */
    private val periodDefaults = linkedMapOf(
        "早上" to 8, "早晨" to 8, "上午" to 8,
        "中午" to 12,
        "下午" to 14,
        "傍晚" to 18, "晚上" to 18
    )

    // Optional day-period in front of a clock: "晚上11:50" must read as 23:50, not as
    // a bare "晚上" (18:00) plus a leftover "11:50" in the title.
    private val clockBody =
        "(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|半夜)?(\\d{1,2})\\s*[:：](\\d{1,2})"
    private val clockRe = Pattern.compile("^$clockBody")
    // Same expression without the ^ anchor: used to find a time buried inside a token.
    private val clockFindRe = Pattern.compile(clockBody)

    // 小时既支持 ASCII 数字（3点/15点）也支持中文数字（两点/十二点）。
    // 分钟支持 半/一刻/三刻、ASCII 数字（05/15分）、中文数字（十分/三十分）。
    private val hourBody =
        "(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|半夜)?" +
            "(\\d{1,4}|[一两二三四五六七八九十]{1,2})\\s*点(?:\\s*" +
            "(半|一刻|三刻|\\d{1,2}\\s*分?|[一两二三四五六七八九十]{1,3}分))?"
    private val hourRe = Pattern.compile("^$hourBody")
    // Unanchored variant: "开会晚上9点" must still yield 21:00.
    private val hourFindRe = Pattern.compile(hourBody)

    // ---------- result ----------

    private data class Acc(
        var title: MutableList<String> = ArrayList(),
        var date: LocalDate? = null,
        var time: LocalTime? = null,
        var repeatDays: Int = 0,
        var dateError: Boolean = false,
        var timeError: Boolean = false,
        var night: Boolean = false,
        /** A bare period word seen in an earlier token, waiting for a "N点" in the next one. */
        var pendingPeriod: String? = null
    ) {
        /** Assign a parsed date, flagging it as an error when it lies before [referenceToday]. */
        fun setDate(d: LocalDate?, referenceToday: LocalDate) {
            date = d
            if (d != null && d.isBefore(referenceToday)) dateError = true
        }
    }

    fun parse(input: String): ParsedTodo {
        if (input.isBlank()) return ParsedTodo("", null, null, 0, false, false)

        // Refresh the clock each call so a long-lived parser stays current with the
        // user's wall clock (fixes stale-`now` failing to flag a just-past today-time).
        refreshClock()

        val acc = Acc()
        for (token in splitTokens(input)) {
            var rest = stripLeadingDate(token, acc)
            rest = stripLeadingTime(rest, acc)
            if (rest.isNotBlank()) acc.title += rest
        }

        // ---- 只有时间、没有日期 -> 推断"下一次这个钟点" ----
        // "早上开会" / "3点开会" 这类输入只给了时间。此前 dueDate 一直是 null，
        // 于是 repository.parsedToDue() 直接返回 null，待办存成"无期"；可预览卡片
        // 又会显示时间芯片 —— 用户看到"8点"，落库却是无日期，认知完全错位。
        // 规则：这个钟点今天还没到就是今天，已经过了就是明天（"早上"在 9 点说 = 明早）。
        val parsedTime = acc.time
        if (parsedTime != null && acc.date == null) {
            acc.setDate(
                if (parsedTime.isBefore(now)) today.plusDays(1) else today,
                today
            )
        }

        // A time that lies before "now" on today is inherently impossible -> timeError.
        val theTime = acc.time
        var dueDate = acc.date
        var todayTimePast = dueDate == today && theTime != null && theTime.isBefore(now)

        // A REPEATING todo is never "in the past": roll its first occurrence forward
        // instead of rejecting it ("每天晚上" typed at 22:00 must start tomorrow 18:00).
        if (todayTimePast && acc.repeatDays > 0 && dueDate != null && theTime != null) {
            dueDate = rollToFuture(acc.repeatDays, dueDate, theTime)
            todayTimePast = false
        }

        // Space-separated remainder: the FIRST chunk is the event itself, everything after
        // it is the free-text detail ("周六下午3点组会 要带笔记本去" -> 组会 / 要带笔记本去).
        val parts = acc.title.map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedTodo(
            title = parts.firstOrNull().orEmpty(),
            dueDate = dueDate,
            dueTime = acc.time,
            repeatDays = acc.repeatDays,
            timeError = acc.timeError || todayTimePast,
            dateError = acc.dateError,
            detail = parts.drop(1).joinToString(" ").trim().ifBlank { null }
        )
    }

    /**
     * Parse a sentence that mentions SEVERAL dates ("明天后天晚上开会") into one todo per
     * date, sharing the same time / title / detail / repeat.
     *
     * Falls back to [parse] whenever at most one date is present, so every single-date
     * behavior (dateError, timeError, repeat rollover) stays exactly as before.
     */
    fun parseAll(input: String): List<ParsedTodo> {
        if (input.isBlank()) return listOf(parse(input))
        refreshClock()

        val dates = LinkedHashSet<LocalDate>()
        val acc = Acc()
        for (token in splitTokens(input)) {
            var rest = token
            if (repeatDays(token) != null) {
                // A repeat word ("每天/每周一") is a step, not a second due date.
                rest = stripLeadingDate(rest, acc)
            } else {
                var guard = 0
                while (guard++ < 4) {
                    val before = rest
                    rest = stripLeadingDate(rest, acc)
                    if (rest == before) break
                    acc.date?.let { dates += it }
                }
            }
            rest = stripLeadingTime(rest, acc)
            if (rest.isNotBlank()) acc.title += rest
        }
        if (dates.size <= 1) return listOf(parse(input))

        val parts = acc.title.map { it.trim() }.filter { it.isNotEmpty() }
        val title = parts.firstOrNull().orEmpty()
        val detail = parts.drop(1).joinToString(" ").trim().ifBlank { null }
        val time = acc.time

        return dates.sorted().map { d ->
            ParsedTodo(
                title = title,
                dueDate = d,
                dueTime = time,
                repeatDays = acc.repeatDays,
                timeError = d == today && time != null && time.isBefore(now),
                dateError = d.isBefore(today),
                detail = detail
            )
        }
    }

    /** Advance [from] by [stepDays] until the date+time is in the future. */
    private fun rollToFuture(stepDays: Int, from: LocalDate, time: LocalTime): LocalDate {
        val step = stepDays.coerceAtLeast(1).toLong()
        var d = from
        var guard = 0
        while ((d.isBefore(today) || (d == today && time.isBefore(now))) && guard < 400) {
            d = d.plusDays(step)
            guard++
        }
        return d
    }

    private fun refreshClock() {
        if (fixedToday == null) today = AppTime.today()
        if (fixedNow == null) now = AppTime.now()
    }

    private fun splitTokens(input: String): List<String> =
        input.trim().split(Regex("[\\s，,。、　&；;·]+")).filter { it.isNotBlank() }

    /** Remove one *leading* date expression from [rest]; mutate [acc]; return the remainder. */
    private fun stripLeadingDate(rest: String, acc: Acc): String {
        // 0) repeat prefixes (每天/每周/每月/每周X). These establish the repeat step and also
        //    set the effective due date (to avoid "no date" for a repeating item).
        repeatDays(rest)?.let { rep ->
            acc.repeatDays = rep.days
            acc.setDate(nextOccurrence(rep.days, rep.anchor ?: today), today)
            if (rep.anchor != null) {
                // 每周一 ... -> keep day-of-week as part of title? The day word becomes the due
                // date; drop it so it isn't duplicated. Title keeps the remainder.
                val remain = rest.removePrefix(rep.match).trimStart()
                if (remain.isNotEmpty()) return remain
            }
            return rest.removePrefix(rep.match).trimStart()
        }

        // 1) relative words
        relative.entries.sortedByDescending { it.key.length }.forEach { (key, off) ->
            if (rest.startsWith(key)) {
                acc.setDate(today.plusDays(off), today)
                if (key in nightRelatives) acc.night = true
                return rest.substring(key.length)
            }
        }

        // 1.5) year / month keywords (今年 / 明年 / 下一年 / 本月 / 下个月 [+ X月 + X日])
        val ymKey = yearMonthKeywords.firstOrNull { rest.startsWith(it) }
        if (ymKey != null) {
            val remain = rollYearMonth(ymKey, rest, acc)
            if (remain != null) return remain
            // No concrete date (e.g. bare 今年/明年) -> fall through and keep text.
        }

        // 2) 下周X...
        for (prefix in nextWeekPrefixes) {
            if (rest.startsWith(prefix)) {
                val day = weekdayChar(rest.getOrNull(prefix.length) ?: ' ')
                if (day != null) {
                    val nextMonday = today.plusDays((8 - today.dayOfWeek.value).toLong())
                    acc.setDate(nextMonday.plusDays((day.value - 1).toLong()), today)
                    return rest.substring(prefix.length + 1)
                }
                break
            }
        }

        // 3) current-week weekday literal
        for ((key, dow) in weekdayLiterals) {
            if (rest.startsWith(key)) {
                acc.setDate(today.with(dow), today)
                return rest.substring(key.length)
            }
        }

        // 4) numeric / absolute dates
        num3Re.matcher(rest).let { m ->
            if (m.lookingAt()) {
                val yearStr = m.group(1)!!
                val year = when (yearStr.length) {
                    2 -> if (yearStr.toInt() < 70) 2000 + yearStr.toInt() else 1900 + yearStr.toInt()
                    else -> yearStr.toInt()
                }
                acc.setDate(safeDate(year, m.group(2)!!.toInt(), m.group(3)!!.toInt()), today)
                return rest.substring(m.end())
            }
        }
        absYearRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                acc.setDate(safeDate(m.group(1)!!.toInt(), m.group(2)!!.toInt(), m.group(3)!!.toInt()), today)
                return rest.substring(m.end())
            }
        }
        monthDayRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                acc.setDate(safeDate(today.year, m.group(1)!!.toInt(), m.group(2)!!.toInt()), today)
                return rest.substring(m.end())
            }
        }
        dayOnlyRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                acc.setDate(safeDate(today.year, today.monthValue, m.group(1)!!.toInt()), today)
                return rest.substring(m.end())
            }
        }

        return rest
    }

    /**
     * Handle year/month keyword prefixes. Returns the remainder of [rest] after the
     * keyword (and any optional `X月` / `X日` suffix), or null when no concrete date
     * could be formed (e.g. bare `今年` / `明年` with no month).
     */
    private fun rollYearMonth(key: String, rest: String, acc: Acc): String? {
        var year = today.year
        var month: Int? = null

        when (key) {
            "下一年", "明年" -> year = today.year + 1
            "今年" -> {}
            "下个月" -> {
                val nm = today.plusMonths(1)
                year = nm.year
                month = nm.monthValue
            }
            "本月" -> month = today.monthValue
        }

        var s = rest.substring(key.length)

        // optional "X月"
        monthOnlyRe.matcher(s).let { m ->
            if (m.lookingAt()) {
                month = m.group(1)!!.toInt()
                s = s.substring(m.end())
            }
        }
        // optional "X日" / "X号"
        var day: Int? = null
        dayOnlyRe.matcher(s).let { m ->
            if (m.lookingAt()) {
                day = m.group(1)!!.toInt()
                s = s.substring(m.end())
            }
        }

        val m = month ?: return null // no concrete date (bare 今年/明年) -> not a date
        val y = year
        val lastDay = LocalDate.of(y, m, 1).lengthOfMonth()
        val d = if (day != null) day.coerceIn(1, lastDay) else today.dayOfMonth.coerceAtMost(lastDay)

        acc.setDate(safeDate(y, m, d), today)
        return s
    }

    /** Remove one time expression from [rest]; mutate [acc]; return the remainder. */
    private fun stripLeadingTime(rest: String, acc: Acc): String {
        clockRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                acc.time = clockMatchTime(m, acc)
                return rest.substring(m.end())
            }
        }
        hourRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                acc.time = hourMatchTime(m, acc)
                return rest.substring(m.end())
            }
        }

        // Bare day-period without a clock: "早上组会" / "下午 开会" -> its default hour.
        for ((word, hour) in periodDefaults) {
            if (rest.startsWith(word)) {
                acc.pendingPeriod = word
                acc.time = LocalTime.of(hour, 0)
                acc.night = false
                return rest.substring(word.length)
            }
        }

        // Chinese input often runs words together, so the time may sit *inside* the token
        // instead of at its start: "开会晚上" (18:00) / "开会晚上9点" (21:00).
        embeddedTime(rest)?.let { e ->
            acc.time = e.time
            acc.night = false
            acc.pendingPeriod = null
            return (rest.substring(0, e.start) + rest.substring(e.end)).trim()
        }
        return rest
    }

    private data class EmbeddedTime(val start: Int, val end: Int, val time: LocalTime)

    /**
     * Find a clock or day-period that starts after the first character of [token].
     * A rightmost bare period wins ("下午开会晚上" -> 晚上/18:00), since the trailing
     * word is usually the intended time.
     */
    private fun embeddedTime(token: String): EmbeddedTime? {
        clockFindRe.matcher(token).let { m ->
            while (m.find()) {
                if (m.start() > 0) {
                    return EmbeddedTime(m.start(), m.end(), clockMatchTime(m, Acc()))
                }
            }
        }
        hourFindRe.matcher(token).let { m ->
            while (m.find()) {
                if (m.start() > 0) {
                    return EmbeddedTime(m.start(), m.end(), hourMatchTime(m, Acc()))
                }
            }
        }
        var best: EmbeddedTime? = null
        for ((word, hour) in periodDefaults) {
            val i = token.lastIndexOf(word)
            if (i > 0 && (best == null || i > best.start)) {
                best = EmbeddedTime(i, i + word.length, LocalTime.of(hour, 0))
            }
        }
        return best
    }

    /** Turn a clockRe match (groups: period, hour, minute) into a concrete time. */
    private fun clockMatchTime(m: java.util.regex.Matcher, acc: Acc): LocalTime {
        val prefix = m.group(1)
        val rawHour = m.group(2)!!.toInt()
        val minute = m.group(3)!!.toInt()
        val period = prefix ?: acc.pendingPeriod
        var hour = if (period != null) applyPeriod(period, rawHour) else rawHour
        // 晚上12:xx rolls past midnight -> next day.
        if (hour >= 24) {
            acc.date?.let { acc.setDate(it.plusDays(1), today) }
        }
        hour %= 24
        acc.night = false
        acc.pendingPeriod = null
        return LocalTime.of(hour, minute)
    }

    /** Turn an hourRe match (groups: period, hour, minute-suffix) into a concrete time. */
    private fun hourMatchTime(m: java.util.regex.Matcher, acc: Acc): LocalTime {
        val prefix = m.group(1)
        var hour = m.group(2)!!.let { chineseToInt(it) ?: it.toInt() }
        val minute = m.group(3)?.let {
            when {
                it == "半" -> 30
                it == "一刻" -> 15
                it == "三刻" -> 45
                it.endsWith("分") -> {
                    // ASCII 数字分钟（15分/05分）或中文数字分钟（十五分/三十分）。
                    val digits = it.removeSuffix("分").trim()
                    chineseNumber(digits) ?: digits.toIntOrNull() ?: 0
                }
                it.isNotBlank() -> it.trim().toIntOrNull() ?: 0
                else -> 0
            }
        } ?: 0

        if (prefix != null) {
            hour = applyPeriod(prefix, hour)
        } else if (acc.pendingPeriod != null) {
            // "下午 3点" arrives as two tokens: the bare period set the default,
            // then this bare hour must be shifted into that period.
            hour = applyPeriod(acc.pendingPeriod!!, hour)
        } else if (acc.night && hour in 1..11) {
            // e.g. 今晚9点 / 明晚8点 -> a bare hour after a "night" day word is PM.
            hour += 12
        }
        acc.night = false
        acc.pendingPeriod = null
        return LocalTime.of(hour % 24, minute)
    }

    /** Apply a day-period prefix to a 12h hour, yielding the 24h hour. */
    private fun applyPeriod(period: String, hour: Int): Int = when (period) {
        "下午", "傍晚", "晚上", "夜里", "半夜" -> if (hour in 1..12) hour + 12 else hour
        "凌晨" -> if (hour == 12) 0 else hour
        "中午" -> if (hour in 1..11) 12 else hour
        "上午", "早上", "早晨" -> if (hour == 12) 0 else hour
        else -> hour
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try { LocalDate.of(y, m, d) } catch (_: Exception) { null }

    private data class Repeat(val match: String, val days: Int, val anchor: LocalDate?)

    /** Detect a leading repeat prefix: 每天/每日(1), 每周/每个星期(7), 每周X(7, anchored), 每月(≈30). */
    private fun repeatDays(rest: String): Repeat? {
        // 每周一 ~ 每周日 / 每星期一 ...
        for ((key, dow) in weekdayLiterals) {
            if (rest.startsWith("每" + key, ignoreCase = false)) {
                val anchor = nextWeekday(dow)
                return Repeat("每" + key, 7, anchor)
            }
        }
        return when {
            rest.startsWith("每天") || rest.startsWith("每日") -> Repeat(if (rest.startsWith("每天")) "每天" else "每日", 1, today)
            rest.startsWith("每周") || rest.startsWith("每个星期") || rest.startsWith("每个礼拜") -> {
                val m = when {
                    rest.startsWith("每周") -> "每周"
                    rest.startsWith("每个星期") -> "每个星期"
                    else -> "每个礼拜"
                }
                Repeat(m, 7, today)
            }
            rest.startsWith("每月") || rest.startsWith("每个月") -> {
                val m = if (rest.startsWith("每月")) "每月" else "每个月"
                Repeat(m, 30, today)
            }
            else -> null
        }
    }

    /** The coming weekday at-or-after [today] (today if it's that weekday, else next week). */
    private fun nextWeekday(dow: DayOfWeek): LocalDate {
        val cur = today.dayOfWeek.value
        val target = dow.value
        val offset = if (target >= cur) target - cur else target - cur + 7
        return today.plusDays(offset.toLong())
    }

    /** First due occurrence given a repeat step — today unless already past, then next step. */
    private fun nextOccurrence(days: Int, anchor: LocalDate): LocalDate {
        var d = anchor
        // Roll strictly forward to a not-past date.
        if (days > 0) {
            var guard = 0
            while (d.isBefore(today) && guard < 366) {
                d = d.plusDays(days.toLong())
                guard++
            }
        }
        return d
    }

    /** Map a Chinese numeral hour (一两..十二) to an Int, or null for non-Chinese text. */
    private fun chineseToInt(s: String): Int? = when (s) {
        "零" -> 0
        "一" -> 1
        "二", "两" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "七" -> 7
        "八" -> 8
        "九" -> 9
        "十" -> 10
        "十一" -> 11
        "十二" -> 12
        else -> null
    }

    /** Parse an arbitrary Chinese numeral (零..九十九) such as 十五 / 二十 / 三十五. */
    private fun chineseNumber(s: String): Int? {
        if (s.isEmpty()) return null
        val digit = { c: Char -> "零一二三四五六七八九".indexOf(c).takeIf { it >= 0 } }
        return when {
            s.length == 1 -> digit(s[0])
            s == "十" -> 10
            s.length == 2 && s[1] == '十' -> {
                // 二十 / 三十 / 四十 …
                val d = digit(s[0]) ?: return null
                d * 10
            }
            s.length == 2 && s[0] == '十' && s[1] != '十' -> {
                // 十一 / 十二 / 十五 …
                val d = digit(s[1]) ?: return null
                10 + d
            }
            s.length == 3 && s[1] == '十' -> {
                // 二十五 / 三十五 …
                val tens = digit(s[0]) ?: return null
                val ones = digit(s[2]) ?: return null
                tens * 10 + ones
            }
            else -> null
        }
    }

    private fun weekdayChar(c: Char): DayOfWeek? = when (c) {
        '一', '1' -> DayOfWeek.MONDAY
        '二', '2' -> DayOfWeek.TUESDAY
        '三', '3' -> DayOfWeek.WEDNESDAY
        '四', '4' -> DayOfWeek.THURSDAY
        '五', '5' -> DayOfWeek.FRIDAY
        '六', '6' -> DayOfWeek.SATURDAY
        '日', '天', '7' -> DayOfWeek.SUNDAY
        else -> null
    }
}