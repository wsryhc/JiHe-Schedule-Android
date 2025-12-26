package com.example.jihe_schedule.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.jihe_schedule.JiHeApplication
import com.example.jihe_schedule.alarm.AlarmScheduler
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JiHeApplication
        val courseDao = app.database.courseDao()
        val scheduleDao = app.database.scheduleDao()
        val settingsRepository = app.settingsRepository

        Log.d("ReminderWorker", "Worker started")

        // 1. 检查开关
        val enableNotifications = settingsRepository.enableNotifications.first()
        val enableCourseNotify = settingsRepository.enableCourseNotify.first()

        if (!enableNotifications || !enableCourseNotify) {
            return Result.success()
        }

        // 2. 获取配置
        val notifyMinutes = settingsRepository.courseNotifyTime.first()
        val courseTimesList = settingsRepository.courseTimes.first()
        val maxPeriods = settingsRepository.maxPeriodCount.first()

        if (courseTimesList.isEmpty()) {
            return Result.success()
        }

        // 3. 获取学期和周次
        val allSchedules = scheduleDao.getAllSchedules().first()
        val currentSchedule = allSchedules.firstOrNull { it.isSelected } ?: allSchedules.firstOrNull()

        if (currentSchedule == null) return Result.success()

        val today = LocalDate.now()
        val start = try { LocalDate.parse(currentSchedule.termStartDate) } catch (e: Exception) { LocalDate.now() }
        val daysDiff = ChronoUnit.DAYS.between(start, today)

        if (daysDiff < 0) {
            return Result.success()
        }

        val currentWeek = (daysDiff / 7).toInt() + 1

        // Java Time API: Monday=1, Sunday=7. 数据库存的也是 1=周一。
        // 直接使用 value，不要减 1，否则会偏移到昨天
        val dayOfWeek = today.dayOfWeek.value

        // 4. 获取今天的课程
        val allCourses = courseDao.getCoursesByScheduleId(currentSchedule.id).first()
        val todayCourses = allCourses.filter {
            it.day == dayOfWeek && it.weeks.contains(currentWeek)
        }

        Log.d("ReminderWorker", "今天发现 ${todayCourses.size} 节课 (第 $currentWeek 周, 星期$dayOfWeek)")

        // 5. 设置闹钟
        todayCourses.forEach { course ->
            // 过滤超出节数的课
            if (course.startPeriod > maxPeriods) {
                return@forEach
            }

            val timeIndex = course.startPeriod - 1
            if (timeIndex >= 0 && timeIndex < courseTimesList.size) {
                val courseTimeObj = courseTimesList[timeIndex]
                val startTimeLocal = courseTimeObj.getStartTimeAsLocalTime()

                // 组合时间
                val classDateTime = LocalDateTime.of(today, startTimeLocal)

                // 计算触发时间
                val triggerDateTime = classDateTime.minusMinutes(notifyMinutes.toLong())

                // 尝试设置
                AlarmScheduler.scheduleAlarmForCourse(applicationContext, course, triggerDateTime)
            }
        }

        return Result.success()
    }

    companion object {
        fun startImmediately(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .addTag("sync_course_alarms")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "SyncCourseAlarmsWork",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}