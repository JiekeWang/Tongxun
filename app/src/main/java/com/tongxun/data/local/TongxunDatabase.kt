package com.tongxun.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.tongxun.data.local.dao.*
import com.tongxun.data.local.entity.*
import com.tongxun.data.local.migration.DatabaseMigrations

@Database(
    entities = [
        UserEntity::class,
        FriendEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        GroupEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TongxunDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun friendDao(): FriendDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun groupDao(): GroupDao
    
    companion object {
        @Volatile
        private var INSTANCE: TongxunDatabase? = null
        
        fun getDatabase(context: Context): TongxunDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TongxunDatabase::class.java,
                    "tongxun_database"
                )
                    .addMigrations(*DatabaseMigrations.getAllMigrations())
                    .fallbackToDestructiveMigration() // 仅在开发阶段使用，生产环境应移除
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

