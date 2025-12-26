package com.example.jihe_schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.jihe_schedule.model.ScheduleInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules")
    fun getAllSchedules(): Flow<List<ScheduleInfo>>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): ScheduleInfo?

    @Query("SELECT * FROM schedules WHERE isSelected = 1 LIMIT 1")
    fun getSelectedSchedule(): Flow<ScheduleInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleInfo)

    // 🔥 新增：补上这个缺失的方法，用于更新单个对象
    @Update
    suspend fun updateSchedule(schedule: ScheduleInfo)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleInfo)

    // 🔥 核心修复：使用事务，原子性地完成“清除旧选中”和“设置新选中”
    // 这个方法非常棒，我们会在 ViewModel 中直接调用它
    @Transaction
    suspend fun switchActiveSchedule(id: String) {
        clearAllSelection()
        setSelected(id)
    }

    @Query("UPDATE schedules SET isSelected = 0")
    suspend fun clearAllSelection()

    @Query("UPDATE schedules SET isSelected = 1 WHERE id = :id")
    suspend fun setSelected(id: String)
}