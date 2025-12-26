package com.example.jihe_schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.jihe_schedule.model.Todo
import java.util.UUID

@Composable
fun TodoDetailDialog(
    todo: Todo?, // 传入 null 表示新建，传入对象表示编辑
    onDismiss: () -> Unit,
    onSave: (Todo) -> Unit,
    onDelete: (Todo) -> Unit
) {
    // 如果是编辑模式，初始值就是原来的；如果是新建，就是空
    var title by remember { mutableStateOf(todo?.title ?: "") }
    var description by remember { mutableStateOf(todo?.description ?: "") }

    // 时间暂时先用文本框代替，后面我们再换成漂亮的日历选择器
    var timeStr by remember { mutableStateOf(todo?.startTime ?: "10:00") }

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
                // 标题
                Text(
                    text = if (todo == null) "新建待办" else "编辑待办",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 输入框区域
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("要做什么？") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注 (可选)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = timeStr,
                    onValueChange = { timeStr = it },
                    label = { Text("时间 (例如 14:00)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：如果是编辑模式，显示删除按钮；否则显示占位
                    if (todo != null) {
                        TextButton(
                            onClick = { onDelete(todo) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("删除")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 右侧：取消和保存
                    Row {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (title.isBlank()) return@Button

                            // 构建新对象
                            val newTodo = todo?.copy(
                                title = title,
                                description = description,
                                startTime = timeStr
                            ) ?: Todo(
                                id = UUID.randomUUID().toString(),
                                title = title,
                                description = description,
                                date = "2025-12-21", // 暂时写死，下一步做日历
                                startTime = timeStr,
                                endTime = "12:00",
                                completed = false
                            )

                            onSave(newTodo)
                        }) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}