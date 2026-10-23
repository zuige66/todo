package com.lirui.oneline.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lirui.oneline.data.model.Todo

@Database(
    entities = [Todo::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

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
         * Add [Todo.deletedAt]（回收站；可空，null = 未删除）。
         *
         * 老数据全部是"没删过"，deletedAt 自然为 null，所有查询的 `deletedAt IS NULL`
         * 条件都成立，行为零变化。列必须是可空的 —— 没有默认值的 ALTER 在旧行上会失败。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN deletedAt INTEGER")
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oneline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}