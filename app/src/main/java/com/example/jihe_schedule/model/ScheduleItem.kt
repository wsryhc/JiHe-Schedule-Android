package com.example.jihe_schedule.model

data class ScheduleItem(
    val title: String,
    val subTitle: String, // 新增：副标题（显示"待办事项"或教室）
    val startTime: String, // 新增：开始时间 (如 "02:38")
    val endTime: String,   // 新增：结束时间 (如 "13:00")
    val isCompleted: Boolean = false,
    val type: ItemType,
    val themeColor: Long
)

enum class ItemType {
    COURSE, TODO
}