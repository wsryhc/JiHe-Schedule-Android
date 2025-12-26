package com.example.jihe_schedule.data

import androidx.room.TypeConverter

class Converters {
    // 简单的逗号分隔法，或者用 Gson/Json 都可以。这里演示用 Gson 更加通用。
    // 如果不想引入 Gson 库，也可以手动写 String.join

    @TypeConverter
    fun fromString(value: String): List<Int> {
        if (value.isEmpty()) return emptyList()
        // 简单手动解析："1,2,3" -> List<Int>
        return value.split(",").mapNotNull { it.toIntOrNull() }
    }

    @TypeConverter
    fun fromList(list: List<Int>): String {
        // List<Int> -> "1,2,3"
        return list.joinToString(",")
    }
}