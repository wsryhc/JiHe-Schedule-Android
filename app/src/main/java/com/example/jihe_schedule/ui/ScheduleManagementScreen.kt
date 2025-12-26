package com.example.jihe_schedule.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.jihe_schedule.model.ScheduleInfo
import com.example.jihe_schedule.viewmodel.ScheduleManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToReview: () -> Unit,
    viewModel: ScheduleManagementViewModel
) {
    val schedules by viewModel.schedules.collectAsState()
    val pendingData by viewModel.pendingReviewData.collectAsState()

    // 获取当前选中的课表
    val activeSchedule = schedules.find { it.isSelected }

    // 监听：一旦有待校对数据，自动跳转到 Review 页面
    LaunchedEffect(pendingData) {
        if (pendingData != null) {
            onNavigateToReview()
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showAiInstructionDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // 🔥 删除相关的 State
    var showDeleteOptionDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteCurrentDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteAllDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val jsonFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.importFromFile(context, uri, onSuccess = {}, onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
    }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            Toast.makeText(context, "正在离线识别课表...", Toast.LENGTH_SHORT).show()

            viewModel.parseOcrImage(context, uri, "") { errorMsg ->
                isLoading = false
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 加载状态重置
    LaunchedEffect(pendingData) {
        if (pendingData != null) isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课程表管理") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    // 🔥 新增：删除按钮
                    IconButton(onClick = { showDeleteOptionDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建课表") }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (schedules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无课表", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(schedules) { schedule ->
                        ScheduleItemCard(
                            schedule = schedule,
                            onSelect = { viewModel.setActiveSchedule(schedule) },
                            onEdit = { onNavigateToEditor(schedule.id) }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // 🔥 弹窗 1: 删除选项 (选择删除当前 or 删除所有)
    if (showDeleteOptionDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteOptionDialog = false },
            title = { Text("删除课表") },
            text = {
                Column {
                    Text("请选择要执行的操作：", modifier = Modifier.padding(bottom = 16.dp))

                    // 选项 1: 删除当前使用课表
                    OutlinedButton(
                        onClick = {
                            showDeleteOptionDialog = false
                            showConfirmDeleteCurrentDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activeSchedule != null
                    ) {
                        Text("删除当前使用课表")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 选项 2: 删除所有课表
                    Button(
                        onClick = {
                            showDeleteOptionDialog = false
                            showConfirmDeleteAllDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = schedules.isNotEmpty()
                    ) {
                        Text("删除所有课表")
                    }
                }
            },
            confirmButton = {}, // 不需要底部确认按钮，操作在上面选择
            dismissButton = {
                TextButton(onClick = { showDeleteOptionDialog = false }) { Text("取消") }
            }
        )
    }

    // 🔥 弹窗 2: 二次确认 - 删除当前课表
    if (showConfirmDeleteCurrentDialog && activeSchedule != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteCurrentDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除？") },
            text = { Text("确定要删除当前使用的课表 \"${activeSchedule.name}\" 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(activeSchedule)
                        showConfirmDeleteCurrentDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteCurrentDialog = false }) { Text("取消") }
            }
        )
    }

    // 🔥 弹窗 3: 二次确认 - 删除所有课表
    if (showConfirmDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAllDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("危险操作") },
            text = { Text("确定要删除 所有课表 吗？\n所有课程数据都将丢失，此操作无法撤销！") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllSchedules()
                        showConfirmDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteAllDialog = false }) { Text("取消") }
            }
        )
    }

    // 1. 新建方式选择弹窗
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("添加课表", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    ImportOptionItem(Icons.Default.Create, "新建空白课表", "手动填写所有信息") {
                        showCreateDialog = false
                        onNavigateToEditor(null)
                    }

                    ImportOptionItem(Icons.Default.Code, "导入 JSON 数据", "从文件或剪贴板导入") {
                        showCreateDialog = false
                        showImportJsonDialog = true
                    }

                    ImportOptionItem(Icons.Default.Image, "离线图片识别导入", "尽量只截取表格内容，准确率不高") {
                        showCreateDialog = false
                        imagePickerLauncher.launch("image/*")
                    }

                    ImportOptionItem(Icons.Default.SmartToy, "使用 AI 工具导入", "使用其他 AI 工具识别") {
                        showCreateDialog = false
                        showAiInstructionDialog = true
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showCreateDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
                }
            }
        }
    }

    // 2. JSON 导入方式选择
    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("导入 JSON") },
            text = { Text("请选择导入方式") },
            confirmButton = {
                TextButton(onClick = {
                    showImportJsonDialog = false
                    val clipData = clipboardManager.getText()
                    if (clipData != null) viewModel.importFromJson(clipData.toString(), onSuccess = {}, onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
                    else Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }) { Text("粘贴自剪贴板") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportJsonDialog = false
                    jsonFileLauncher.launch("application/json")
                }) { Text("选择文件") }
            }
        )
    }

    // 3. AI 辅助导入教程弹窗
    if (showAiInstructionDialog) {
        val promptText = """
            请帮我识别这张课程表图片。
            请提取课程信息，并输出为【标准的 JSON 格式】。
            
            ⚠️ 格式严格要求：
            1. 最外层必须是一个对象，包含 "courses" 数组。
            2. 字段名称必须完全一致 (startPeriod, endPeriod)。
            3. 随机生成 hex 颜色代码 (如 "#B388FF") 填入 color 字段。
            4. 不要输出 Markdown 标记 (如 ```json)，只输出纯文本 JSON。
            5. 给我的消息中不要包含json以外的文字。
            6. 同一课程名称有可能在每周的不同天上课，属于正常现象。
            7. 不同的课程可能在不同周的同一天的同一节课上课。
            JSON 结构示例：
            {
              "courses": [
                {
                  "name": "课程名称",
                  "classroom": "教室",
                  "teacher": "教师",
                  "color": "#B388FF",
                  "day": 1,
                  "startPeriod": 1,
                  "endPeriod": 2,
                  "weeks": [1, 2, 3, 4, 5, 6, 7, 8]
                }
              ]
            }
            
            字段说明：
            - day: 星期几 (数字 1-7)
            - startPeriod: 开始节次 (数字)
            - endPeriod: 结束节次 (数字)
            - weeks: 上课周数 (数字数组)
        """.trimIndent()

        AlertDialog(
            onDismissRequest = { showAiInstructionDialog = false },
            title = { Text("AI 辅助导入教程") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("推荐使用 ChatGPT / Claude / Kimi 等 AI 工具辅助导入。", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("步骤：", fontWeight = FontWeight.Bold)
                    Text("1. 复制下方提示词。", style = MaterialTheme.typography.bodySmall)
                    Text("2. 发送图片和提示词给 AI。", style = MaterialTheme.typography.bodySmall)
                    Text("3. 复制 AI 返回的 JSON 代码。", style = MaterialTheme.typography.bodySmall)
                    Text("4. 回到本应用选择「导入 JSON」-「粘贴自剪贴板」。", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("提示词") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(promptText))
                    Toast.makeText(context, "提示词已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }) { Text("复制提示词") }
            },
            dismissButton = {
                TextButton(onClick = { showAiInstructionDialog = false }) { Text("关闭") }
            }
        )
    }
}

// 辅助组件：ImportOptionItem
@Composable
fun ImportOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// 辅助组件：ScheduleItemCard
@Composable
fun ScheduleItemCard(
    schedule: ScheduleInfo,
    onSelect: () -> Unit, // 点击卡片主体（选中）
    onEdit: () -> Unit    // 点击编辑按钮（编辑）
) {
    val borderColor = if (schedule.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (schedule.isSelected) 2.dp else 0.dp
    Card(
        onClick = onSelect, // 这里改为 onSelect
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(borderWidth, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = schedule.isSelected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(schedule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (schedule.isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("使用中", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("开学: ${schedule.termStartDate} | 共 ${schedule.totalWeeks} 周", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 独立的编辑按钮
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}