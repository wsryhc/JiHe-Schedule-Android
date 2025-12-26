package com.example.jihe_schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.jihe_schedule.model.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    // 1. 获取所有待办 (Flow)
    @Query("SELECT * FROM todos ORDER BY date ASC, startTime ASC")
    fun getAllTodos(): Flow<List<Todo>>

    // 2. 根据日期获取待办 (Flow)
    @Query("SELECT * FROM todos WHERE date = :targetDate")
    fun getTodosByDate(targetDate: String): Flow<List<Todo>>

    // 🔥🔥🔥 [新增] 直接获取待办 (非 Flow)，专用于小组件后台刷新 🔥🔥🔥
    @Query("SELECT * FROM todos WHERE date = :targetDate")
    suspend fun getTodosByDateDirect(targetDate: String): List<Todo>

    // 3. 插入或更新
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: Todo)

    // 3.1 批量插入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<Todo>)

    // 4. 更新待办
    @Update
    suspend fun updateTodo(todo: Todo)

    // 5. 删除待办
    @Delete
    suspend fun deleteTodo(todo: Todo)

    // 6. 清空所有
    @Query("DELETE FROM todos")
    suspend fun clearAll()

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}