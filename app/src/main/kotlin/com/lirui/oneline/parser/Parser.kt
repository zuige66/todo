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
 * - 下周X / 下个星期X / 下礼拜X => next week; **下下周X => +2 weeks** (not +1)
 * - 本周X / 这周X / 这个星期X => same week as a bare 周X (the prefix is just swallowed)
 * - full-width digits / colon / dot are normalised to ASCII before tokenising (８：３０ -> 8:30)
 * - numeric short dates M.D / YY.M.D / YYYY.M.D accept . - / separators; 2-digit year -> 20yy
 * - **date digits may be Chinese numerals** (一~三十一): `1月二日` / `十二月三十一号` / `二号`
 * - explicit year (2026年1月2日 / 26.1.2) resolved to the past => dateError
 * - **no year given** (1月2日 / 5号) resolved to the past => roll forward silently
 *   (next year for 月日, next month for 日号) — never a dateError
 * - any other recognized date (weekday / keyword-anchored) resolved to the past => dateError
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
            // 周日这一组要同时收中文数字「七」：'周7' 有人打，'周七' 也有人打
            listOf("周天", "周日", "周七", "星期七", "星期天", "礼拜天", "周7")
        )
        sets.flatMapIndexed { i, forms -> forms.map { it to DayOfWeek.of(i + 1) } }
            .distinctBy { it.first }
            .sortedByDescending { it.first.length }
    }

    // next-week prefixes, longest first
    private val nextWeekPrefixes = listOf(
        "下下个星期", "下下个礼拜", "下下周",
        "下个星期", "下星期", "下个礼拜", "下礼拜", "下个周", "下周"
    )

    /**
     * "本周/这周" 前缀。它们是**同一周**，解析结果与裸「周X」完全一致，
     * 存在的意义只是别让「本」「这」两个字留在标题里。
     */
    private val thisWeekPrefixes = listOf(
        "这个星期", "这个礼拜", "这个周", "本星期", "本礼拜", "这星期", "这礼拜", "本周", "这周"
    )

    // year / month anchor keywords, longest first
    private val yearMonthKeywords = listOf("下一年", "下个月", "下月", "明年", "今年", "本月")

    /**
     * 日期里的"数字位"：ASCII 1-2 位，或中文数字（一 / 十二 / 三十一）。
     *
     * 「1月二日」「十二月三十一号」这类中文数字写法在日常输入里非常常见。之前只认 `\d`，
     * `1月二日` 会在 `1` + `月` 之后卡在 `二` 上，整条正则匹配失败 —— 日期一个字段都没解析
     * 出来，原样掉进标题，用户看到的就是"根本没识别"。
     *
     * 故意不写成 `(?:...)`：调用处统一用 `($numPat)` 包一层拿捕获组，组号才不会错位。
     */
    private val numPat = "\\d{1,2}|[一二三四五六七八九十]{1,3}"

    // numeric date prefixes (lookingAt; no trailing anchor so a trailing title stays)
    // `(?:[日号])?` is required: "2026年10月24日开会" would otherwise leave a stray "日"
    // at the head of the title ("日开会").
    private val num3Re = Pattern.compile("^((?:19|20)?\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})(?:[日号])?")
    private val absYearRe =
        Pattern.compile("^((?:19|20)\\d{2})[年\\-/.]($numPat)[月\\-/.]($numPat)(?:[日号])?")
    private val monthDayRe = Pattern.compile("^($numPat)[月\\-/.]($numPat)(?:[日号])?")
    private val dayOnlyRe = Pattern.compile("^($numPat)(?:[日号])")
    private val monthOnlyRe = Pattern.compile("^($numPat)月")

    // ---------- time ----------

    /**
     * Day-period words used as a *bare* time expression (no "点"), mapped to the
     * conventional default hour: 凌晨 = 01:00, 半夜 = 00:00, 清晨/早上/上午 = 08:00,
     * 中午 = 12:00, 下午 = 14:00, 傍晚/晚上 = 18:00, 夜里 = 22:00.
     * e.g. "周日早上组会" -> 08:00 + title 组会.
     *
     * 凌晨/半夜/夜里/清晨 四个是审计（DESIGN §28.2）补的：此前只覆盖 早上~晚上，
     * 单独写「凌晨开会」会整段留在标题里。
     */
    private val periodDefaults = linkedMapOf(
        "半夜" to 0, "凌晨" to 1, "清晨" to 6,
        "早晨" to 8, "早上" to 8, "上午" to 8,
        "中午" to 12,
        "下午" to 14,
        "傍晚" to 18, "晚上" to 18,
        "夜里" to 22
    )

    // Optional day-period in front of a clock: "晚上11:50" must read as 23:50, not as
    // a bare "晚上" (18:00) plus a leftover "11:50" in the title.
    private val clockBody =
        "(凌晨|清晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|半夜)?(\\d{1,2})\\s*[:：](\\d{1,2})"
    private val clockRe = Pattern.compile("^$clockBody")
    // Same expression without the ^ anchor: used to find a time buried inside a token.
    private val clockFindRe = Pattern.compile(clockBody)

    // 小时既支持 ASCII 数字（3点/15点）也支持中文数字（两点/十二点/二十三点）。
    // 中文位数放宽到 3 位：`{1,2}` 拼不出「二十五」，会在 embedded 阶段错取成「十五」。
    // 加「零」是为了让「零点」成立（此前整段留标题）。
    // 分钟支持 半/一刻/三刻、ASCII 数字（05/15分）、中文数字（十分/三十分）。
    private val hourBody =
        "(凌晨|清晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|半夜)?" +
            "(\\d{1,4}|[零一两二三四五六七八九十]{1,3})\\s*点(?:\\s*" +
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
        for (token in splitTokens(normalize(input))) {
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
        for (token in splitTokens(normalize(input))) {
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

    /**
     * 全角 -> 半角归一。
     *
     * 中文输入法很容易打出「８：３０」「２０２６年」「８月８号」「１５点」，
     * 而所有正则都基于 ASCII `\d`，不归一就会整段进标题（审计 §28.2）。
     * 只在真的含全角字符时才建新字符串，正常输入零开销。
     */
    private fun normalize(input: String): String {
        if (input.none { it in '０'..'９' || it in "：．－—–／　" }) return input
        return buildString(input.length) {
            for (c in input) {
                append(
                    when (c) {
                        in '０'..'９' -> '0' + (c - '０')
                        '：' -> ':'
                        '．' -> '.'
                        '－', '—', '–' -> '-'
                        '／' -> '/'
                        '　' -> ' '
                        else -> c
                    }
                )
            }
        }
    }

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

        // 2) 下周X / 下下周X
        for (prefix in nextWeekPrefixes) {
            if (rest.startsWith(prefix)) {
                val day = weekdayChar(rest.getOrNull(prefix.length) ?: ' ')
                if (day != null) {
                    // "下下周" 是两周后。此前所有前缀都按 +0 周算，`下下周三` 给出的其实是
                    // 下周三 —— 认了关键词却给错日期，比"不识别"更危险（审计 §28.2）。
                    val weeksAhead = if (prefix.startsWith("下下")) 2L else 1L
                    val nextMonday = today.plusDays((8 - today.dayOfWeek.value).toLong())
                    acc.setDate(
                        nextMonday.plusDays(7L * (weeksAhead - 1) + (day.value - 1).toLong()),
                        today
                    )
                    return rest.substring(prefix.length + 1)
                }
                break
            }
        }

        // 2.5) 本周X / 这周X：与裸「周X」等价，唯一作用是别把「本」「这」留在标题里
        val twPrefix = thisWeekPrefixes.firstOrNull { rest.startsWith(it) }
        if (twPrefix != null) {
            val after = rest.substring(twPrefix.length)
            val day = weekdayChar(after.firstOrNull() ?: ' ')
            if (day != null) {
                acc.setDate(today.with(day), today)
                return after.substring(1)
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
                val d = safeDate(year, m.group(2)!!.toInt(), m.group(3)!!.toInt())
                if (d != null) {
                    acc.setDate(d, today)
                    return rest.substring(m.end())
                }
            }
        }
        absYearRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                val month = numOf(m.group(2)!!)
                val day = numOf(m.group(3)!!)
                val d = if (month != null && day != null) {
                    safeDate(m.group(1)!!.toInt(), month, day)
                } else null
                if (d != null) {
                    acc.setDate(d, today)
                    return rest.substring(m.end())
                }
            }
        }
        // 只写月日 / 只写日号：**没写年份**，已过去就顺延到下一次，不报 dateError。
        // 显式写了年份的分支在上面，仍然是"过去即报错"——用户明确指定了年份就不该悄悄改。
        monthDayRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                val d = nextMonthDay(numOf(m.group(1)!!), numOf(m.group(2)!!))
                if (d != null) {
                    acc.setDate(d, today)
                    return rest.substring(m.end())
                }
            }
        }
        dayOnlyRe.matcher(rest).let { m ->
            if (m.lookingAt()) {
                val d = nextDayOfMonth(numOf(m.group(1)!!))
                if (d != null) {
                    acc.setDate(d, today)
                    return rest.substring(m.end())
                }
            }
        }

        return rest
    }

    /**
     * 「1月2日」「1月二日」这类**只写月日、没写年份**的日期。
     *
     * 今年这一天已经过去就顺延到明年（用户决策：不报错、不要求手改）。
     * 2月29日在非闰年 `LocalDate.of` 会抛异常，这里逐年往后找到下一个能成立的年份。
     */
    private fun nextMonthDay(month: Int?, day: Int?): LocalDate? {
        if (month == null || day == null) return null
        if (month !in 1..12 || day !in 1..31) return null
        var year = today.year
        repeat(9) {
            val d = safeDate(year, month, day)
            if (d != null && !d.isBefore(today)) return d
            year++
        }
        return null
    }

    /**
     * 只写「X号 / X日」：默认本月，本月的这一天已经过去就顺延到下个月。
     * 该月天数不够时取当月最后一天（"31号" 落在小月 -> 30 号），与 `rollYearMonth` 一致。
     */
    private fun nextDayOfMonth(day: Int?): LocalDate? {
        if (day == null || day !in 1..31) return null
        var first = today.withDayOfMonth(1)
        repeat(14) {
            val d = first.withDayOfMonth(day.coerceAtMost(first.lengthOfMonth()))
            if (!d.isBefore(today)) return d
            first = first.plusMonths(1)
        }
        return null
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
            "下个月", "下月" -> {
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
                month = numOf(m.group(1)!!)
                s = s.substring(m.end())
            }
        }
        // optional "X日" / "X号"
        var day: Int? = null
        dayOnlyRe.matcher(s).let { m ->
            if (m.lookingAt()) {
                day = numOf(m.group(1)!!)
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
        // 分钟必须卡到 0~59：`8:60` 会让 LocalTime.of 抛 DateTimeException，
        // 而预览是每敲一个字符就 parse 一次 —— 等于打字打到一半 App 崩溃（审计 §28.1）。
        val minute = m.group(3)!!.toInt().coerceIn(0, 59)
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
        // `numOf` 覆盖 ASCII 与中文数字一~九十九。旧的 `chineseToInt(it) ?: it.toInt()`
        // 只认到「十二」，「十三点」「二十三点」会 null 落到 toInt() 上抛
        // NumberFormatException（审计 §28.1）。
        var hour = m.group(2)!!.let { numOf(it) } ?: 0
        val minute = (m.group(3)?.let {
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
        } ?: 0).coerceIn(0, 59) // 同上：`8点70分` 不能让 LocalTime.of 崩

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
        // 与 clockMatchTime 对齐：24 点以后跨天（24点 / 晚上12点 = 次日 00:00）。
        if (hour >= 24) {
            acc.date?.let { acc.setDate(it.plusDays((hour / 24).toLong()), today) }
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
        "上午", "早上", "早晨", "清晨" -> if (hour == 12) 0 else hour
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

    /**
     * ASCII 数字或中文数字（零~九十九）-> Int；都不是则返回 null。
     *
     * 日期与时间**都**必须走这里。曾经小时用的是只认到「十二」的 `chineseToInt`，
     * 拼出「十三」后返回 null 再被 `toInt()` 硬转，直接 NumberFormatException（审计 §28.1）。
     */
    private fun numOf(s: String): Int? = s.toIntOrNull() ?: chineseNumber(s)

    /** Parse an arbitrary Chinese numeral (零..九十九) such as 十五 / 二十 / 三十五. */
    private fun chineseNumber(s: String): Int? {
        if (s.isEmpty()) return null
        // 「两」不在「零一二三…」里，但口语里"两点半""两点二十分"极常见，
        // 单列出来当 2 用（此前靠 chineseToInt 里的 "两" 分支兜着）。
        val digit = { c: Char ->
            if (c == '两') 2 else "零一二三四五六七八九".indexOf(c).takeIf { it >= 0 }
        }
        return when {
            // `十` 必须排在 `length == 1` 之前：它既是一位、又不属于 零~九，
            // 顺序反了会走到 digit('十') = null，导致「十月」「十号」全部解析失败。
            s == "十" -> 10
            s.length == 1 -> digit(s[0])
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