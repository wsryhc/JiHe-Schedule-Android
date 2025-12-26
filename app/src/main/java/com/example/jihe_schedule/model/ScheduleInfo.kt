package com.example.jihe_schedule.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleInfo(
    @PrimaryKey val id: String,
    val name: String,
    val termStartDate: String,
    val totalWeeks: Int = 25,
    // 🔥 新增：是否为当前选中的课表 (默认为 false)
    val isSelected: Boolean = false
)