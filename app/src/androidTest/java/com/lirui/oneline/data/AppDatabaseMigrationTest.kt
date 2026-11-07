package com.lirui.oneline.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 迁移链 v1 → v7 的真机测试（v1.0.10 补）。
 *
 * 为什么非要 instrumented test：迁移是否写对，取决于 **PRAGMA table_info 里读到的
 * 实际列定义**，不是 Kotlin 实体长什么样。0.9.3 那次「迁移完秒崩」就是因为
 * `ALTER TABLE ... ADD COLUMN deletedAt INTEGER` 没写 `DEFAULT NULL`，
 * 编译期、本地单测都发现不了，只有 Room 拿到真实 SQLite 库校验时才会炸。
 *
 * 依赖：
 *  - `androidx.room:room-testing:2.8.4`
 *  - `app/schemas/.../1.json ~ 7.json`（MigrationTestHelper 用它建旧版本库）
 *  - 一台设备/模拟器：`./gradlew :app:connectedDebugAndroidTest`
 *
 * 迁移对象统一取 [AppDatabase.ALL_MIGRATIONS]，不在这里另抄一份 —— 抄漏一条
 * 就等于测了一条根本没在跑的链。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList()
    )

    private val dbName = "migration-test"

    /** 全链 v1→v7：老待办不能丢，六个版本加进来的列都得在，新表建得出来。 */
    @Test
    fun migrate1To7_preservesTodosAndAddsAllColumns() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO todos " +
                    "(id, title, dueAtMillis, hasExactTime, archived, createdAt, completedAt) " +
                    "VALUES (1, '买牛奶', 1700000000000, 0, 0, 1700000000000, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName, 7, true, *AppDatabase.ALL_MIGRATIONS
        )

        // 老数据还在，且没被迁移改写。
        db.query("SELECT title, archived FROM todos WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("买牛奶", c.getString(0))
            assertEquals(0, c.getInt(1))
        }

        // 每个版本新加的列都存在（Room 的 validate 会另外核对类型 / NOT NULL / 默认值）。
        val expected = listOf(
            "detail", "repeatDays", "startAtMillis", "deletedAt", "endAtMillis"
        )
        val actual = mutableListOf<String>()
        db.query("PRAGMA table_info(todos)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) actual += c.getString(nameIdx)
        }
        expected.forEach { col ->
            assertTrue("v7 缺少列 $col", col in actual)
        }

        // v6→v7 建的专注表：结构由 runMigrationsAndValidate 校验，这里确认能查。
        db.query("SELECT COUNT(*) FROM focus_sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    /**
     * 0.9.3 那条红线：deletedAt 的 dflt_value 必须是字符串 `'NULL'`。
     * 只写 `ADD COLUMN x INTEGER` 时这里是 null，Room 迁移校验会直接抛
     * `Migration didn't properly handle`，App 一打开就崩。
     */
    @Test
    fun migrate4To5_deletedAtHasExplicitDefaultNull() {
        helper.createDatabase(dbName, 4).apply { close() }
        val db = helper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.ALL_MIGRATIONS[3]
        )
        db.query("PRAGMA table_info(todos)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            val dfltIdx = c.getColumnIndex("dflt_value")
            var defaultValue: String? = null
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == "deletedAt") defaultValue = c.getString(dfltIdx)
            }
            assertEquals("NULL", defaultValue)
        }
        db.close()
    }

    /** v6→v7 的建表列必须和 FocusSession 实体逐字对齐，否则同样启动即崩。 */
    @Test
    fun migrate6To7_createsFocusSessionsTable() {
        helper.createDatabase(dbName, 6).apply { close() }
        val db = helper.runMigrationsAndValidate(
            dbName, 7, true, AppDatabase.ALL_MIGRATIONS[5]
        )
        db.query("SELECT COUNT(*) FROM focus_sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }
}
