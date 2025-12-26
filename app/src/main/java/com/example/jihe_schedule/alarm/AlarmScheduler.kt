package com.example.jihe_schedule.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.model.Todo
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AlarmScheduler {

    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * 设置待办事项闹钟
     */
    fun scheduleAlarm(context: Context, todo: Todo, triggerTime: LocalDateTime) {
        val id = todo.id.hashCode()
        val title = "待办提醒：${todo.title}"
        val message = todo.description ?: "是时候完成这个任务了！"
        setExactAlarm(context, id, title, message, triggerTime, "待办")
    }

    /**
     * 设置课程闹钟
     */
    fun scheduleAlarmForCourse(context: Context, course: Course, triggerTime: LocalDateTime) {
        // 使用负数 ID 避免冲突
        val id = -course.id
        val title = "上课提醒：${course.name}"
        val message = "教室：${course.classroom}  |  即将开始"
        setExactAlarm(context, id, title, message, triggerTime, "课程")
    }

    /**
     * 通用：设置精确闹钟底层实现
     */
    private fun setExactAlarm(
        context: Context,
        id: Int,
        title: String,
        message: String,
        triggerTime: LocalDateTime,
        type: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMillis = System.currentTimeMillis()

        // 调试日志 tag
        val logTag = "AlarmScheduler"

        // 1. 判断时间是否已过
        if (triggerMillis <= nowMillis) {
            Log.w(logTag, "跳过过期闹钟: [$type] $title (时间: ${triggerTime.format(formatter)})")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }

            Log.d(logTag, "已设置闹钟: [$type] $title @ ${triggerTime.format(formatter)}")

        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 取消课程闹钟
     */
    fun cancelAlarmForCourse(context: Context, course: Course) {
        cancelAlarmById(context, -course.id)
    }

    fun cancelAlarm(context: Context, todo: Todo) {
        cancelAlarmById(context, todo.id.hashCode())
    }

    private fun cancelAlarmById(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}