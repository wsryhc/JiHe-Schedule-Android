package com.example.jihe_schedule.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val teacher: String,
    val classroom: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val color: String,

    // 🔥 核心修改：类型改为 String，默认值为 "1"
    // 这样它就能匹配 ScheduleInfo 的 String 类型 ID 了
    val scheduleId: String = "1"
) : Parcelable