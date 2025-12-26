package com.example.jihe_schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.jihe_schedule.model.Course

@Composable
fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit, // 关闭弹窗的回调
    onDelete: () -> Unit   // 删除回调
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 课程名称 (大标题)
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 2. 详细信息区域
                InfoRow(label = "📍 教室", value = course.classroom.ifEmpty { "未设置" })
                InfoRow(label = "👨‍🏫 老师", value = course.teacher.ifEmpty { "未设置" })
                InfoRow(label = "🕒 时间", value = "周${getDayName(course.day)} ${course.startPeriod}-${course.endPeriod}节")
                InfoRow(label = "📅 周数", value = getWeeksDesc(course.weeks))

                Spacer(modifier = Modifier.height(24.dp))

                // 3. 底部按钮区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 删除按钮
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除课程")
                    }

                    // 确定/关闭按钮
                    Button(onClick = onDismiss) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

// 辅助组件：信息行
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.width(80.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// 辅助函数：获取中文周几
fun getDayName(dayIndex: Int): String {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    return days.getOrElse(dayIndex) { "" }
}

// 辅助函数：把 [1,2,3,5,6] 变成 "1-3, 5-6周" 这种易读格式 (这里先简单处理，直接显示范围)
fun getWeeksDesc(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "无"
    return "${weeks.minOrNull()}-${weeks.maxOrNull()}周"
}