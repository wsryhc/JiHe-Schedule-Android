package com.example.jihe_schedule.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jihe_schedule.viewmodel.TodoViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoManagerScreen(
    viewModel: TodoViewModel,
    onBack: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    val todos by viewModel.allTodos.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember {mutableStateOf(false)}

    // 导出文件 Launcher
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportTodosToJson()
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入文件 Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                viewModel.parseTodosFromJson(
                    json = json,
                    onSuccess = { list ->
                        viewModel.setPendingReviewTodos(list)
                        onNavigateToReview()
                    },
                    onError = { Toast.makeText(context, "导入失败：格式错误", Toast.LENGTH_SHORT).show() }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待办管理") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. 简略信息区域
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("当前已存储事项", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${todos.size} 个", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 2. 危险操作区
            Text("⚠️ 数据操作", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { showDeleteConfirmDialog =true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清空所有")
                }

                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出备份")
                }
            }

            HorizontalDivider()

            // 3. 导入数据区
            Text("📥 导入数据", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("导入前请确认 JSON 格式正确，导入后将进入校对页面。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val text = clipData.getItemAt(0).text.toString()
                            viewModel.parseTodosFromJson(
                                json = text,
                                onSuccess = { list ->
                                    viewModel.setPendingReviewTodos(list)
                                    onNavigateToReview()
                                },
                                onError = { Toast.makeText(context, "剪贴板内容格式错误", Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("读取剪贴板")
                }

                OutlinedButton(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("选择文件")
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("选择导出方式") },
            text = { Text("您可以将备份数据复制到剪贴板，或保存为 JSON 文件。") },
            confirmButton = {
                TextButton(onClick = {
                    val fileName = "jihe_todos_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.json"
                    exportFileLauncher.launch(fileName)
                    showExportDialog = false
                }) { Text("保存为文件") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val json = viewModel.exportTodosToJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Todo Backup", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }
                    showExportDialog = false
                }) { Text("复制内容") }
            }
        )
    }
    // ... showExportDialog 的代码 ...

// 【新增】删除确认弹窗
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false }, // 点击弹窗外部区域关闭
            title = { Text("⚠️ 确认操作") },
            text = { Text("此操作将清空所有已存储的待办事项，且无法恢复。\n\n您确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllTodos() // 【关键】在这里才真正执行删除
                        showDeleteConfirmDialog = false // 关闭弹窗
                        Toast.makeText(context, "已清空所有数据", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    // 一般确认删除按钮会用红色字，强调危险性
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}