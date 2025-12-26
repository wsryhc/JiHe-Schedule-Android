package com.example.jihe_schedule

import android.app.Application
import android.util.Log
import androidx.room.InvalidationTracker
import com.example.jihe_schedule.data.AppDatabase
import com.example.jihe_schedule.data.SettingsRepository
// 🔥 修改引用路径：从 glance 改为 util
import com.example.jihe_schedule.util.WidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class JiHeApplication : Application() {

    // 全局协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载单例
    val database by lazy { AppDatabase.getDatabase(this) }
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()

        // 🔥 1. 启动数据库监听 (内容变动即刷新)
        startDatabaseObserver()

        // 🔥 2. 启动设置监听 (样式/开关变动即刷新)
        startSettingsObserver()
    }

    /**
     * 监听数据库表变动
     * 只要 todos, courses, schedules 这三张表有任何增删改，就会触发
     */
    private fun startDatabaseObserver() {
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("todos", "courses", "schedules") {
                override fun onInvalidated(tables: Set<String>) {
                    Log.d("JiHeWidget", "👀 数据库变动: $tables -> 触发刷新")
                    // 立即刷新，不再延迟
                    WidgetHelper.refreshNow(this@JiHeApplication)
                }
            }
        )
    }

    /**
     * 监听设置项变动
     * 只要主题色、显示开关、时间设置有变化，就会触发
     */
    private fun startSettingsObserver() {
        applicationScope.launch {
            // 将所有影响小组件外观的 Flow 合并监听
            // merge 会把不同类型的 Flow 合并成一个流，只要其中任何一个发出新值，下游就会收到通知
            merge(
                settingsRepository.themeColor,         // 主题色
                settingsRepository.courseTimes,        // 课程时间
                settingsRepository.showCourseInWidget, // 课程显示开关
                settingsRepository.showTodoInWidget    // 待办显示开关
            )
                .distinctUntilChanged() // 防抖：只有值真的变了才刷新
                .collectLatest {
                    Log.d("JiHeWidget", "⚙️ 设置变动 -> 触发刷新")
                    WidgetHelper.refreshNow(this@JiHeApplication)
                }
        }
    }
}