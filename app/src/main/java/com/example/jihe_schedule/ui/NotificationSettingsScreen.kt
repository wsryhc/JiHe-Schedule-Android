package com.example.jihe_schedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jihe_schedule.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val enableNotifications by viewModel.enableNotifications.collectAsState()
    val enableCourseNotify by viewModel.enableCourseNotify.collectAsState()
    val courseNotifyTime by viewModel.courseNotifyTime.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息提醒设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ListItem(
                headlineContent = { Text("开启消息提醒", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("关闭后，课程和待办事项都将不再推送通知") },
                trailingContent = {
                    Switch(
                        checked = enableNotifications,
                        onCheckedChange = { viewModel.updateNotificationSettings(it, enableCourseNotify, courseNotifyTime) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AnimatedVisibility(
                visible = enableNotifications,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Text("课程提醒", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("开启课程提醒")
                                Switch(checked = enableCourseNotify, onCheckedChange = { viewModel.updateNotificationSettings(enableNotifications, it, courseNotifyTime) })
                            }
                            AnimatedVisibility(visible = enableCourseNotify) {
                                Column {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("上课前多久提醒：${courseNotifyTime}分钟")
                                    Slider(
                                        value = courseNotifyTime.toFloat(),
                                        onValueChange = { viewModel.updateNotificationSettings(enableNotifications, enableCourseNotify, it.toInt()) },
                                        // 🔥 修改：范围改为 0f..180f
                                        valueRange = 0f..180f,
                                        // 🔥 修改：步进设为 35 (即 180/5 - 1)，这样每格是 5 分钟，方便选择
                                        steps = 35
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        // 🔥 修改：更新标签文字
                                        Text("0分钟", style = MaterialTheme.typography.bodySmall)
                                        Text("180分钟", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("待办事项提醒", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    Text("请在“待办事项”的编辑页面单独设置每个任务的提醒时间。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}