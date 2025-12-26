package com.example.jihe_schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jihe_schedule.JiHeApplication
import com.example.jihe_schedule.alarm.AlarmScheduler
// 🔥 引用 util 包下的 Helper
import com.example.jihe_schedule.util.WidgetHelper
import com.example.jihe_schedule.model.Todo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

// 用于兼容 AI 导入的数据结构
data class TodoImportData(
    val todos: List<Todo>?
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {

    private val todoDao = (application as JiHeApplication).database.todoDao()
    private val gson = Gson()

    val allTodos: StateFlow<List<Todo>> = todoDao.getAllTodos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _pendingReviewTodos = MutableStateFlow<List<Todo>?>(null)
    val pendingReviewTodos = _pendingReviewTodos.asStateFlow()

    fun setPendingReviewTodos(todos: List<Todo>) {
        _pendingReviewTodos.value = todos
    }

    fun clearPendingReviewTodos() {
        _pendingReviewTodos.value = null
    }

    // 批量保存
    fun saveReviewedTodos(todos: List<Todo>) {
        viewModelScope.launch {
            todoDao.insertAll(todos)
            todos.forEach { scheduleNotificationForTodo(it) }
            clearPendingReviewTodos()

            // 刷新小组件
            WidgetHelper.refreshNow(getApplication())
        }
    }

    fun parseTodosFromJson(json: String, onSuccess: (List<Todo>) -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var finalTodos: List<Todo> = emptyList()

                try {
                    val listType = object : TypeToken<List<Todo>>() {}.type
                    finalTodos = gson.fromJson(json, listType)
                } catch (e: Exception) {}

                if (finalTodos.isNullOrEmpty()) {
                    try {
                        val wrapper = gson.fromJson(json, TodoImportData::class.java)
                        if (!wrapper.todos.isNullOrEmpty()) {
                            finalTodos = wrapper.todos
                        }
                    } catch (e: Exception) {}
                }

                if (!finalTodos.isNullOrEmpty()) {
                    val sanitizedTodos = finalTodos.map {
                        it.copy(
                            id = UUID.randomUUID().toString(),
                            completed = it.completed
                        )
                    }
                    withContext(Dispatchers.Main) { onSuccess(sanitizedTodos) }
                } else {
                    withContext(Dispatchers.Main) { onError() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onError() }
            }
        }
    }

    suspend fun exportTodosToJson(): String {
        return withContext(Dispatchers.IO) {
            val todos = allTodos.value.ifEmpty {
                todoDao.getAllTodos().first()
            }
            gson.toJson(todos)
        }
    }

    // --- 基础 CRUD 操作 ---

    fun addTodo(todo: Todo) {
        viewModelScope.launch {
            todoDao.insertTodo(todo)
            scheduleNotificationForTodo(todo)
            WidgetHelper.refreshNow(getApplication())
        }
    }

    fun updateTodo(todo: Todo) {
        viewModelScope.launch {
            todoDao.updateTodo(todo)
            AlarmScheduler.cancelAlarm(getApplication(), todo)
            scheduleNotificationForTodo(todo)
            WidgetHelper.refreshNow(getApplication())
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            todoDao.deleteTodo(todo)
            AlarmScheduler.cancelAlarm(getApplication(), todo)
            WidgetHelper.refreshNow(getApplication())
        }
    }

    fun deleteAllTodos() {
        viewModelScope.launch {
            val currentList = allTodos.value
            currentList.forEach { AlarmScheduler.cancelAlarm(getApplication(), it) }
            todoDao.deleteAll()
            WidgetHelper.refreshNow(getApplication())
        }
    }

    fun toggleComplete(todo: Todo) {
        val newTodo = todo.copy(completed = !todo.completed)
        updateTodo(newTodo)
    }

    private fun scheduleNotificationForTodo(todo: Todo) {
        if (todo.completed || todo.reminderValue == null) return
        try {
            val date = LocalDate.parse(todo.date)
            val time = LocalTime.parse(todo.startTime)
            val taskDateTime = LocalDateTime.of(date, time)
            val reminderMinutes = calculateMinutes(todo.reminderValue, todo.reminderUnit)
            val triggerTime = taskDateTime.minusMinutes(reminderMinutes)
            AlarmScheduler.scheduleAlarm(getApplication(), todo, triggerTime)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateMinutes(value: Int, unit: String?): Long {
        return when (unit) {
            "minute" -> value.toLong()
            "hour" -> value.toLong() * 60
            "day" -> value.toLong() * 24 * 60
            "week" -> value.toLong() * 7 * 24 * 60
            "month" -> value.toLong() * 30 * 24 * 60
            "year" -> value.toLong() * 365 * 24 * 60
            else -> 0
        }
    }
}