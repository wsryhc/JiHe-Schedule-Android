package com.example.jihe_schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jihe_schedule.model.Todo

@Composable
fun TodoItem(
    todo: Todo,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable { onClick() }, // 点击整个卡片
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 左侧时间 (例如 10:00)
            Column(
                modifier = Modifier.width(50.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = todo.startTime,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // 2. 中间竖线
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(
                        // 如果有自定义颜色就用，没有就用默认紫
                        if (todo.color != null) Color(android.graphics.Color.parseColor(todo.color))
                        else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))

            // 3. 内容区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                    color = if (todo.completed) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                if (!todo.description.isNullOrEmpty()) {
                    Text(
                        text = todo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // 4. 右侧复选框
            Checkbox(
                checked = todo.completed,
                onCheckedChange = onCheckedChange
            )
        }
    }
}