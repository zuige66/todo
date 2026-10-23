package com.lirui.oneline.data

import android.content.Context
import com.lirui.oneline.data.model.Todo
import com.lirui.oneline.parser.ParsedTodo
import com.lirui.oneline.reminder.NotificationDismissScheduler
import com.lirui.oneline.reminder.NotificationHelper
import com.lirui.oneline.reminder.ReminderEngine
import com.lirui.oneline.reminder.ReminderScheduler
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Single facade over the DB + reminder scheduler + parse conversion.
 */
class TodoRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).todoDao()

    val active: Flow<List<Todo>> = dao.observeActive()
    val archived: Flow<List<Todo>> = dao.observeArchived()

    suspend fun addFromParsed(parsed: ParsedTodo): Long {
        val due = parsedToDue(parsed)
        val start = parsedToStart(parsed)
        val id = dao.insert(Todo(
            title = parsed.cleanTitle,
            detail = parsed.detail.orEmpty(),
            dueAtMillis = due?.first,
            startAtMillis = start,
            hasExactTime = parsed.hasExactTime,
            repeatDays = parsed.repeatDays
        ))
        due?.let { (epoch, exact) ->
            ReminderScheduler.scheduleBlocking(context, id, epoch, exact, start)
        }
        return id
    }

    /**
     * Build the concrete due epoch from a [ParsedTodo].
     * Returns null for a no-date item. Defaults missing time-of-day to the user-set default
     * (SettingsRepo.defaultMinuteOfDay, default 09:00).
     */
    private fun parsedToDue(parsed: ParsedTodo): Pair<Long, Boolean>? {
        val zone = AppTime.ZONE
        val date = parsed.dueDate
        if (date == null) return null
        val time = parsed.dueTime ?: defaultTime(parsed.dueDate)
        val zdt = ZonedDateTime.of(date, time, zone)
        return zdt.toInstant().toEpochMilli() to parsed.hasExactTime
    }

    /**
     * 时间段的开始日 epoch；单日待办（[ParsedTodo.startDate] == null）返回 null。
     * 开始日只按「日」存（当天 00:00）—— 时刻只作用于结束日（dueAtMillis）。
     */
    private fun parsedToStart(parsed: ParsedTodo): Long? {
        val date = parsed.startDate ?: return null
        // 开始日 == 结束日（"周一到周一"/"24日到24日"）就是单日待办，不要落成退化区间，
        // 否则列表会一直挂着「最后一天」。
        if (parsed.dueDate == null || parsed.dueDate == date) return null
        return date.atStartOfDay(AppTime.ZONE).toInstant().toEpochMilli()
    }

    /** Default time-of-day for a date-only todo — the user-configurable default. */
    private fun defaultTime(date: LocalDate?): LocalTime {
        val mo = SettingsRepo.defaultMinuteOfDay(context)
        return LocalTime.of(mo / 60, mo % 60)
    }

    /**
     * Settings test button: a 「通知测试」 todo due at the next whole minute.
     *
     * 标题只写「通知测试」，**不要**把「该办了」写进来 —— 那是提醒状态语，
     * 由 `NotificationHelper.composeTitle` 拼成 `通知测试 🚀🚀该办了⏳⏳`
     * （状态语与 emoji 现在都可在设置页改，默认值即此）。
     * 写进标题会变成「通知测试 该办了 🚀🚀该办了⏳⏳」。
     */
    suspend fun addTestInOneMinute(): Long {
        val zone = AppTime.ZONE
        val due = ZonedDateTime.now(zone).withSecond(0).withNano(0).plusMinutes(1)
        val epoch = due.toInstant().toEpochMilli()
        val id = dao.insert(Todo(
            title = "通知测试",
            detail = "下一分钟到期",
            dueAtMillis = epoch,
            hasExactTime = true
        ))
        ReminderScheduler.scheduleBlocking(context, id, epoch, true)
        return id
    }

    /**
     * 归档一条待办（重复任务改为顺延）。
     *
     * @return false = 这条待办已经不在了（被删除 / 被别处处理过）。
     *   调用方必须据此收起"归档中"状态，否则 UI 会永远停在勾选态。
     */
    suspend fun archive(id: Long): Boolean {
        val todo = dao.getById(id) ?: return false
        // A repeating todo rolls to its next occurrence instead of being archived.
        if (todo.repeatDays > 0 && SettingsRepo.autoRepeatEnabled(context)) {
            rollForward(todo)
            return true
        }
        dao.update(todo.copy(archived = true, completedAt = System.currentTimeMillis()))
        ReminderScheduler.cancelForId(context, id)
        return true
    }

    /** Advance a repeating todo to its next occurrence and re-schedule its reminders. */
    private suspend fun rollForward(todo: Todo) {
        val zone = AppTime.ZONE
        val due = todo.dueAtMillis
        if (due == null) { // no-date repeat: just bounce completedAt, keep it active
            dao.update(todo.copy(completedAt = System.currentTimeMillis()))
            return
        }
        val inst = java.time.Instant.ofEpochMilli(due)
        val curDate = inst.atZone(zone).toLocalDate()
        val curTime = inst.atZone(zone).toLocalTime()
        val today = LocalDate.now(zone)
        val nowTime = LocalTime.now(zone)
        var next = curDate
        var guard = 0
        // Advance by at least one step; keep advancing while the resulting date+time is not
        // in the future. Comparing only the date would leave "每天18点" completed at 22:00
        // pointing at today 18:00 again (already past -> immediate re-nag).
        do {
            next = next.plusDays(todo.repeatDays.toLong())
            guard++
        } while ((next.isBefore(today) || (next == today && curTime.isBefore(nowTime))) && guard < 400)
        val nextEpoch = ZonedDateTime.of(next, curTime, zone).toInstant().toEpochMilli()
        // 顺延时开始日同步平移（重复任务通常没有开始日，这里是防御性处理）。
        val nextStart = todo.startAtMillis?.let { it + (nextEpoch - due) }
        dao.updateContent(
            id = todo.id,
            title = todo.title,
            detail = todo.detail,
            dueAtMillis = nextEpoch,
            startAtMillis = nextStart,
            hasExactTime = todo.hasExactTime,
            repeatDays = todo.repeatDays
        )
        ReminderScheduler.scheduleBlocking(context, todo.id, nextEpoch, todo.hasExactTime, nextStart)
    }

    suspend fun unarchive(id: Long) {
        dao.getById(id)?.let {
            dao.update(it.copy(archived = false, completedAt = null))
            it.dueAtMillis?.let { due ->
                ReminderScheduler.scheduleFor(context, id, due, it.hasExactTime, it.startAtMillis)
            }
        }
    }

    /**
     * 删除（硬删除，不可恢复）。
     *
     * 入口在归档页，点「删除」会先弹确认对话框 —— 确认只有一道，但必须有：
     * 这条待办已经完成过，用户很容易把"清掉一条旧记录"和"就这么留着"看混。
     *
     * 闹钟和通知要**立刻**撤：待办已经从表里消失，留着闹钟只会在几分钟后弹出一条
     * 点进去找不到东西的提醒。延时撤通知那套（[NotificationDismissScheduler]）是给
     * "完成"用的 —— 删除没有反悔窗口，不需要留。
     */
    suspend fun delete(id: Long) {
        NotificationDismissScheduler.cancelScheduled(id)
        NotificationHelper.cancelReminder(context, id)
        ReminderScheduler.cancelForId(context, id)
        dao.deleteById(id)
    }

    /** Replace a todo's content (title / due / exact / repeat) from a fresh parse. */
    suspend fun editFromParsed(id: Long, parsed: ParsedTodo) {
        val due = parsedToDue(parsed)
        val start = parsedToStart(parsed)
        dao.updateContent(
            id = id,
            title = parsed.cleanTitle,
            detail = parsed.detail.orEmpty(),
            dueAtMillis = due?.first,
            startAtMillis = start,
            hasExactTime = parsed.hasExactTime,
            repeatDays = parsed.repeatDays
        )
        // Rebuild reminders for the new due time.
        if (due != null) {
            val archivedTodo = dao.getById(id)
            if (archivedTodo?.archived == false) {
                ReminderScheduler.scheduleBlocking(context, id, due.first, due.second, start)
            }
        } else {
            ReminderScheduler.cancelForId(context, id)
        }
    }

    suspend fun getById(id: Long): Todo? = dao.getById(id)

    // ---------- notification action buttons (「完成」/「撤回」) ----------

    /**
     * Notification 「完成」action. Intentionally delegates to [archive] so the notification
     * button and the list circle can never drift apart: normal todos are archived, repeating
     * ones roll forward to their next occurrence.
     */
    suspend fun completeFromNotification(id: Long) {
        archive(id)
    }
    /**
     * Notification 「撤回」action — the inverse of [completeFromNotification].
     *
     * - Repeating todo (rolled forward by [rollForward]): step back exactly one period and
     *   re-arm its reminders. The todo may land in the past, which is correct: the user is
     *   saying "I did not actually finish this one".
     * - Normal todo: un-archive it and rebuild its reminders.
     *
     * @return the updated row, or null when the todo no longer exists.
     */
    suspend fun undoFromNotification(id: Long): Todo? {
        val todo = dao.getById(id) ?: return null
        if (todo.repeatDays > 0 && SettingsRepo.autoRepeatEnabled(context)) {
            val prev = ReminderEngine.previousOccurrence(todo.dueAtMillis, todo.repeatDays)
            if (prev != null) {
                dao.updateContent(
                    id = todo.id,
                    title = todo.title,
                    detail = todo.detail,
                    dueAtMillis = prev,
                    startAtMillis = todo.startAtMillis,
                    hasExactTime = todo.hasExactTime,
                    repeatDays = todo.repeatDays
                )
                ReminderScheduler.scheduleBlocking(
                    context, todo.id, prev, todo.hasExactTime, todo.startAtMillis
                )
            }
            // No-date repeat: there is no period to roll back, nothing to do.
            return dao.getById(id)
        }
        if (todo.archived) unarchive(id)
        return dao.getById(id)
    }
}