package com.example.jihe_schedule

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.model.Todo
import com.example.jihe_schedule.ui.AddCourseScreen
import com.example.jihe_schedule.ui.CourseTimeManagerScreen
import com.example.jihe_schedule.ui.MainScreen
import com.example.jihe_schedule.ui.NotificationSettingsScreen
import com.example.jihe_schedule.ui.ScheduleEditorScreen
import com.example.jihe_schedule.ui.ScheduleManagementScreen
import com.example.jihe_schedule.ui.ScheduleReviewScreen
import com.example.jihe_schedule.ui.SettingsScreen
import com.example.jihe_schedule.ui.TodoEditScreen
import com.example.jihe_schedule.ui.TodoManagerScreen
import com.example.jihe_schedule.ui.TodoReviewScreen
import com.example.jihe_schedule.ui.theme.JiHeScheduleTheme
import com.example.jihe_schedule.viewmodel.ScheduleManagementViewModel
import com.example.jihe_schedule.viewmodel.ScheduleViewModel
import com.example.jihe_schedule.viewmodel.SettingsViewModel
import com.example.jihe_schedule.viewmodel.TodoViewModel
import com.example.jihe_schedule.worker.ReminderWorker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        startReminderWorker()

        setContent {
            // 这些 ViewModel 在 Activity 范围内是单例的
            val settingsViewModel: SettingsViewModel = viewModel()
            val scheduleMgmtViewModel: ScheduleManagementViewModel = viewModel()
            val scheduleViewModel: ScheduleViewModel = viewModel()
            val todoViewModel: TodoViewModel = viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val themeColorHex by settingsViewModel.themeColor.collectAsState()

            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            val primaryColor = try {
                Color(android.graphics.Color.parseColor(themeColorHex))
            } catch (e: Exception) {
                Color(0xFF6650a4)
            }

            val myColorScheme = if (isDarkTheme) {
                darkColorScheme(primary = primaryColor)
            } else {
                lightColorScheme(primary = primaryColor)
            }

            JiHeScheduleTheme(darkTheme = isDarkTheme, colorSchemeOverride = myColorScheme) {
                val navController = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "main") {

                        // 1. 主界面
                        composable("main") {
                            val returnDateStr = navController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.get<String>("return_date")

                            val returnDate = if (returnDateStr != null) {
                                try { LocalDate.parse(returnDateStr) } catch (e: Exception) { null }
                            } else null

                            MainScreen(
                                settingsViewModel = settingsViewModel,
                                scheduleViewModel = scheduleViewModel,
                                initialTodoDate = returnDate,
                                onNavigateToCourseEdit = { day, period, course ->
                                    navController.currentBackStackEntry?.savedStateHandle?.set("edit_day", day)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("edit_period", period)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("edit_course", course)
                                    navController.navigate("course_editor")
                                },
                                onNavigateToTodoEdit = { todo, date ->
                                    navController.currentBackStackEntry?.savedStateHandle?.set("edit_todo", todo)
                                    val dateStr = date?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("init_date", dateStr)
                                    navController.navigate("todo_editor")
                                },
                                onNavigateToScheduleManager = { navController.navigate("schedule_manager") },
                                onNavigateToCourseTimeManager = { navController.navigate("course_time_manager") },
                                onNavigateToTodoManager = { navController.navigate("todo_manager") },
                                onNavigateToNotificationManager = { navController.navigate("notification_manager") }
                            )
                        }

                        // 2. 课程编辑
                        composable("course_editor") {
                            val args = remember {
                                val state = navController.previousBackStackEntry?.savedStateHandle
                                val day = state?.get<Int>("edit_day") ?: 1
                                val period = state?.get<Int>("edit_period") ?: 1
                                val course = state?.get<Course>("edit_course")
                                Triple(day, period, course)
                            }
                            AddCourseScreen(
                                courseToEdit = args.third,
                                initialDay = args.first,
                                initialPeriod = args.second,
                                onBack = { navController.popBackStack() },
                                viewModel = scheduleViewModel
                            )
                        }

                        // 3. 待办编辑 (🔥 修改：传入 settingsViewModel)
                        composable("todo_editor") {
                            val todo = navController.previousBackStackEntry?.savedStateHandle?.get<Todo>("edit_todo")
                            val initDate = navController.previousBackStackEntry?.savedStateHandle?.get<String>("init_date")

                            TodoEditScreen(
                                todoToEdit = todo,
                                initialDate = initDate,
                                onBack = { dateStr ->
                                    if (dateStr != null) {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set("return_date", dateStr)
                                    }
                                    navController.popBackStack()
                                },
                                viewModel = todoViewModel, // 可选
                                settingsViewModel = settingsViewModel // 🔥 新增：用于检查全局通知开关
                            )
                        }

                        // 4. 设置页面 (🔥 修改：传入 onNavigateToNotificationManager)
                        composable("settings") {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateToScheduleManager = { navController.navigate("schedule_manager") },
                                onNavigateToCourseTimeManager = { navController.navigate("course_time_manager") },
                                onNavigateToTodoManager = { navController.navigate("todo_manager") },
                                onNavigateToNotificationManager = { navController.navigate("notification_manager") }
                            )
                        }

                        // 🔥 11. 新增：消息提醒设置页
                        composable("notification_manager") {
                            NotificationSettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 5. 课表列表管理页
                        composable("schedule_manager") {
                            ScheduleManagementScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEditor = { scheduleId ->
                                    val route = if (scheduleId != null) "schedule_editor?id=$scheduleId" else "schedule_editor"
                                    navController.navigate(route)
                                },
                                onNavigateToReview = { navController.navigate("schedule_review") },
                                viewModel = scheduleMgmtViewModel
                            )
                        }

                        // 6. 课表编辑页
                        composable(
                            route = "schedule_editor?id={id}",
                            arguments = listOf(navArgument("id") { nullable = true })
                        ) { backStackEntry ->
                            val scheduleId = backStackEntry.arguments?.getString("id")
                            ScheduleEditorScreen(
                                scheduleId = scheduleId,
                                viewModel = scheduleMgmtViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 7. OCR/JSON 结果校对页
                        composable("schedule_review") {
                            ScheduleReviewScreen(
                                viewModel = scheduleMgmtViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 8. 课程时间管理
                        composable("course_time_manager") {
                            CourseTimeManagerScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 9. 待办管理页面
                        composable("todo_manager") {
                            TodoManagerScreen(
                                viewModel = todoViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToReview = { navController.navigate("todo_review") }
                            )
                        }

                        // 10. 待办导入校对页面
                        composable("todo_review") {
                            TodoReviewScreen(
                                viewModel = todoViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startReminderWorker() {
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CourseReminderWork",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )
    }
}