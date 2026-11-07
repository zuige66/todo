package com.lirui.oneline.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较是检查更新里唯一的"算法"，也是最容易悄悄错的地方。
 * 这些用例覆盖实际会出现的 tag 形态（`v0.3`、`0.3.5-sessionfix-test`、`1.0`）。
 */
class VersionTest {

    @Test
    fun patchIsNewer() {
        assertTrue(Version.isNewer("0.4.1", "0.4.0") == true)
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(Version.isNewer("0.4", "0.4.0") == true)
        assertFalse(Version.isNewer("0.4.0", "0.4") == true)
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(Version.isNewer("0.3.9", "0.4.0") == true)
        assertFalse(Version.isNewer("0.9", "1.0") == true)
    }

    @Test
    fun leadingVIsIgnored() {
        assertTrue(Version.isNewer("v0.5", "0.4") == true)
        assertTrue(Version.isNewer("V0.5", "v0.4") == true)
    }

    @Test
    fun prereleaseSuffixDoesNotChangeComparison() {
        // `0.4.0-beta` 与 `0.4.0` 视为同版本：发版 tag 带后缀时不应反复提示更新。
        assertEquals(0, Version.compare("0.4.0-beta.1", "0.4.0"))
        assertTrue(Version.isNewer("0.4.1-rc1", "0.4.0") == true)
    }

    @Test
    fun segmentCountDiffers() {
        assertEquals(0, Version.compare("1.2", "1.2.0"))
        assertTrue(Version.isNewer("1.2.1", "1.2") == true)
    }

    @Test
    fun unparsableReturnsNullNotFalse() {
        // null 和 false 必须区分：null 要提示"版本号格式异常"，false 是"已是最新"。
        assertNull(Version.isNewer("latest", "0.4"))
        assertNull(Version.isNewer("", "0.4"))
        assertNull(Version.isNewer("0.4", "nightly"))
        assertNull(Version.parse("v"))
    }

    @Test
    fun parseKeepsDigits() {
        assertEquals(listOf(0, 4), Version.parse("v0.4"))
        assertEquals(listOf(1, 2, 3), Version.parse(" 1.2.3 "))
    }
}
