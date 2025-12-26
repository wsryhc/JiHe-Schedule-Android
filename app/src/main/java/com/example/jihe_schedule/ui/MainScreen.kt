package com.example.jihe_schedule.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.model.Todo
import com.example.jihe_schedule.viewmodel.ScheduleViewModel
import com.example.jihe_schedule.viewmodel.SettingsViewModel
import java.time.LocalDate

@Composable
fun MainScreen(
    initialTodoDate: LocalDate? = null,
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
    onNavigateToCourseEdit: (Int, Int, Course?) -> Unit,
    onNavigateToTodoEdit: (Todo?, LocalDate?) -> Unit,
    onNavigateToScheduleManager: () -> Unit,
    onNavigateToCourseTimeManager: () -> Unit,
    onNavigateToTodoManager: () -> Unit,
    onNavigateToNotificationManager: () -> Unit
) {
    // 使用 rememberSaveable 保存选中状态，避免页面切换/返回时重置
    var selectedIndex by rememberSaveable { mutableIntStateOf(1) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    icon = { Text("✅") },
                    label = { Text("待办") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    icon = { Text("📅") },
                    label = { Text("课表") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 2,
                    onClick = { selectedIndex = 2 },
                    icon = { Text("⚙️") },
                    label = { Text("设置") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedIndex) {
                0 -> TodoScreen(
                    initialDate = initialTodoDate,
                    settingsViewModel = settingsViewModel,
                    scheduleViewModel = scheduleViewModel,
                    onNavigateToEdit = onNavigateToTodoEdit
                )

                1 -> ScheduleScreen(
                    viewModel = scheduleViewModel,
                    settingsViewModel = settingsViewModel,
                    onManageCourse = onNavigateToCourseEdit,
                    onNavigateToScheduleManager = onNavigateToScheduleManager // 🔥 传递参数
                )

                2 -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToScheduleManager = onNavigateToScheduleManager,
                    onNavigateToCourseTimeManager = onNavigateToCourseTimeManager,
                    onNavigateToTodoManager = onNavigateToTodoManager,
                    onNavigateToNotificationManager = onNavigateToNotificationManager
                )
            }
        }
    }
}