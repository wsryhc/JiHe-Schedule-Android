package com.example.jihe_schedule.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.jihe_schedule.model.ScheduleInfo
import com.example.jihe_schedule.viewmodel.ScheduleManagementViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleId: String?, // null 表示新建，有值表示编辑
    viewModel: ScheduleManagementViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 表单状态
    var name by remember { mutableStateOf("") }
    var startDateStr by remember { mutableStateOf("") }
    var totalWeeks by remember { mutableFloatStateOf(20f) }

    // 页面状态
    var isLoading by remember { mutableStateOf(true) }
    var scheduleToEdit by remember { mutableStateOf<ScheduleInfo?>(null) }

    // 弹窗状态
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareImageDialog by remember { mutableStateOf(false) } // 分享图片弹窗
    var showExportJsonDialog by remember { mutableStateOf(false) } // 导出JSON弹窗

    val datePickerState = rememberDatePickerState()

    // 🔥 文件保存 Launcher (用户选择位置保存 JSON)
    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && scheduleToEdit != null) {
            viewModel.saveJsonToUri(context, uri, scheduleToEdit!!)
        }
    }

    LaunchedEffect(scheduleId) {
        if (scheduleId != null) {
            val list = viewModel.schedules.value
            val found = list.find { it.id == scheduleId }
            if (found != null) {
                scheduleToEdit = found
                name = found.name
                startDateStr = found.termStartDate
                totalWeeks = found.totalWeeks.toFloat()
            }
        } else {
            val today = LocalDate.now()
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            startDateStr = monday.toString()
        }
        isLoading = false
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        startDateStr = monday.toString()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scheduleId == null) "新建课表" else "编辑课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (scheduleId != null) {
                        // 1. 分享按钮 -> 导出图片
                        IconButton(onClick = { showShareImageDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "分享/导出图片")
                        }
                        // 2. 导出按钮 -> 导出 JSON
                        IconButton(onClick = { showExportJsonDialog = true }) {
                            Icon(Icons.Default.Output, contentDescription = "备份/导出数据")
                        }
                        // 3. 删除按钮
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称 (必填)") },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = startDateStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("开学日期 (第一周周一)") },
                    trailingIcon = { Icon(Icons.Default.DateRange, null) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Box(modifier = Modifier.fillMaxWidth().height(56.dp).offset(y = (-56).dp).clickable { showDatePicker = true })

                Spacer(modifier = Modifier.height(24.dp))

                Text("学期总周数: ${totalWeeks.toInt()} 周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = { if(totalWeeks > 1) totalWeeks-- },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) { Icon(Icons.Default.Remove, null) }

                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = totalWeeks,
                        onValueChange = { totalWeeks = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = { if(totalWeeks < 30) totalWeeks++ },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) { Icon(Icons.Default.Add, null) }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val newSchedule = ScheduleInfo(
                                id = scheduleId ?: UUID.randomUUID().toString(),
                                name = name,
                                termStartDate = startDateStr,
                                totalWeeks = totalWeeks.toInt(),
                                isSelected = scheduleToEdit?.isSelected ?: false
                            )
                            viewModel.upsertSchedule(newSchedule)
                            onBack()
                        } else {
                            Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("保存") }
            }
        }
    }

    if (showDeleteConfirm && scheduleToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除课表“${scheduleToEdit!!.name}”吗？\n该操作将同时删除该课表下的所有课程数据，且不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(scheduleToEdit!!)
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    // 🔥🔥🔥 1. 分享/导出图片弹窗
    if (showShareImageDialog && scheduleToEdit != null) {
        var selectedWeek by remember { mutableIntStateOf(1) }
        var selectedTheme by remember { mutableStateOf("light") } // "light" or "dark"

        Dialog(onDismissRequest = { showShareImageDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("分享课表图片", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("选择周次: 第 $selectedWeek 周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    // 🔥 优化：带加减号的滑块
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        IconButton(
                            onClick = { if (selectedWeek > 1) selectedWeek-- },
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).size(32.dp)
                        ) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }

                        Slider(
                            value = selectedWeek.toFloat(),
                            onValueChange = { selectedWeek = it.toInt() },
                            valueRange = 1f..scheduleToEdit!!.totalWeeks.toFloat(),
                            steps = (scheduleToEdit!!.totalWeeks - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { if (selectedWeek < scheduleToEdit!!.totalWeeks) selectedWeek++ },
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).size(32.dp)
                        ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("选择主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        FilterChip(
                            selected = selectedTheme == "light",
                            onClick = { selectedTheme = "light" },
                            label = { Text("浅色模式") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedTheme == "dark",
                            onClick = { selectedTheme = "dark" },
                            label = { Text("深色模式") }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showShareImageDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.exportAsImage(context, scheduleToEdit!!, selectedWeek, selectedTheme)
                            showShareImageDialog = false
                        }) { Text("保存到相册") }
                    }
                }
            }
        }
    }

    // 🔥🔥🔥 2. 导出 JSON 弹窗
    if (showExportJsonDialog && scheduleToEdit != null) {
        Dialog(onDismissRequest = { showExportJsonDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Output, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("导出数据 (JSON)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("将课程数据导出为 .json 格式，可用于备份或发送给其他设备导入。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))

                    ImportOptionItem(Icons.Default.ContentCopy, "复制内容", "复制 JSON 文本到剪贴板") {
                        viewModel.copyJsonToClipboard(context, scheduleToEdit!!)
                        showExportJsonDialog = false
                    }

                    ImportOptionItem(Icons.Default.Save, "保存到文件", "选择位置保存 .json 文件") {
                        // 🔥 调用系统文件选择器
                        saveJsonLauncher.launch("${scheduleToEdit!!.name}_export.json")
                        showExportJsonDialog = false
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showExportJsonDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
                }
            }
        }
    }
}