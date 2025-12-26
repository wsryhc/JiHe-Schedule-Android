package com.example.jihe_schedule.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.jihe_schedule.JiHeApplication
import com.example.jihe_schedule.R
import com.example.jihe_schedule.data.AppDatabase
import com.example.jihe_schedule.model.ItemType
import com.example.jihe_schedule.model.ScheduleItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class NativeWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NativeWidgetFactory(this.applicationContext)
    }
}

class NativeWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    data class WidgetItem(
        val display: ScheduleItem,
        val originalId: String = "",
        val isOngoing: Boolean = false
    )

    private var items: List<WidgetItem> = emptyList()

    private var titleColor = Color.parseColor("#000000")
    private var subColor = Color.parseColor("#666666")

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking {
            try {
                // 🔥 修改点：直接使用字符串读取，不引用外部常量，解决 Unresolved reference 报错
                val prefs = context.getSharedPreferences("widget_theme_state", Context.MODE_PRIVATE)
                val isDark = prefs.getBoolean("is_dark_mode", false)

                if (isDark) {
                    titleColor = Color.parseColor("#E0E0E0") // 深色模式
                    subColor = Color.parseColor("#888888")
                } else {
                    titleColor = Color.parseColor("#1C1B1F") // 浅色模式
                    subColor = Color.parseColor("#666666")
                }

                // --- 2. 读取数据 ---
                val app = context.applicationContext as JiHeApplication
                val db = AppDatabase.getDatabase(context)
                val repository = app.settingsRepository

                val showCourse = repository.showCourseInWidget.firstOrNull() ?: true
                val showTodo = repository.showTodoInWidget.firstOrNull() ?: true
                val timeList = repository.courseTimes.firstOrNull() ?: emptyList()

                val today = LocalDate.now()
                val nowTime = LocalTime.now()

                var targetDate = today
                var isShowingTomorrow = false
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
                    val currentWeek = (ChronoUnit.DAYS.between(start, today) / 7).toInt() + 1
                    val allCourses = courseDao.getCoursesDirect(selectedSchedule.id)
                    val todayCourses = allCourses.filter { it.day == today.dayOfWeek.value && it.weeks.contains(currentWeek) }

                    val hasRemaining = todayCourses.any { course ->
                        val endTimeStr = timeList.getOrNull(course.endPeriod - 1)?.end ?: "23:59"
                        try { nowTime.isBefore(LocalTime.parse(endTimeStr)) } catch (e: Exception) { true }
                    }
                    if (hasRemaining) hasActiveItemsToday = true
                }

                if (!hasActiveItemsToday && showCourse && selectedSchedule != null) {
                    val tomorrow = today.plusDays(1)
                    val start = LocalDate.parse(selectedSchedule.termStartDate)
                    val tomorrowWeek = (ChronoUnit.DAYS.between(start, tomorrow) / 7).toInt() + 1
                    val courseDao = db.courseDao()
                    val allCourses = courseDao.getCoursesDirect(selectedSchedule.id)
                    val tomorrowCourses = allCourses.filter { it.day == tomorrow.dayOfWeek.value && it.weeks.contains(tomorrowWeek) }

                    if (tomorrowCourses.isNotEmpty()) {
                        targetDate = tomorrow
                        isShowingTomorrow = true
                    }
                }

                val newItems = mutableListOf<WidgetItem>()

                if (showTodo) {
                    val todoDao = db.todoDao()
                    val todos = todoDao.getTodosByDate(targetDate.toString()).firstOrNull() ?: emptyList()
                    val activeTodos = todos.filter { !it.completed }

                    newItems.addAll(activeTodos.map { t ->
                        val color = parseColor(t.color)
                        WidgetItem(
                            display = ScheduleItem(t.title, "待办", t.startTime, t.endTime, false, ItemType.TODO, color),
                            originalId = t.id
                        )
                    })
                }

                if (showCourse && selectedSchedule != null) {
                    val courseDao = db.courseDao()
                    val start = LocalDate.parse(selectedSchedule.termStartDate)
                    val targetWeek = (ChronoUnit.DAYS.between(start, targetDate) / 7).toInt() + 1
                    val allCourses = courseDao.getCoursesDirect(selectedSchedule.id)
                    val courses = allCourses.filter { it.day == targetDate.dayOfWeek.value && it.weeks.contains(targetWeek) }

                    courses.forEach { course ->
                        val startTimeStr = timeList.getOrNull(course.startPeriod - 1)?.start ?: "00:00"
                        val endTimeStr = timeList.getOrNull(course.endPeriod - 1)?.end ?: "23:59"

                        try {
                            val startTime = LocalTime.parse(startTimeStr)
                            val endTime = LocalTime.parse(endTimeStr)
                            val showThisCourse = if (isShowingTomorrow) true else nowTime.isBefore(endTime)

                            if (showThisCourse) {
                                val isOngoing = if (isShowingTomorrow) false else (nowTime.isAfter(startTime) && nowTime.isBefore(endTime))
                                val color = parseColor(course.color)
                                newItems.add(WidgetItem(
                                    display = ScheduleItem(course.name, course.classroom.ifEmpty { "未设置" }, startTimeStr, endTimeStr, false, ItemType.COURSE, color),
                                    isOngoing = isOngoing
                                ))
                            }
                        } catch (e: Exception) { }
                    }
                }
                newItems.sortBy { it.display.startTime }
                items = newItems

            } catch (e: Exception) {
                Log.e("JiHeNative", "Data Load Error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {}
    override fun getCount(): Int = items.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_native_item)

        val widgetItem = items[position]
        val item = widgetItem.display
        val views = RemoteViews(context.packageName, R.layout.widget_native_item)

        // 1. 设置文字颜色 (跟随系统)
        views.setTextViewText(R.id.item_start_time, item.startTime)
        views.setTextColor(R.id.item_start_time, titleColor)

        views.setTextViewText(R.id.item_end_time, item.endTime)
        views.setTextColor(R.id.item_end_time, subColor)

        views.setTextViewText(R.id.item_title, item.title)
        views.setTextColor(R.id.item_title, titleColor)

        views.setTextViewText(R.id.item_subtitle, item.subTitle)
        views.setTextColor(R.id.item_subtitle, subColor)

        // 2. 左侧颜色条
        views.setInt(R.id.item_color_bar, "setColorFilter", item.themeColor.toInt())

        // 3. 处理 "上课中"
        if (item.type == ItemType.COURSE && widgetItem.isOngoing) {
            views.setViewVisibility(R.id.item_ongoing_badge_container, View.VISIBLE)

            // 背景继续用课程颜色
            val bgColor = item.themeColor.toInt()
            views.setInt(R.id.item_ongoing_bg, "setColorFilter", bgColor)

            // 🔥 文字颜色强制跟随系统 (titleColor)，无视课程颜色
            views.setTextColor(R.id.item_ongoing_text, titleColor)
        } else {
            views.setViewVisibility(R.id.item_ongoing_badge_container, View.GONE)
        }

        // 4. Checkbox
        if (item.type == ItemType.TODO) {
            views.setViewVisibility(R.id.item_check_box, View.VISIBLE)
            views.setInt(R.id.item_check_box, "setColorFilter", subColor)
            val todoIntent = Intent()
            todoIntent.putExtra("todo_id", widgetItem.originalId)
            todoIntent.putExtra("action_type", "toggle_todo")
            views.setOnClickFillInIntent(R.id.item_check_box, todoIntent)
        } else {
            views.setViewVisibility(R.id.item_check_box, View.GONE)
        }

        // 5. 整体跳转
        val openAppIntent = Intent()
        openAppIntent.putExtra("action_type", "open_main")
        views.setOnClickFillInIntent(R.id.widget_item_container, openAppIntent)

        return views
    }

    private fun parseColor(hex: String?): Long {
        return try { Color.parseColor(hex).toLong() } catch (e: Exception) { 0xFF2196F3 }
    }
}