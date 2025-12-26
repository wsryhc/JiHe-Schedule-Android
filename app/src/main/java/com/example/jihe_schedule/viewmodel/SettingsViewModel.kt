package com.example.jihe_schedule.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jihe_schedule.JiHeApplication
import com.example.jihe_schedule.data.CourseTime
import com.example.jihe_schedule.worker.ReminderWorker
// 🔥 引用 util 包下的 Helper
import com.example.jihe_schedule.util.WidgetHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as JiHeApplication
    private val repository = app.settingsRepository
    private val todoDao = app.database.todoDao()
    private val contentResolver = application.contentResolver

    val periodMorning = repository.periodMorning.stateIn(viewModelScope, SharingStarted.Eagerly, 4)
    val periodAfternoon = repository.periodAfternoon.stateIn(viewModelScope, SharingStarted.Eagerly, 4)
    val periodEvening = repository.periodEvening.stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val maxPeriodCount = repository.maxPeriodCount.stateIn(viewModelScope, SharingStarted.Eagerly, 11)

    val showSaturday = repository.showSaturday.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showSunday = repository.showSunday.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showWeekend = repository.showWeekend.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val bgImageUri = repository.bgImageUri.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bgOpacity = repository.bgOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.5f)
    val borderOpacity = repository.borderOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.1f)
    val courseOpacity = repository.courseOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.85f)

    val todoBgImageUri = repository.todoBgImageUri.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val todoBgOpacity = repository.todoBgOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.5f)
    val todoCalendarOpacity = repository.todoCalendarOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.6f)
    val todoCardOpacity = repository.todoCardOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.6f)

    val scheduleTransparentHeader = repository.scheduleTransparentHeader.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val todoTransparentHeader = repository.todoTransparentHeader.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val scheduleForceDark = repository.scheduleForceDark.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val todoForceDark = repository.todoForceDark.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode = repository.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "auto")
    val themeColor = repository.themeColor.stateIn(viewModelScope, SharingStarted.Eagerly, "#6650a4")

    val courseTimes = repository.courseTimes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val showCourseInApp = repository.showCourseInApp.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showTodoInApp = repository.showTodoInApp.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val separateMode = repository.separateMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showCourseInWidget = repository.showCourseInWidget.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showTodoInWidget = repository.showTodoInWidget.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val enableNotifications = repository.enableNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val enableCourseNotify = repository.enableCourseNotify.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val courseNotifyTime = repository.courseNotifyTime.stateIn(viewModelScope, SharingStarted.Eagerly, 20)

    fun updatePeriods(m: Int, a: Int, e: Int) {
        viewModelScope.launch { repository.updatePeriods(m, a, e) }
    }

    fun updateWeekend(sat: Boolean, sun: Boolean) {
        viewModelScope.launch { repository.updateWeekend(sat, sun) }
    }

    fun setBackgroundImage(uri: Uri?) {
        viewModelScope.launch {
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }
            }
            repository.updateBgImage(uri?.toString())
        }
    }

    fun updateOpacities(bg: Float, border: Float, course: Float) {
        viewModelScope.launch { repository.updateOpacities(bg, border, course) }
    }

    fun setTodoBackgroundImage(uri: Uri?) {
        viewModelScope.launch {
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }
            }
            repository.updateTodoBgImage(uri?.toString())
        }
    }

    fun updateTodoOpacity(opacity: Float) {
        viewModelScope.launch { repository.updateTodoOpacity(opacity) }
    }

    fun updateTodoSpecificOpacities(calendar: Float, card: Float) {
        viewModelScope.launch { repository.updateTodoSpecificOpacities(calendar, card) }
    }

    fun updateScheduleBgSettings(transparent: Boolean, forceDark: Boolean) {
        viewModelScope.launch { repository.updateScheduleBgSettings(transparent, forceDark) }
    }

    fun updateTodoBgSettings(transparent: Boolean, forceDark: Boolean) {
        viewModelScope.launch { repository.updateTodoBgSettings(transparent, forceDark) }
    }

    fun updateTheme(mode: String, color: String) {
        viewModelScope.launch { repository.updateTheme(mode, color) }
    }

    fun saveCourseTimes(times: List<CourseTime>) {
        viewModelScope.launch {
            repository.saveCourseTimes(times)
            ReminderWorker.startImmediately(getApplication())
        }
    }

    fun clearAllData() {
        viewModelScope.launch { todoDao.clearAll() }
    }

    fun updateAppDisplaySettings(showCourse: Boolean, showTodo: Boolean, separate: Boolean) {
        viewModelScope.launch { repository.updateAppDisplaySettings(showCourse, showTodo, separate) }
    }

    // 🔥 修改：使用 WidgetHelper 刷新
    fun updateWidgetDisplaySettings(showCourse: Boolean, showTodo: Boolean) {
        viewModelScope.launch {
            repository.updateWidgetDisplaySettings(showCourse, showTodo)
            WidgetHelper.refreshNow(getApplication())
        }
    }

    fun updateNotificationSettings(enabled: Boolean, courseEnabled: Boolean, time: Int) {
        viewModelScope.launch {
            repository.updateNotificationSettings(enabled, courseEnabled, time)
            ReminderWorker.startImmediately(getApplication())
        }
    }
}