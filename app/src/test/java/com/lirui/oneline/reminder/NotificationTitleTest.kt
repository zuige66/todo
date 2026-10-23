package com.lirui.oneline.reminder

import com.lirui.oneline.util.NotificationStyle
import com.lirui.oneline.util.SettingsRepo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通知标题的拼装规则。
 *
 * v0.4 把「前缀 / 提醒语 / 后缀」三段合并成一个模板（`{提醒}` 是占位符），
 * 这些用例锁住三件事：
 * 1. 占位符按提醒类型展开（提前提醒不能也显示"该办了"）；
 * 2. 空模板 / 空状态语不产生多余空格（否则通知标题会莫名提前截断）；
 * 3. 完成后走另一条规则（事件名 + 完成后缀）。
 */
class NotificationTitleTest {

    private val token = SettingsRepo.NOTIF_REMINDER_TOKEN

    private fun style(
        template: String,
        done: String = "🎉🎉",
        dueWord: String = "该办了"
    ) = NotificationStyle(titleTemplate = template, done = done, dueWord = dueWord)

    @Test
    fun placeholderExpandsToReminderWord() {
        assertEquals(
            "开组会 🚀🚀该办了⏳⏳",
            NotificationHelper.composeTitle("开组会", "该办了", false, style("🚀🚀${token}⏳⏳"))
        )
    }

    @Test
    fun placeholderExpandsToAdvanceWord() {
        // 同一条模板，换提醒类型就换状态语 —— 这正是"不能合并成写死一句话"的原因。
        assertEquals(
            "开组会 🚀🚀提前提醒⏳⏳",
            NotificationHelper.composeTitle("开组会", "提前提醒", false, style("🚀🚀${token}⏳⏳"))
        )
    }

    @Test
    fun emptyTemplateLeavesOnlyTitle() {
        assertEquals(
            "开组会",
            NotificationHelper.composeTitle("开组会", "该办了", false, style(""))
        )
    }

    @Test
    fun templateWithoutPlaceholderDropsMessage() {
        // 用户主动删掉占位符：四种提醒共用这一句固定标题，是他自己的选择。
        assertEquals(
            "开组会 固定后缀",
            NotificationHelper.composeTitle("开组会", "该办了", false, style("固定后缀"))
        )
    }

    @Test
    fun trailingSpacesAreTrimmed() {
        assertEquals(
            "开组会 🚀🚀",
            NotificationHelper.composeTitle("开组会", "该办了", false, style("🚀🚀 "))
        )
    }

    @Test
    fun emptyMessageDoesNotLeaveDoubleSpace() {
        assertEquals(
            "开组会 🚀🚀⏳⏳",
            NotificationHelper.composeTitle("开组会", "", false, style("🚀🚀${token}⏳⏳"))
        )
    }

    @Test
    fun doneUsesDoneSuffixOnly() {
        assertEquals(
            "开组会 🎉🎉",
            NotificationHelper.composeTitle("开组会", "该办了", true, style("🚀🚀${token}⏳⏳"))
        )
    }

    @Test
    fun emptyDoneSuffixLeavesTitleOnly() {
        assertEquals(
            "开组会",
            NotificationHelper.composeTitle("开组会", "该办了", true, style("🚀🚀${token}⏳⏳", done = ""))
        )
    }

    @Test
    fun multiplePlaceholdersAllExpand() {
        assertEquals(
            "开组会 该办了-该办了",
            NotificationHelper.composeTitle("开组会", "该办了", false, style("${token}-${token}"))
        )
    }
}
