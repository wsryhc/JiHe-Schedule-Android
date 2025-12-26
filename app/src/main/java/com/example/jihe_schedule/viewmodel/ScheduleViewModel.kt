package com.example.jihe_schedule.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jihe_schedule.JiHeApplication
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.alarm.AlarmScheduler
import com.example.jihe_schedule.data.CourseTime
import com.example.jihe_schedule.worker.ReminderWorker
// 🔥 引用 util 包下的 Helper
import com.example.jihe_schedule.util.WidgetHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as JiHeApplication
    private val scheduleDao = app.database.scheduleDao()
    private val courseDao = app.database.courseDao()
    private val settingsRepo = app.settingsRepository

    // 配置项
    val maxPeriodCount = settingsRepo.maxPeriodCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 12)
    val periodMorning = settingsRepo.periodMorning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)
    val periodAfternoon = settingsRepo.periodAfternoon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)
    val periodEvening = settingsRepo.periodEvening
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val showWeekend = settingsRepo.showWeekend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val courseTimes = settingsRepo.courseTimes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 数据流
    val allSchedules = scheduleDao.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSchedule = scheduleDao.getSelectedSchedule()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedWeek = MutableStateFlow(1)
    val selectedWeek = _selectedWeek.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentCourses = currentSchedule.flatMapLatest { schedule ->
        if (schedule == null) flowOf(emptyList())
        else courseDao.getCoursesByScheduleId(schedule.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allSchedules.collect { list ->
                if (list.isNotEmpty()) {
                    val hasSelection = list.any { it.isSelected }
                    if (!hasSelection) {
                        scheduleDao.switchActiveSchedule(list[0].id)
                    }
                }
            }
        }

        viewModelScope.launch {
            currentSchedule.collect { schedule ->
                if (schedule != null) {
                    val week = calculateCurrentWeek(schedule.termStartDate)
                    _selectedWeek.value = week.coerceIn(1, schedule.totalWeeks)
                }
            }
        }

        viewModelScope.launch {
            combine(courseTimes, periodMorning, periodAfternoon, periodEvening) { times, am, pm, nm ->
                val total = am + pm + nm
                if (times.isEmpty() || times.size != total) {
                    generateAndSaveDefaultTimes(am, pm, nm)
                }
            }.collect()
        }

        ReminderWorker.startImmediately(getApplication())
    }

    private fun generateAndSaveDefaultTimes(am: Int, pm: Int, nm: Int) {
        val newTimes = mutableListOf<CourseTime>()
        val duration = 45L
        val breakTime = 10L
        var hitMidnight = false

        fun addSection(count: Int, startHour: Int) {
            var time = LocalTime.of(startHour, 0)
            repeat(count) {
                val endTime = time.plusMinutes(duration)
                if (endTime.isBefore(time) || endTime == LocalTime.MIDNIGHT) {
                    newTimes.add(CourseTime.from(time, LocalTime.of(23, 59)))
                    hitMidnight = true
                } else {
                    newTimes.add(CourseTime.from(time, endTime))
                    val nextStart = endTime.plusMinutes(breakTime)
                    if (nextStart.isBefore(endTime)) hitMidnight = true
                    time = nextStart
                }
            }
        }

        addSection(am, 8)
        addSection(pm, 14)
        addSection(nm, 19)

        viewModelScope.launch {
            settingsRepo.saveCourseTimes(newTimes)
            ReminderWorker.startImmediately(getApplication())
            if (hitMidnight) {
                Toast.makeText(getApplication(), "提示：部分课程时间超过午夜，已自动截止于 23:59", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateSelectedWeek(week: Int) {
        currentSchedule.value?.let {
            if (week in 1..it.totalWeeks) {
                _selectedWeek.value = week
            }
        }
    }

    private fun calculateCurrentWeek(startDateStr: String): Int {
        try {
            val start = LocalDate.parse(startDateStr)
            val now = LocalDate.now()
            val daysDiff = ChronoUnit.DAYS.between(start, now)
            if (daysDiff < 0) return 1
            return (daysDiff / 7).toInt() + 1
        } catch (e: Exception) {
            return 1
        }
    }

    // 🔥 刷新逻辑统一走 WidgetHelper
    private fun updateWidget() {
        viewModelScope.launch {
            try {
                WidgetHelper.refreshNow(getApplication())
            } catch (_: Exception) {}
        }
    }

    fun insertCourse(course: Course) {
        viewModelScope.launch {
            courseDao.insertCourse(course)
            updateWidget()
            ReminderWorker.startImmediately(getApplication())
        }
    }

    fun updateCourse(course: Course) {
        viewModelScope.launch {
            courseDao.updateCourse(course)
            updateWidget()
            ReminderWorker.startImmediately(getApplication())
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseDao.deleteCourse(course)
            AlarmScheduler.cancelAlarmForCourse(getApplication(), course)
            updateWidget()
            ReminderWorker.startImmediately(getApplication())
        }
    }
}