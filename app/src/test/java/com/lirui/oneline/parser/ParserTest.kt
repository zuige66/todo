package com.lirui.oneline.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ParserTest {

    // Fixed reference "today" so tests are deterministic: 2026-10-21 is a Wednesday.
    private val today = LocalDate.of(2026, 10, 21) // Wednesday

    private fun parser() = Parser(today)
    private fun parse(s: String) = parser().parse(s)

    // ---- basic ----
    @Test fun bareNote_keepsTitle_noDate() {
        val r = parse("买牛奶")
        assertEquals("买牛奶", r.title)
        assertNull(r.dueDate)
        assertNull(r.dueTime)
        assertFalse(r.dateError)
    }

    @Test fun title_trimmed() {
        val r = parse("  买菜  ")
        assertEquals("买菜", r.title)
    }

    // ---- relative days ----
    @Test fun today() {
        val r = parse("今天开会")
        assertEquals("开会", r.title)
        assertEquals(today, r.dueDate)
    }

    @Test fun tomorrow_defaultTimeNull() {
        val r = parse("明天交周报")
        assertEquals("交周报", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertNull(r.dueTime)
    }

    @Test fun dayAfterTomorrow() {
        val r = parse("后天考试")
        assertEquals("考试", r.title)
        assertEquals(today.plusDays(2), r.dueDate)
    }

    @Test fun strippedTitleConvertsAndWords() {
        val r = parse("明天交周报，顺便买菜")
        assertEquals("交周报", r.title)
        assertEquals("顺便买菜", r.detail)
        assertEquals(today.plusDays(1), r.dueDate)
    }

    // ---- bare day-period word (no 点) -> its conventional default hour ----
    @Test fun bareMorning_meansEight() {
        // user's exact case: 周日早上组会 must not swallow 早上 into the title
        val r = parse("周日早上组会")
        assertEquals("组会", r.title)
        assertEquals(today.with(DayOfWeek.SUNDAY), r.dueDate) // 2026-10-25
        assertEquals(LocalTime.of(8, 0), r.dueTime)
    }

    @Test fun bareNoon_meansTwelve() {
        val r = parse("明天中午吃饭")
        assertEquals("吃饭", r.title)
        assertEquals(LocalTime.of(12, 0), r.dueTime)
    }

    @Test fun bareAfternoon_meansFourteen() {
        val r = parse("明天下午开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(14, 0), r.dueTime)
    }

    @Test fun bareEvening_meansEighteen() {
        val r = parse("明天晚上跑步")
        assertEquals("跑步", r.title)
        assertEquals(LocalTime.of(18, 0), r.dueTime)
    }

    @Test fun explicitClockWinsOverPeriodDefault() {
        val r = parse("晚上8点跑步")
        assertEquals("跑步", r.title)
        assertEquals(LocalTime.of(20, 0), r.dueTime)
    }

    @Test fun splitPeriodAndHourAcrossTokens() {
        val r = parse("下午 3点 开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(15, 0), r.dueTime)
    }

    // ---- event / detail split on the first space ----
    @Test fun spaceSplitsEventAndDetail() {
        val r = parse("周六下午3点组会 要带笔记本去")
        assertEquals("组会", r.title)
        assertEquals("要带笔记本去", r.detail)
        assertEquals(LocalDate.of(2026, 10, 24), r.dueDate)
        assertEquals(LocalTime.of(15, 0), r.dueTime)
    }

    @Test fun noSpace_keepsSingleTitle_noDetail() {
        val r = parse("周六下午3点组会要带笔记本去")
        assertEquals("组会要带笔记本去", r.title)
        assertNull(r.detail)
    }

    // ---- time word embedded inside one token (Chinese input runs words together) ----
    @Test fun embeddedPeriod_atEndOfToken() {
        // user's exact case: 开会晚上 (no space before 晚上) must still parse 18:00
        val r = parse("明天，后天 开会晚上")
        assertEquals("开会", r.title)
        assertEquals(today.plusDays(2), r.dueDate) // 10/23
        assertEquals(LocalTime.of(18, 0), r.dueTime)
    }

    @Test fun embeddedPeriodWithHour_atEndOfToken() {
        val r = parse("明天开会晚上9点")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(21, 0), r.dueTime)
    }

    @Test fun embeddedBareHour_atEndOfToken() {
        val r = parse("开会3点")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(3, 0), r.dueTime)
    }

    @Test fun embeddedPeriod_afternoon() {
        val r = parse("开会下午")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(14, 0), r.dueTime)
    }

    @Test fun embeddedScan_doesNotTouchPlainText() {
        val r = parse("今天买牛奶")
        assertEquals("买牛奶", r.title)
        assertEquals(today, r.dueDate)
        assertNull(r.dueTime)
    }

    // ---- 时段词 + 冒号时钟："晚上11:50" 必须是 23:50，而不是"晚上=18:00" + 标题残留 ----
    @Test fun periodBeforeColonClock_evening11_50_is_23_50() {
        // user's exact case: 晚上11:50测试一
        val r = parse("晚上11:50测试一")
        assertEquals("测试一", r.title)
        assertEquals(LocalTime.of(23, 50), r.dueTime)
        assertNull(r.detail)
    }

    @Test fun periodBeforeColonClock_afternoon3_30_is_15_30() {
        val r = parse("明天下午3:30交周报")
        assertEquals("交周报", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(15, 30), r.dueTime)
    }

    @Test fun periodBeforeColonClock_morning7_45_is_7_45() {
        val r = parse("早上7:45起床")
        assertEquals("起床", r.title)
        assertEquals(LocalTime.of(7, 45), r.dueTime)
    }

    @Test fun periodBeforeColonClock_noon12_05_stays_12_05() {
        val r = parse("中午12:05吃饭")
        assertEquals("吃饭", r.title)
        assertEquals(LocalTime.of(12, 5), r.dueTime)
    }

    @Test fun periodBeforeColonClock_evening12_xx_rollsToNextDay() {
        // 晚上12:30 == 次日 00:30
        val r = parse("明天晚上12:30守岁")
        assertEquals("守岁", r.title)
        assertEquals(today.plusDays(2), r.dueDate)
        assertEquals(LocalTime.of(0, 30), r.dueTime)
    }

    @Test fun periodBeforeColonClock_embeddedInsideToken() {
        val r = parse("明天开会晚上11:50")
        assertEquals("开会", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(23, 50), r.dueTime)
    }

    @Test fun periodBeforeColonClock_periodAndClockSplitBySpace() {
        // 时段词与时钟跨 token：pendingPeriod 必须作用到后面的时钟上
        val r = parse("晚上 11:50 测试一")
        assertEquals("测试一", r.title)
        assertEquals(LocalTime.of(23, 50), r.dueTime)
    }

    @Test fun periodWithChineseHour_eveningEleven_is_23() {
        // user's other phrasing: 晚上十一点
        val r = parse("晚上十一点提醒我")
        assertEquals("提醒我", r.title)
        assertEquals(LocalTime.of(23, 0), r.dueTime)
    }

    // ---- several dates in one sentence -> several todos ----
    @Test fun multiDate_runTogether_yieldsTwoTodos() {
        // user's case: 明天后天晚上开会
        val r = parser().parseAll("明天后天晚上开会")
        assertEquals(2, r.size)
        assertEquals(today.plusDays(1), r[0].dueDate)
        assertEquals(today.plusDays(2), r[1].dueDate)
        assertEquals(LocalTime.of(18, 0), r[0].dueTime)
        assertEquals("开会", r[0].title)
        assertEquals("开会", r[1].title)
    }

    @Test fun multiDate_commaSeparated_yieldsTwoTodos() {
        val r = parser().parseAll("明天，后天 开会")
        assertEquals(2, r.size)
        assertEquals(today.plusDays(1), r[0].dueDate)
        assertEquals(today.plusDays(2), r[1].dueDate)
        assertEquals("开会", r[0].title)
    }

    @Test fun multiDate_singleDate_staysOne() {
        val r = parser().parseAll("明天下午3点开会")
        assertEquals(1, r.size)
        assertEquals(today.plusDays(1), r[0].dueDate)
        assertEquals(LocalTime.of(15, 0), r[0].dueTime)
    }

    @Test fun multiDate_repeatIsNotASecondDate() {
        val r = parser().parseAll("每周一开会")
        assertEquals(1, r.size)
        assertEquals(7, r[0].repeatDays)
    }

    // ---- repeating todo typed after its time must roll forward, not error ----
    @Test fun repeatDaily_eveningAfterThatTime_rollsToTomorrow() {
        val p = Parser(today, LocalTime.of(22, 0))
        val r = p.parse("每天晚上开会")
        assertFalse(r.timeError)
        assertEquals(1, r.repeatDays)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(18, 0), r.dueTime)
        assertEquals("开会", r.title)
    }

    @Test fun repeatWeekly_pastTime_rollsOneWeek() {
        // 每周五 18:00, "now" is Friday 22:00 -> next Friday.
        val fri = LocalDate.of(2026, 10, 23)
        val p = Parser(fri, LocalTime.of(22, 0))
        val r = p.parse("每周五晚上6点开会")
        assertFalse(r.timeError)
        assertEquals(7, r.repeatDays)
        assertEquals(fri.plusWeeks(1), r.dueDate)
    }

    // ---- weekday, past -> error ----
    @Test fun futureWeekday_sameWeek() {
        // today is Wed 10-21; 周五 = +2
        val r = parse("周五交材料")
        assertEquals("交材料", r.title)
        assertEquals(today.with(DayOfWeek.FRIDAY), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun pastWeekday_inThisWeek_errors() {
        // today Wed; 周一 already past this week -> error
        val r = parse("周一开会")
        assertTrue(r.dateError)
    }

    @Test fun nextWeekWeekday() {
        val r = parse("下周三开会")
        assertEquals("开会", r.title)
        // next Monday = today+(8-3)=+5 => 10-26; 周三 => 10-28
        assertEquals(LocalDate.of(2026, 10, 28), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun nextWeekday_alsoNextMondayWhenTodayMonday() {
        val r = parse("下周一交")
        assertEquals("交", r.title)
        assertEquals(LocalDate.of(2026, 10, 26), r.dueDate)
    }

    // ---- absolute & numeric ----
    @Test fun absoluteMmDd_future() {
        val r = parse("10月23日体检")
        assertEquals("体检", r.title)
        assertEquals(LocalDate.of(2026, 10, 23), r.dueDate)
        assertFalse(r.dateError)
    }

    // 没写年份的月日：已过去就顺延到明年，不再报错（用户决策 2026-10-22）
    @Test fun absoluteMmDd_past_rollsToNextYear() {
        val r = parse("9月1日开学")
        assertEquals("开学", r.title)
        assertEquals(LocalDate.of(2027, 9, 1), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun numericShortMd() {
        val r = parse("10.23 体检")
        assertEquals("体检", r.title)
        assertEquals(LocalDate.of(2026, 10, 23), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun numericShortYm_2digitYear() {
        val r = parse("27.1.21 汇报")
        assertEquals("汇报", r.title)
        assertEquals(LocalDate.of(2027, 1, 21), r.dueDate)
    }

    @Test fun numericShortYmd_fullYear() {
        val r = parse("2027-1-21 汇报")
        assertEquals("汇报", r.title)
        assertEquals(LocalDate.of(2027, 1, 21), r.dueDate)
    }

    @Test fun numericShortMd_past_rollsToNextYear() {
        val r = parse("9.1 开学")
        assertEquals(LocalDate.of(2027, 9, 1), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun separatorVariants() {
        assertEquals(LocalDate.of(2026, 10, 23), parse("10/23 体检").dueDate)
        assertEquals(LocalDate.of(2026, 10, 23), parse("10-23 体检").dueDate)
    }

    // ---- 中文数字日期（用户实测：1月二日）----
    @Test fun chineseNumeral_monthDay_mixedWithAscii() {
        // 用户原话「1月二日，开会」：ASCII 月 + 中文数字日 必须都认
        val r = parse("1月二日，开会")
        assertEquals("开会", r.title)
        assertEquals(LocalDate.of(2027, 1, 2), r.dueDate) // 今年 1/2 已过 -> 明年
        assertNull(r.detail)
        assertFalse(r.dateError)
    }

    @Test fun chineseNumeral_monthDay_allChinese() {
        val r = parse("一月二日 开会")
        assertEquals("开会", r.title)
        assertEquals(LocalDate.of(2027, 1, 2), r.dueDate)
    }

    @Test fun chineseNumeral_teensAndThirtyOne() {
        // 十二 / 三十一 是三位汉字，正则要吃得下
        val r = parse("十二月三十一号 盘点")
        assertEquals("盘点", r.title)
        assertEquals(LocalDate.of(2026, 12, 31), r.dueDate)
    }

    // 「十」既是一位、又不属于 零~九：chineseNumber 的分支顺序曾经把 `十` 解析成 null，
    // 于是「十月」「十号」整体匹配失败。这里钉住它。
    @Test fun chineseNumeral_ten() {
        assertEquals(LocalDate.of(2027, 10, 3), parse("十月三日 开会").dueDate)
        assertEquals(LocalDate.of(2026, 11, 10), parse("十号 交租").dueDate)
    }

    @Test fun chineseNumeral_absYearMonthDay() {
        val r = parse("2026年十月二十四日 开会")
        assertEquals("开会", r.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.dueDate)
    }

    @Test fun chineseNumeral_dayOnly_rollsToNextMonth() {
        // 今天 10/21，本月的 2 号已过 -> 下个月 2 号
        val r = parse("二号 交房租")
        assertEquals("交房租", r.title)
        assertEquals(LocalDate.of(2026, 11, 2), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun chineseNumeral_nextMonthKeyword() {
        val r = parse("下个月五号 交房租")
        assertEquals("交房租", r.title)
        assertEquals(LocalDate.of(2026, 11, 5), r.dueDate)
    }

    // 显式写了年份就不顺延，过去的仍然报错
    @Test fun explicitYear_past_stillErrors() {
        val r = parse("2026年1月2日 开会")
        assertTrue(r.dateError)
    }

    // 2/29 在非闰年会落到下一个闰年
    @Test fun leapDay_rollsToNextLeapYear() {
        val r = parse("2月29日 检查")
        assertEquals(LocalDate.of(2028, 2, 29), r.dueDate)
        assertFalse(r.dateError)
    }

    // 本月 31 号还没过 -> 就是本月
    @Test fun dayOnly_thisMonthStillAhead() {
        val r = parse("三十一号 交租")
        assertEquals(LocalDate.of(2026, 10, 31), r.dueDate)
    }

    // ---------- 健壮性审计（DESIGN §28）回归 ----------

    // §28.1 P0：分钟 > 59 曾抛 DateTimeException（预览每敲一字符就 parse，等于打字打到一半崩溃）
    @Test fun minuteOver59_clampedNotCrash() {
        assertEquals(LocalTime.of(8, 59), parse("8:60 开会").dueTime)
        assertEquals(LocalTime.of(8, 59), parse("8点70分 开会").dueTime)
        // 99:99 是垃圾输入，只要不崩、取值落在合法区间即可（99%24=3，分钟被卡到 59）
        assertEquals(LocalTime.of(3, 59), parse("99:99 开会").dueTime)
    }

    // §28.1 P0：中文小时超过「十二」曾抛 NumberFormatException
    @Test fun chineseHourBeyondTwelve() {
        assertEquals(LocalTime.of(13, 0), parse("十三点 开会").dueTime)
        assertEquals(LocalTime.of(23, 0), parse("二十三点 开会").dueTime)
        assertEquals(LocalTime.of(15, 30), parse("十五点半 开会").dueTime)
    }

    // §28.2：小时字符类补「零」
    @Test fun zeroHourWord() {
        assertEquals(LocalTime.of(0, 0), parse("零点 开会").dueTime)
        assertEquals(LocalTime.of(0, 0), parse("凌晨零点 开会").dueTime)
    }

    // §28.2：本周X / 这周X（今天周三，本周五 = 10-23）
    @Test fun thisWeekPrefix() {
        assertEquals(LocalDate.of(2026, 10, 23), parse("本周五 开会").dueDate)
        assertEquals(LocalDate.of(2026, 10, 23), parse("这周五 开会").dueDate)
        assertEquals(LocalDate.of(2026, 10, 23), parse("这个星期五 开会").dueDate)
        assertEquals("开会", parse("本周五 开会").title)
    }

    // §28.2：下月 = 下个月
    @Test fun nextMonthShortForm() {
        assertEquals(LocalDate.of(2026, 11, 5), parse("下月5号 交租").dueDate)
    }

    // §28.2：周七
    @Test fun weekdaySeven() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("周七 开会").dueDate)
    }

    // §28.2：下下周X 曾只加一周（给了下周三的日期）
    @Test fun nextNextWeek_isTwoWeeksAhead() {
        assertEquals(LocalDate.of(2026, 10, 28), parse("下周三 开会").dueDate)
        assertEquals(LocalDate.of(2026, 11, 4), parse("下下周三 开会").dueDate)
    }

    // §28.2：裸时段词补齐
    @Test fun bareEarlyMorningPeriods() {
        assertEquals(LocalTime.of(1, 0), parse("凌晨 开会").dueTime)
        assertEquals(LocalTime.of(0, 0), parse("半夜 开会").dueTime)
        assertEquals(LocalTime.of(6, 0), parse("清晨 跑步").dueTime)
        assertEquals(LocalTime.of(22, 0), parse("夜里 开会").dueTime)
        assertEquals("开会", parse("清晨6点 开会").title)
    }

    // §28.2：全角数字/冒号归一
    @Test fun fullWidthInput() {
        assertEquals(LocalTime.of(8, 30), parse("８：３０ 开会").dueTime)
        assertEquals(LocalTime.of(15, 0), parse("１５点 开会").dueTime)
        assertEquals(LocalDate.of(2026, 10, 24), parse("１０月２４日 开会").dueDate)
        assertEquals(LocalDate.of(2026, 10, 24), parse("２０２６年１０月２４日 开会").dueDate)
    }

    // ---------- 健壮性审计 §28.3（P2 误伤）回归 ----------

    // 「N号」+ 单位/量词是序号，不是日期
    @Test fun numberPlusUnit_isNotADate() {
        val r = parse("1号线 坐地铁")
        assertNull(r.dueDate)
        assertEquals("1号线", r.title)
        assertEquals("坐地铁", r.detail)
        assertNull(parse("3号楼 开会").dueDate)
        assertNull(parse("5号窗口 取件").dueDate)
    }

    @Test fun numberPlusMeetingRoom_isNotADate() {
        val r = parse("三号会议室 开会")
        assertNull(r.dueDate)
        assertEquals("三号会议室", r.title)
    }

    // 反向：动词「开会」的「会」不能被当成"会议室"，否则「三号开会」建不出日期
    @Test fun numberPlusVerb_isStillADate() {
        assertEquals(LocalDate.of(2026, 11, 3), parse("三号开会").dueDate)
    }

    // 寒暄/名词不是时间
    @Test fun greetingsAreNotTimes() {
        assertEquals("早上好", parse("早上好").title)
        assertNull(parse("早上好").dueTime)
        assertNull(parse("晚上好").dueTime)
        assertNull(parse("下午茶").dueTime)
        assertNull(parse("中午饭").dueTime)
    }

    // 「一点点」不是 01:00
    @Test fun aLittleBit_isNotOneOClock() {
        val r = parse("一点点")
        assertNull(r.dueTime)
        assertEquals("一点点", r.title)
    }

    // ASCII 的「N刻」= N*15 分（此前被切成 08:03 + 标题「刻」）
    @Test fun asciiKe() {
        assertEquals(LocalTime.of(8, 45), parse("8点3刻 开会").dueTime)
        assertEquals(LocalTime.of(8, 15), parse("8点1刻 开会").dueTime)
    }

    // ---- times ----
    @Test fun exactClock_hhmm() {
        val r = parse("下午3点 交周报")
        assertEquals("交周报", r.title)
        assertEquals(LocalTime.of(15, 0), r.dueTime)
        assertTrue(r.hasExactTime)
    }

    @Test fun hhmmColon() {
        val r = parse("交周报 15:30")
        assertEquals("交周报", r.title)
        assertEquals(LocalTime.of(15, 30), r.dueTime)
        assertTrue(r.hasExactTime)
    }

    @Test fun hourWord_noPrefix_bare() {
        val r = parse("晚上8点 跑步")
        assertEquals("跑步", r.title)
        assertEquals(LocalTime.of(20, 0), r.dueTime)
    }

    @Test fun hourWordHalf() {
        val r = parse("早上7点半 起床")
        assertEquals("起床", r.title)
        assertEquals(LocalTime.of(7, 30), r.dueTime)
    }

    @Test fun noonPrefix() {
        val r = parse("中午12点吃饭")
        assertEquals("吃饭", r.title)
        assertEquals(LocalTime.of(12, 0), r.dueTime)
    }

    // ---- combined date+time ----
    @Test fun tomorrow_afternoon_time() {
        val r = parse("明天下午4点 交周报")
        assertEquals("交周报", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(16, 0), r.dueTime)
    }

    // ---- night words shift bare hours to PM ----
    @Test fun tonight_9_to_21() {
        val r = parse("今晚9点吃饭")
        assertEquals("吃饭", r.title)
        assertEquals(today, r.dueDate)
        assertEquals(LocalTime.of(21, 0), r.dueTime)
    }

    @Test fun tonight_9_with_suffix_21_30() {
        val r = parse("今晚9点半跑步")
        assertEquals("跑步", r.title)
        assertEquals(LocalTime.of(21, 30), r.dueTime)
    }

    @Test fun tomorrowNight_8_to_20() {
        val r = parse("明晚8点看电影")
        assertEquals("看电影", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(20, 0), r.dueTime)
    }

    @Test fun bare_evening_prefix_still_pm() {
        val r = parse("晚上6点打篮球")
        assertEquals(LocalTime.of(18, 0), r.dueTime)
    }

    @Test fun tomorrow_noon_preserved() {
        val r = parse("明天中午12点吃饭")
        assertEquals(LocalTime.of(12, 0), r.dueTime)
    }

    // ---- year / month keywords ----
    @Test fun nextYear_full() {
        val r = parse("下一年1月15日交年检")
        assertEquals("交年检", r.title)
        assertEquals(LocalDate.of(2027, 1, 15), r.dueDate)
    }

    @Test fun nextYear_noDay() {
        val r = parse("下一年3月开会")
        val d = r.dueDate
        assertNotNull(d)
        assertEquals(2027, d!!.year)
        assertEquals(3, d.monthValue)
        assertEquals(today.dayOfMonth.coerceAtMost(31).let{ it }, d.dayOfMonth)
    }

    @Test fun thisYear_monthFuture() {
        val r = parse("今年11月5日体检")
        assertEquals(LocalDate.of(2026, 11, 5), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun thisYear_pastMonth_errors() {
        val r = parse("今年3月1日体检")
        assertTrue(r.dateError)
    }

    @Test fun nextMonth_fullDay() {
        val r = parse("下个月10日交房租")
        assertEquals(LocalDate.of(2026, 11, 10), r.dueDate)
    }

    @Test fun nextMonth_noDay() {
        val r = parse("下个月交水电")
        val d = r.dueDate
        assertNotNull(d)
        assertEquals(2026, d!!.year)
        assertEquals(11, d.monthValue)
    }

    @Test fun thisMonth_fullDay() {
        val r = parse("本月25日还信用卡")
        assertEquals(LocalDate.of(2026, 10, 25), r.dueDate)
        assertFalse(r.dateError)
    }

    @Test fun bareThisYear_noMonth_noDate() {
        val r = parse("今年报个税")
        assertNull(r.dueDate)
        assertEquals("今年报个税", r.title)
    }

    @Test fun bareNextYear_noMonth_noDate() {
        val r = parse("明年体检")
        assertNull(r.dueDate)
        assertEquals("明年体检", r.title)
    }

    // ---- today's already-past time -> error ----
    @Test fun todayPastTime_errors() {
        val r = Parser(today, LocalTime.of(9, 0)).parse("今天8点开会")
        assertTrue(r.timeError)
        assertEquals(LocalTime.of(8, 0), r.dueTime)
        assertEquals(today, r.dueDate)
    }

    @Test fun todayFutureTime_ok() {
        val r = Parser(today, LocalTime.of(9, 0)).parse("今天10点开会")
        assertFalse(r.timeError)
        assertEquals(LocalTime.of(10, 0), r.dueTime)
    }

    // Regression: the app reuses ONE Parser() all session. Its `now` must come from the
    // *current* wall clock on every call, not the moment the Parser was constructed —
    // otherwise "今晚8点" typed after 8pm wouldn't flag timeError (stale construction-time now).
    @Test fun reusedParser_readsCurrentClock() {
        val p = Parser(LocalDate.now()) // fixed today, NO fixed now => real current time
        val r = p.parse("今天0点留意") // 00:00 today is already past (unless it's exactly midnight now)
        val currentMinute = java.time.LocalTime.now()
        if (currentMinute.hour == 0 && currentMinute.minute == 0) {
            return // exact midnight edge; can't assert reliably
        }
        assertTrue("a today-00:00 time must be seen as past by a fresh-clock parser: $currentMinute", r.timeError)
        assertEquals(LocalDate.now(), r.dueDate)
    }

    @Test fun todayPastTime_futureDate_notError() {
        val r = Parser(today, LocalTime.of(9, 0)).parse("明天8点开会")
        assertFalse(r.timeError)
        assertEquals(LocalTime.of(8, 0), r.dueTime)
    }

    // ---- Chinese-numeral hour extraction (两点半 etc.) ----
    @Test fun chinese_two_half() {
        val r = parse("明天两点半开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(2, 30), r.dueTime)
        assertEquals(today.plusDays(1), r.dueDate)
    }

    @Test fun chinese_twelve() {
        val r = parse("明天十二点吃饭")
        assertEquals(LocalTime.of(12, 0), r.dueTime)
    }

    @Test fun chinese_eleven() {
        val r = parse("明天十一点开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(11, 0), r.dueTime)
    }

    @Test fun chinese_two_with_minutes() {
        val r = parse("明天两点十五分开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(2, 15), r.dueTime)
    }

    @Test fun chinese_afternoon_prefix_pm() {
        val r = parse("明天下午三点开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(15, 0), r.dueTime)
    }

    // ---- user's exact phrasing: 下个月6号，两点半开会 ----
    @Test fun userExact_nextMonth_6_chineseTwoThirty() {
        val r = parse("下个月6号，两点半开会")
        assertEquals("开会", r.title)
        assertEquals(LocalDate.of(2026, 11, 6), r.dueDate)
        assertEquals(LocalTime.of(2, 30), r.dueTime)
    }

    @Test fun chineseThirty_as_thirty() {
        val r = parse("明天十点半开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(10, 30), r.dueTime)
    }

    @Test fun chineseTwentyFive() {
        val r = parse("明天三点二十五分开会")
        assertEquals("开会", r.title)
        assertEquals(LocalTime.of(3, 25), r.dueTime)
    }

    // ---- repeat (每天/每周/每周X/每月) ----
    @Test fun repeat_daily() {
        val r = parse("每天早锻炼")
        assertEquals("早锻炼", r.title)
        assertEquals(1, r.repeatDays)
        assertNotNull(r.dueDate)
    }

    @Test fun repeat_weekly() {
        val r = parse("每周例会")
        assertEquals("例会", r.title)
        assertEquals(7, r.repeatDays)
    }

    @Test fun repeat_monthly() {
        val r = parse("每月交房租")
        assertEquals("交房租", r.title)
        assertEquals(30, r.repeatDays)
    }

    @Test fun repeat_weekdayFriday() {
        // 2026-10-21 is Wednesday; next Friday = 10/23.
        val r = parse("每周五开会")
        assertEquals("开会", r.title)
        assertEquals(7, r.repeatDays)
        assertEquals(LocalDate.of(2026, 10, 23), r.dueDate)
    }

    // ---- 只有时间没有日期：必须推断成"下一次这个钟点"，不能留成无日期 ----
    // 回归：输入"早上测试一"曾经 dueDate=null + dueTime=08:00，落库成"无期"，
    // 但预览卡片显示"8点"，两处说法打架。

    private fun parserAt(now: LocalTime) = Parser(today, now)

    @Test fun timeOnly_noDate_infersToday_whenClockAhead() {
        val r = parserAt(LocalTime.of(7, 0)).parse("早上测试一")
        assertEquals("测试一", r.title)
        assertEquals(today, r.dueDate)
        assertEquals(LocalTime.of(8, 0), r.dueTime)
        assertFalse(r.timeError)
    }

    @Test fun timeOnly_noDate_infersTomorrow_whenClockPassed() {
        // 09:00 输入"早上…"，8 点已经过了 -> 明天 8 点，而不是无日期。
        val r = parserAt(LocalTime.of(9, 0)).parse("早上测试一")
        assertEquals("测试一", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(8, 0), r.dueTime)
        assertFalse(r.timeError)
    }

    @Test fun clockOnly_noDate_infersTomorrow_whenPassed() {
        val r = parserAt(LocalTime.of(15, 0)).parse("3点开会")
        assertEquals("开会", r.title)
        assertEquals(today.plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(3, 0), r.dueTime)
    }

    @Test fun clockOnly_noDate_staysToday_whenAhead() {
        val r = parserAt(LocalTime.of(8, 0)).parse("下午3点开会")
        assertEquals(today, r.dueDate)
        assertEquals(LocalTime.of(15, 0), r.dueTime)
    }

    // 显式写了"今天"就不顺延，仍然报"时间已过"——用户明确指定了日期。
    @Test fun explicitToday_pastTime_stillTimeError() {
        val r = parserAt(LocalTime.of(9, 0)).parse("今天早上8点开会")
        assertEquals(today, r.dueDate)
        assertTrue(r.timeError)
    }

    // 没有时间的纯标题不受影响。
    @Test fun noTimeNoDate_staysDateless() {
        val r = parserAt(LocalTime.of(9, 0)).parse("买牛奶")
        assertNull(r.dueDate)
        assertNull(r.dueTime)
    }

    // 完整日期后面带"日/号"时，后缀必须一起吃掉，不能留在标题里。
    @Test fun fullDate_withDaySuffix_notLeakedIntoTtitle() {
        val r = parse("2026年10月24日开会")
        assertEquals("开会", r.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.dueDate)
    }

    @Test fun dashedDate_withDaySuffix_notLeakedIntoTtitle() {
        val r = parse("2026-10-24日开会")
        assertEquals("开会", r.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.dueDate)
    }
}
