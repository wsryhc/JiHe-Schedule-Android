package com.example.jihe_schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.jihe_schedule.model.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    // 获取指定课表的所有课程 (Flow, 用于 UI 观察)
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    fun getCoursesByScheduleId(scheduleId: String): Flow<List<Course>>

    // 🔥 新增: 获取指定课表的所有课程 (同步/挂起, 用于导出 JSON)
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    suspend fun getCoursesDirect(scheduleId: String): List<Course>

    // 插入或更新
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course)

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    // 🔥 新增: 删除指定课表下的所有课程 (用于删除课表时的级联删除)
    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteCoursesByScheduleId(scheduleId: String)
}