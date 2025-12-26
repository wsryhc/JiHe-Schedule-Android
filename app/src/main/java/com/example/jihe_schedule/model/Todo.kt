package com.example.jihe_schedule.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey
    val id: String,

    // 基础信息
    val title: String,
    val description: String? = null,
    val date: String,      // 首次开始日期 "YYYY-MM-DD"
    val startTime: String, // "HH:mm"
    val endTime: String,   // "HH:mm"
    val completed: Boolean = false,

    // 标签与外观
    val tag: String? = null,
    val tagType: String? = "default",
    val color: String? = null,

    // --- 重复规则核心 ---
    // 兼容旧逻辑，主要使用 repeatType
    val isYearly: Boolean = false,
    // 类型: 'none' | 'daily' | 'weekly' | 'monthly' | 'yearly'
    val repeatType: String = "none",

    // --- 新增：结束条件 ---
    // 类型: 'never' (永不) | 'date' (直到日期) | 'count' (按次数)
    val repeatEndType: String = "never",

    // 如果 repeatEndType == 'date'
    val repeatEndDate: String? = null, // "YYYY-MM-DD"

    // 如果 repeatEndType == 'count'
    val repeatCount: Int? = null,

    // --- 新增：例外日期 (仅删除本次) ---
    // 存储格式: "2025-01-01,2025-02-14" (用逗号分隔的日期字符串)
    val excludedDates: String = "",

    // 提醒设置
    val reminderValue: Int? = null,
    val reminderUnit: String? = null, // minute, hour, day, week, month, year
    val notificationId: String? = null
) : Parcelable