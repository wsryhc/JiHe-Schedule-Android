package com.example.jihe_schedule.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class CourseTime(
    val start: String,
    val end: String
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("HH:mm")

        fun from(startTime: LocalTime, endTime: LocalTime): CourseTime {
            return CourseTime(startTime.format(formatter), endTime.format(formatter))
        }

        // 🔥 新增：把 List 转成字符串 "08:00-08:45|08:55-09:40"
        fun listToString(list: List<CourseTime>): String {
            return list.joinToString("|") { "${it.start}-${it.end}" }
        }

        // 🔥 新增：把字符串转回 List
        fun stringToList(str: String): List<CourseTime> {
            if (str.isBlank()) return emptyList()
            return try {
                str.split("|").map {
                    val parts = it.split("-")
                    CourseTime(parts[0], parts[1])
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun getStartTimeAsLocalTime(): LocalTime = LocalTime.parse(start, formatter)
    fun getEndTimeAsLocalTime(): LocalTime = LocalTime.parse(end, formatter)
}