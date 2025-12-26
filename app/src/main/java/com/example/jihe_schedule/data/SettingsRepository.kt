package com.example.jihe_schedule.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val PERIOD_MORNING = intPreferencesKey("period_morning")
        val PERIOD_AFTERNOON = intPreferencesKey("period_afternoon")
        val PERIOD_EVENING = intPreferencesKey("period_evening")

        val SHOW_SATURDAY = booleanPreferencesKey("show_saturday")
        val SHOW_SUNDAY = booleanPreferencesKey("show_sunday")

        val BG_IMAGE_URI = stringPreferencesKey("bg_image_uri")
        val BG_OPACITY = floatPreferencesKey("bg_opacity")
        val BORDER_OPACITY = floatPreferencesKey("border_opacity")
        val COURSE_OPACITY = floatPreferencesKey("course_opacity")

        val TODO_BG_IMAGE_URI = stringPreferencesKey("todo_bg_image_uri")
        val TODO_BG_OPACITY = floatPreferencesKey("todo_bg_opacity")
        val TODO_CALENDAR_OPACITY = floatPreferencesKey("todo_calendar_opacity")
        val TODO_CARD_OPACITY = floatPreferencesKey("todo_card_opacity")

        val SCHEDULE_TRANSPARENT_HEADER = booleanPreferencesKey("schedule_transparent_header")
        val TODO_TRANSPARENT_HEADER = booleanPreferencesKey("todo_transparent_header")

        val SCHEDULE_FORCE_DARK = booleanPreferencesKey("schedule_force_dark")
        val TODO_FORCE_DARK = booleanPreferencesKey("todo_force_dark")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_COLOR = stringPreferencesKey("theme_color")

        val COURSE_TIMES = stringPreferencesKey("course_times")

        // 显示设置 Keys
        val SHOW_COURSE_IN_APP = booleanPreferencesKey("show_course_in_app")
        val SHOW_TODO_IN_APP = booleanPreferencesKey("show_todo_in_app")
        val SEPARATE_MODE = booleanPreferencesKey("separate_mode")

        val SHOW_COURSE_IN_WIDGET = booleanPreferencesKey("show_course_in_widget")
        val SHOW_TODO_IN_WIDGET = booleanPreferencesKey("show_todo_in_widget")

        // 🔥🔥🔥 新增：通知设置 Keys 🔥🔥🔥
        val ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications") // 总开关
        val ENABLE_COURSE_NOTIFY = booleanPreferencesKey("enable_course_notify") // 课程提醒开关
        val COURSE_NOTIFY_TIME = intPreferencesKey("course_notify_time") // 课前多少分钟提醒 (默认20)
    }

    // --- 读取数据 ---
    val periodMorning: Flow<Int> = context.dataStore.data.map { it[PERIOD_MORNING] ?: 4 }
    val periodAfternoon: Flow<Int> = context.dataStore.data.map { it[PERIOD_AFTERNOON] ?: 4 }
    val periodEvening: Flow<Int> = context.dataStore.data.map { it[PERIOD_EVENING] ?: 3 }

    val maxPeriodCount: Flow<Int> = context.dataStore.data.map {
        (it[PERIOD_MORNING] ?: 4) + (it[PERIOD_AFTERNOON] ?: 4) + (it[PERIOD_EVENING] ?: 3)
    }

    val showSaturday: Flow<Boolean> = context.dataStore.data.map { it[SHOW_SATURDAY] ?: true }
    val showSunday: Flow<Boolean> = context.dataStore.data.map { it[SHOW_SUNDAY] ?: true }
    val showWeekend: Flow<Boolean> = context.dataStore.data.map { (it[SHOW_SATURDAY] ?: true) || (it[SHOW_SUNDAY] ?: true) }

    val bgImageUri: Flow<String?> = context.dataStore.data.map { it[BG_IMAGE_URI] }
    val bgOpacity: Flow<Float> = context.dataStore.data.map { it[BG_OPACITY] ?: 0.5f }
    val borderOpacity: Flow<Float> = context.dataStore.data.map { it[BORDER_OPACITY] ?: 0.1f }
    val courseOpacity: Flow<Float> = context.dataStore.data.map { it[COURSE_OPACITY] ?: 0.85f }

    val todoBgImageUri: Flow<String?> = context.dataStore.data.map { it[TODO_BG_IMAGE_URI] }
    val todoBgOpacity: Flow<Float> = context.dataStore.data.map { it[TODO_BG_OPACITY] ?: 0.5f }
    val todoCalendarOpacity: Flow<Float> = context.dataStore.data.map { it[TODO_CALENDAR_OPACITY] ?: 0.6f }
    val todoCardOpacity: Flow<Float> = context.dataStore.data.map { it[TODO_CARD_OPACITY] ?: 0.6f }

    val scheduleTransparentHeader: Flow<Boolean> = context.dataStore.data.map { it[SCHEDULE_TRANSPARENT_HEADER] ?: false }
    val todoTransparentHeader: Flow<Boolean> = context.dataStore.data.map { it[TODO_TRANSPARENT_HEADER] ?: false }

    val scheduleForceDark: Flow<Boolean> = context.dataStore.data.map { it[SCHEDULE_FORCE_DARK] ?: false }
    val todoForceDark: Flow<Boolean> = context.dataStore.data.map { it[TODO_FORCE_DARK] ?: false }

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "auto" }
    val themeColor: Flow<String> = context.dataStore.data.map { it[THEME_COLOR] ?: "#6650a4" }

    val courseTimes: Flow<List<CourseTime>> = context.dataStore.data.map { preferences ->
        val str = preferences[COURSE_TIMES] ?: ""
        if (str.isNotEmpty()) CourseTime.stringToList(str) else emptyList()
    }

    // 显示设置
    val showCourseInApp: Flow<Boolean> = context.dataStore.data.map { it[SHOW_COURSE_IN_APP] ?: true }
    val showTodoInApp: Flow<Boolean> = context.dataStore.data.map { it[SHOW_TODO_IN_APP] ?: true }
    val separateMode: Flow<Boolean> = context.dataStore.data.map { it[SEPARATE_MODE] ?: true }

    val showCourseInWidget: Flow<Boolean> = context.dataStore.data.map { it[SHOW_COURSE_IN_WIDGET] ?: true }
    val showTodoInWidget: Flow<Boolean> = context.dataStore.data.map { it[SHOW_TODO_IN_WIDGET] ?: true }

    // 🔥🔥🔥 新增：读取通知设置 🔥🔥🔥
    val enableNotifications: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_NOTIFICATIONS] ?: true }
    val enableCourseNotify: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_COURSE_NOTIFY] ?: true }
    val courseNotifyTime: Flow<Int> = context.dataStore.data.map { it[COURSE_NOTIFY_TIME] ?: 20 }

    // --- 写入方法 ---
    suspend fun updatePeriods(morning: Int, afternoon: Int, evening: Int) {
        context.dataStore.edit {
            it[PERIOD_MORNING] = morning
            it[PERIOD_AFTERNOON] = afternoon
            it[PERIOD_EVENING] = evening
        }
    }

    suspend fun updateWeekend(saturday: Boolean, sunday: Boolean) {
        context.dataStore.edit {
            it[SHOW_SATURDAY] = saturday
            it[SHOW_SUNDAY] = sunday
        }
    }

    suspend fun updateBgImage(uri: String?) {
        context.dataStore.edit { if (uri == null) it.remove(BG_IMAGE_URI) else it[BG_IMAGE_URI] = uri }
    }

    suspend fun updateOpacities(bg: Float, border: Float, course: Float) {
        context.dataStore.edit {
            it[BG_OPACITY] = bg
            it[BORDER_OPACITY] = border
            it[COURSE_OPACITY] = course
        }
    }

    suspend fun updateTodoBgImage(uri: String?) {
        context.dataStore.edit { if (uri == null) it.remove(TODO_BG_IMAGE_URI) else it[TODO_BG_IMAGE_URI] = uri }
    }

    suspend fun updateTodoOpacity(opacity: Float) {
        context.dataStore.edit { it[TODO_BG_OPACITY] = opacity }
    }

    suspend fun updateTodoSpecificOpacities(calendar: Float, card: Float) {
        context.dataStore.edit {
            it[TODO_CALENDAR_OPACITY] = calendar
            it[TODO_CARD_OPACITY] = card
        }
    }

    suspend fun updateScheduleBgSettings(transparent: Boolean, forceDark: Boolean) {
        context.dataStore.edit {
            it[SCHEDULE_TRANSPARENT_HEADER] = transparent
            it[SCHEDULE_FORCE_DARK] = forceDark
        }
    }

    suspend fun updateTodoBgSettings(transparent: Boolean, forceDark: Boolean) {
        context.dataStore.edit {
            it[TODO_TRANSPARENT_HEADER] = transparent
            it[TODO_FORCE_DARK] = forceDark
        }
    }

    suspend fun updateTheme(mode: String, color: String) {
        context.dataStore.edit {
            it[THEME_MODE] = mode
            it[THEME_COLOR] = color
        }
    }

    suspend fun saveCourseTimes(times: List<CourseTime>) {
        val str = CourseTime.listToString(times)
        context.dataStore.edit { it[COURSE_TIMES] = str }
    }

    suspend fun updateAppDisplaySettings(showCourse: Boolean, showTodo: Boolean, separate: Boolean) {
        context.dataStore.edit {
            it[SHOW_COURSE_IN_APP] = showCourse
            it[SHOW_TODO_IN_APP] = showTodo
            it[SEPARATE_MODE] = separate
        }
    }

    suspend fun updateWidgetDisplaySettings(showCourse: Boolean, showTodo: Boolean) {
        context.dataStore.edit {
            it[SHOW_COURSE_IN_WIDGET] = showCourse
            it[SHOW_TODO_IN_WIDGET] = showTodo
        }
    }

    // 🔥🔥🔥 新增：更新通知设置 🔥🔥🔥
    suspend fun updateNotificationSettings(enabled: Boolean, courseEnabled: Boolean, time: Int) {
        context.dataStore.edit {
            it[ENABLE_NOTIFICATIONS] = enabled
            it[ENABLE_COURSE_NOTIFY] = courseEnabled
            it[COURSE_NOTIFY_TIME] = time
        }
    }
}