package com.example.jihe_schedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.model.Todo
import com.example.jihe_schedule.model.ScheduleInfo
// 🔥 修改 1：在 entities 数组里加上 Course::class
// 🔥 修改 2：把 version 改成 2 (因为我们增加了新表，数据库版本必须升级)
@Database(entities = [Todo::class, Course::class,ScheduleInfo::class], version = 4, exportSchema = false)
// 🔥 修改 3：注册类型转换器 (处理 List<Int> 转 String)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // 暴露 TodoDAO
    abstract fun todoDao(): TodoDao

    // 🔥 修改 4：暴露 CourseDao 给外部使用
    abstract fun courseDao(): CourseDao

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jihe_database"
                )
                    // 🔥 修改 5：开发阶段神器！
                    // 如果数据库结构变了(比如版本1->2)，直接清空重建，防止 App 闪退报错。
                    // (注意：这意味着更新 App 后，之前的测试数据会被清空，但在开发期这很方便)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}