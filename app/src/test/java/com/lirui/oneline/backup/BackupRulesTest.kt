package com.lirui.oneline.backup

import com.lirui.oneline.util.AppTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/**
 * 备份规则层的本地单测。
 *
 * ★ JSON 读写本身测不了：`org.json` 在 JVM 单测里是 android.jar 的 stub
 *   （`unitTests.isReturnDefaultValues = true`），`JSONObject()` 直接返回默认值。
 *   这里只覆盖能独立判定的规则，流读写靠真机导出 / 导入验收（见 DESIGN.md §54）。
 */
class BackupRulesTest {

    @Test
    fun acceptsCurrentFormatAndVersion() {
        assertTrue(BackupRules.isSupported("oneline-backup", 1))
    }

    @Test
    fun rejectsForeignFormatAndFutureVersion() {
        assertFalse(BackupRules.isSupported("other-backup", 1))
        assertFalse(BackupRules.isSupported(null, 1))
        assertFalse(BackupRules.isSupported("oneline-backup", 0))
        assertFalse(BackupRules.isSupported("oneline-backup", 2))
        assertFalse(BackupRules.isSupported("oneline-backup", -1))
    }

    @Test
    fun privacyConsentVersionIsNeverWrittenBack() {
        // 导入旧备份不该让用户重新弹同意，也不该被旧值绕过当前版本的同意。
        assertFalse(BackupRules.importableSetting("privacy_accepted_version"))
        assertTrue(BackupRules.importableSetting("theme_mode"))
        assertTrue(BackupRules.importableSetting("focus_daily_goal_minutes"))
    }

    @Test
    fun activeFocusRunStaysOutOfTheBackup() {
        assertTrue(BackupRules.exportableFocusKey("last_minutes"))
        assertTrue(BackupRules.exportableFocusKey("last_count_up"))
        // 这些是「进行中专注」的运行态，导出去再导入会复活一个僵尸计时。
        assertFalse(BackupRules.exportableFocusKey("active"))
        assertFalse(BackupRules.exportableFocusKey("segment_start_millis"))
        assertFalse(BackupRules.exportableFocusKey("started_at_millis"))
    }

    @Test
    fun fileNameIsAsciiAndUsesBeijingTime() {
        val epoch = ZonedDateTime.of(2026, 11, 7, 15, 30, 0, 0, AppTime.ZONE)
            .toInstant().toEpochMilli()
        assertEquals("oneline-backup-20261107-1530.json", BackupRules.suggestedFileName(epoch))
        assertTrue(
            BackupRules.suggestedFileName(epoch)
                .matches(Regex("oneline-backup-\\d{8}-\\d{4}\\.json"))
        )
    }

    @Test
    fun missingExportTimeIsExplicitlyUnknown() {
        assertEquals("未知", BackupRules.formatTime(0L))
        assertEquals("未知", BackupRules.formatTime(-1L))
    }

    @Test
    fun restoreOffersExactlyThreeModes() {
        val modes = BackupRules.RestoreMode.values()
        assertEquals(3, modes.size)
        assertEquals(
            setOf("整份覆盖", "保留并导入", "去重导入"),
            modes.map { it.label }.toSet()
        )
    }
}
