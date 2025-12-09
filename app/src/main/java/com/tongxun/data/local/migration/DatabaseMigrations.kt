package com.tongxun.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    
    /**
     * Migration from version 1 to 2
     * 为 friends 表添加 nickname 和 avatar 字段
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 为 friends 表添加 nickname 字段
            db.execSQL("ALTER TABLE friends ADD COLUMN nickname TEXT")
            // 为 friends 表添加 avatar 字段
            db.execSQL("ALTER TABLE friends ADD COLUMN avatar TEXT")
        }
    }
    
    /**
     * Migration from version 2 to 3
     * 未来版本迁移示例
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 未来迁移逻辑
        }
    }
    
    /**
     * 获取所有迁移
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3
        )
    }
}

