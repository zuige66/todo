package com.lirui.oneline.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lirui.oneline.data.model.FocusSession
import com.lirui.oneline.data.model.Todo

@Database(
    entities = [Todo::class, FocusSession::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Add [repeatDays] (default 0 = no-repeat) to the todos table. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN repeatDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Add [detail] (free text after the event title) to the todos table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN detail TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Add [startAtMillis]（时间段开始日；可空，null = 单日待办）。
         * 老数据全部是单日，startAtMillis 自然为 null，行为零变化。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN startAtMillis INTEGER")
            }
        }

        /**
         * Add [Todo.deletedAt]（历史列，现在没有任何代码写它；见 [Todo] 的类注释）。
         *
         * ★★ 这一句必须带 `DEFAULT NULL`。踩过的坑（0.9.3 一打开就闪退）：
         *   实体上写的是 `@ColumnInfo(defaultValue = "NULL")`，Room 迁移完会拿**实际列定义**
         *   跟实体期望的比对 —— 而 `ALTER TABLE ... ADD COLUMN x INTEGER`（不写 DEFAULT）
         *   在 `PRAGMA table_info` 里读到的 dflt_value 是**空**，只有显式写
         *   `DEFAULT NULL` 才是字符串 `'NULL'`。两者不等 → Room 抛
         *   `Migration didn't properly handle: todos(...)` → 启动即崩，且编译期完全看不出来。
         *
         * ★ 幂等：已经装过出问题那一版、迁移中途失败但**没有回滚**的设备，列可能已经存在。
         *   直接 ALTER 会抛 "duplicate column name"，所以先查 PRAGMA 再决定加不加。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "todos", "deletedAt")) {
                    db.execSQL("ALTER TABLE todos ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                }
            }
        }

        /**
         * Add [Todo.endAtMillis]（时间区间结束点；可空，null = 普通待办）。
         * 老数据全 null，行为零变化。同样必须带 `DEFAULT NULL`（坑见 [MIGRATION_4_5]）。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "todos", "endAtMillis")) {
                    db.execSQL("ALTER TABLE todos ADD COLUMN endAtMillis INTEGER DEFAULT NULL")
                }
            }
        }

        /**
         * 新增 [FocusSession] 表（v1.0.1 专注计时）。
         *
         * 纯建表，**不动 todos** —— 老用户的待办一条都不会少，也不存在列默认值那种坑
         * （见 [MIGRATION_4_5]）。`IF NOT EXISTS` 是幂等保险：迁移中途失败但没回滚的设备
         * 再跑一次不会抛 "table already exists"。
         *
         * ★ 列定义必须和实体逐字对齐（类型、NOT NULL、主键自增），否则 Room 迁移后校验
         *   不通过会直接抛 `Migration didn't properly handle: focus_sessions(...)` ——
         *   和 0.9.3 那次闪退是同一类错误，只是这次错在建表而不是加列。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`startedAtMillis` INTEGER NOT NULL, " +
                        "`endedAtMillis` INTEGER NOT NULL, " +
                        "`plannedSeconds` INTEGER NOT NULL, " +
                        "`actualSeconds` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        /**
         * 迁移链唯一清单（v1.0.10）。
         * instrumented 的 `MigrationTestHelper` 必须拿**同一组** Migration 对象，
         * 否则测的是"另一条链"；所以从 private 提成数组，运行时和两侧测试共用。
         */
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
        )

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oneline.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build().also { INSTANCE = it }
            }
        }
    }
}