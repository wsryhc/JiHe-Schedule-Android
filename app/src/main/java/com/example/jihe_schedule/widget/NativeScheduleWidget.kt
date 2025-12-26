package com.example.jihe_schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import com.example.jihe_schedule.JiHeApplication
import com.example.jihe_schedule.MainActivity
import com.example.jihe_schedule.R
import com.example.jihe_schedule.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

open class NativeScheduleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_ITEM_CLICK = "com.example.jihe_schedule.ACTION_WIDGET_ITEM_CLICK"
        const val FORCE_UPDATE = "com.example.jihe_schedule.FORCE_UPDATE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {}

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || action == FORCE_UPDATE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    performUpdate(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }

        if (action == ACTION_ITEM_CLICK) {
            val type = intent.getStringExtra("action_type")
            val todoId = intent.getStringExtra("todo_id")

            if (type == "toggle_todo" && !todoId.isNullOrEmpty()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val todoDao = db.todoDao()
                        val allTodos = todoDao.getAllTodos().firstOrNull()
                        val targetTodo = allTodos?.find { it.id == todoId }

                        if (targetTodo != null) {
                            val updatedTodo = targetTodo.copy(completed = true)
                            todoDao.updateTodo(updatedTodo)
                            performUpdate(context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else if (type == "open_main") {
                try {
                    val appIntent = Intent(context, MainActivity::class.java)
                    appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(appIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun performUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, this.javaClass)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(component)

        for (appWidgetId in appWidgetIds) {
            updateAppWidgetUI(context, appWidgetManager, appWidgetId)
        }

        withContext(Dispatchers.Main) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
        }
    }

    private suspend fun updateAppWidgetUI(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_native_layout)
            val app = context.applicationContext as JiHeApplication
            val repository = app.settingsRepository
            val db = AppDatabase.getDatabase(context)

            // --- 1. 读取并确定深色模式状态 ---
            val themeColorHex = repository.themeColor.firstOrNull() ?: "#2196F3"
            val themeMode = repository.themeMode.firstOrNull() ?: "auto"
            val showCourse = repository.showCourseInWidget.firstOrNull() ?: true
            val showTodo = repository.showTodoInWidget.firstOrNull() ?: true
            val timeList = repository.courseTimes.firstOrNull() ?: emptyList()

            val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }

            // 保存状态供 Service 使用
            context.getSharedPreferences("widget_theme_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_dark_mode", isDark)
                .commit()

            // --- 2. 设置背景和颜色 ---

            // 🔥 这里的 setBackgroundResource 会调用 widget_bg_dark/light.xml，从而实现圆角
            val bgRes = if (isDark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            val titleColor = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#000000")
            val subColor = if (isDark) Color.parseColor("#888888") else Color.parseColor("#666666")
            val themeColor = try { Color.parseColor(themeColorHex) } catch (e: Exception) { Color.parseColor("#2196F3") }

            // 文字颜色
            views.setTextColor(R.id.widget_title, titleColor)
            views.setTextColor(R.id.widget_date, subColor)
            views.setTextColor(R.id.widget_empty_view, subColor)

            // 装饰条颜色
            views.setInt(R.id.widget_accent_bar, "setBackgroundColor", themeColor)
            views.setTextColor(R.id.widget_status_text, themeColor)

            // --- 3. 智能日期判断 ---
            val today = LocalDate.now()
            var displayDate = today
            var titleText = "今日安排"
            var hasActiveItemsToday = false

            if (showTodo) {
                val todoDao = db.todoDao()
                val todayTodos = todoDao.getTodosByDate(today.toString()).firstOrNull() ?: emptyList()
                if (todayTodos.any { !it.completed }) hasActiveItemsToday = true
            }

            val scheduleDao = db.scheduleDao()
            val selectedSchedule = scheduleDao.getSelectedSchedule().firstOrNull()

            if (showCourse && !hasActiveItemsToday && selectedSchedule != null) {
                val courseDao = db.courseDao()
                val start = LocalDate.parse(selectedSchedule.termStartDate)
                val days = ChronoUnit.DAYS.between(start, today)
                val currentWeek = (days / 7).toInt() + 1
                val allCourses = courseDao.getCoursesDirect(selectedSchedule.id)
                val todayCourses = allCourses.filter { it.day == today.dayOfWeek.value && it.weeks.contains(currentWeek) }
                val hasRemainingCourses = todayCourses.any { course ->
                    val endTimeStr = timeList.getOrNull(course.endPeriod - 1)?.end ?: "23:59"
                    try { LocalTime.now().isBefore(LocalTime.parse(endTimeStr)) } catch (e: Exception) { true }
                }
                if (hasRemainingCourses) hasActiveItemsToday = true
            }

            if (!hasActiveItemsToday && showCourse && selectedSchedule != null) {
                val tomorrow = today.plusDays(1)
                val start = LocalDate.parse(selectedSchedule.termStartDate)
                val days = ChronoUnit.DAYS.between(start, tomorrow)
                val tomorrowWeek = (days / 7).toInt() + 1
                val courseDao = db.courseDao()
                val allCourses = courseDao.getCoursesDirect(selectedSchedule.id)
                val tomorrowCourses = allCourses.filter { it.day == tomorrow.dayOfWeek.value && it.weeks.contains(tomorrowWeek) }
                if (tomorrowCourses.isNotEmpty()) {
                    displayDate = tomorrow
                    titleText = "明日课程"
                }
            }

            views.setTextViewText(R.id.widget_title, titleText)
            val dateStr = displayDate.format(DateTimeFormatter.ofPattern("MM月dd日 E", Locale.CHINA))
            views.setTextViewText(R.id.widget_date, dateStr)

            var statusText = "假期中"
            if (selectedSchedule != null) {
                try {
                    val start = LocalDate.parse(selectedSchedule.termStartDate)
                    val days = ChronoUnit.DAYS.between(start, displayDate)
                    val week = (days / 7).toInt() + 1
                    if (week in 1..selectedSchedule.totalWeeks) statusText = "第${week}周"
                } catch (e: Exception) {}
            }
            views.setTextViewText(R.id.widget_status_text, statusText)

            // --- 4. 点击事件与Adapter ---
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_empty_view, pendingIntent)

            val serviceIntent = Intent(context, NativeWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME) + "/${System.currentTimeMillis()}")

            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)

            val clickIntent = Intent(context, this.javaClass)
            clickIntent.action = ACTION_ITEM_CLICK
            val clickPendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.widget_list_view, clickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}