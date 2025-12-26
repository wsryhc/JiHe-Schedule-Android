package com.example.jihe_schedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jihe_schedule.model.Todo
import com.example.jihe_schedule.viewmodel.SettingsViewModel
import com.example.jihe_schedule.viewmodel.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

// 配置常量
data class TagOption(val label: String, val value: String, val icon: ImageVector, val isSpecial: Boolean)

val PRESET_TAGS = listOf(
    TagOption("默认", "default", Icons.Default.Info, false),
    TagOption("生日", "birthday", Icons.Default.Star, true),
    TagOption("纪念日", "anniversary", Icons.Default.Favorite, true),
    TagOption("考试", "exam", Icons.Default.Edit, false),
    TagOption("会议", "meeting", Icons.Default.DateRange, false),
    TagOption("自定义", "custom", Icons.Default.Create, false)
)

val REPEAT_OPTIONS = listOf(
    "none" to "不重复", "daily" to "每天重复", "weekly" to "每周重复",
    "monthly" to "每月重复", "yearly" to "每年重复"
)

val REPEAT_END_OPTIONS = listOf("never" to "永不结束", "date" to "直到日期", "count" to "重复次数")

val EXTENDED_COLOR_OPTIONS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
    "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
    "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
    "#FF5722", "#F06292", "#BA68C8", "#4DD0E1", "#AED581"
)

val REMINDER_UNITS_ROW1 = listOf("minute" to "分钟", "hour" to "小时", "day" to "天")
val REMINDER_UNITS_ROW2 = listOf("week" to "周", "month" to "月", "year" to "年")
val REMINDER_LIMITS = mapOf("minute" to 60f, "hour" to 24f, "day" to 30f, "week" to 4f, "month" to 12f, "year" to 3f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoEditScreen(
    todoToEdit: Todo?,
    initialDate: String?,
    onBack: (String?) -> Unit,
    viewModel: TodoViewModel = viewModel(),
    settingsViewModel: SettingsViewModel // 🔥 必须传入此VM以检查消息总开关
) {
    val context = LocalContext.current
    val isEditMode = remember { todoToEdit != null }
    var isNavigating by remember { mutableStateOf(false) }

    // 🔥 获取全局通知开关状态
    val enableNotifications by settingsViewModel.enableNotifications.collectAsState()

    // 状态初始化
    var title by remember { mutableStateOf(todoToEdit?.title ?: "") }
    var description by remember { mutableStateOf(todoToEdit?.description ?: "") }

    var date by remember {
        mutableStateOf(
            if (!todoToEdit?.date.isNullOrEmpty()) LocalDate.parse(todoToEdit!!.date)
            else if (initialDate != null) LocalDate.parse(initialDate)
            else LocalDate.now()
        )
    }

    val deleteTargetDate = remember {
        if (initialDate != null) LocalDate.parse(initialDate)
        else if (!todoToEdit?.date.isNullOrEmpty()) LocalDate.parse(todoToEdit!!.date)
        else LocalDate.now()
    }

    var startTime by remember { mutableStateOf(if (todoToEdit != null) LocalTime.parse(todoToEdit.startTime) else LocalTime.of(12, 0)) }
    var endTime by remember { mutableStateOf(if (todoToEdit != null) LocalTime.parse(todoToEdit.endTime) else LocalTime.of(13, 0)) }

    var selectedTagValue by remember { mutableStateOf(todoToEdit?.tagType ?: "default") }
    var customTagName by remember { mutableStateOf(if (todoToEdit?.tagType == "custom") todoToEdit.tag ?: "" else "") }
    var selectedColor by remember { mutableStateOf(todoToEdit?.color ?: EXTENDED_COLOR_OPTIONS[0]) }

    var repeatType by remember {
        mutableStateOf(todoToEdit?.repeatType?.takeIf { it != "none" } ?: if (todoToEdit?.isYearly == true) "yearly" else "none")
    }
    var repeatEndType by remember { mutableStateOf(todoToEdit?.repeatEndType ?: "never") }
    var repeatEndDate by remember {
        mutableStateOf(
            if (todoToEdit?.repeatEndDate != null) LocalDate.parse(todoToEdit.repeatEndDate)
            else date.plusMonths(1)
        )
    }
    var repeatCount by remember { mutableStateOf(todoToEdit?.repeatCount?.toString() ?: "10") }

    var hasReminder by remember { mutableStateOf(todoToEdit?.reminderValue != null) }
    var reminderValue by remember { mutableFloatStateOf(todoToEdit?.reminderValue?.toFloat() ?: 15f) }
    var reminderUnit by remember { mutableStateOf(todoToEdit?.reminderUnit ?: "minute") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDateDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 🔥 统一弹窗状态管理
    var alertState by remember { mutableStateOf<AlertInfo?>(null) }

    // 保存逻辑函数
    fun performSave() {
        if (isNavigating) return
        isNavigating = true
        val tagName = if (selectedTagValue == "custom") customTagName.ifBlank { "自定义" } else PRESET_TAGS.find { it.value == selectedTagValue }?.label ?: "默认"

        val newTodo = Todo(
            id = todoToEdit?.id ?: UUID.randomUUID().toString(),
            title = title,
            description = description.ifBlank { null },
            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            startTime = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            endTime = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            completed = todoToEdit?.completed ?: false,
            tag = tagName,
            tagType = selectedTagValue,
            color = selectedColor,
            repeatType = repeatType,
            isYearly = repeatType == "yearly",
            repeatEndType = if (repeatType == "none") "never" else repeatEndType,
            repeatEndDate = if (repeatType != "none" && repeatEndType == "date") repeatEndDate.format(DateTimeFormatter.ISO_LOCAL_DATE) else null,
            repeatCount = if (repeatType != "none" && repeatEndType == "count") repeatCount.toIntOrNull() else null,
            excludedDates = todoToEdit?.excludedDates ?: "",
            reminderValue = if (hasReminder) reminderValue.toInt() else null,
            reminderUnit = if (hasReminder) reminderUnit else null
        )

        if (isEditMode) viewModel.updateTodo(newTodo) else viewModel.addTodo(newTodo)
        onBack(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "编辑事项" else "新建事项", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (!isNavigating) onBack(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 1. 基础校验
                        if (title.isBlank()) {
                            alertState = AlertInfo("提示", "标题不能为空")
                            return@TextButton
                        }
                        if (!endTime.isAfter(startTime)) {
                            alertState = AlertInfo("时间错误", "结束时间必须晚于开始时间")
                            return@TextButton
                        }

                        // 2. 提醒时间校验与全局开关检查
                        if (hasReminder) {
                            // 🔥 核心检查：如果开了提醒但全局开关没开
                            if (!enableNotifications) {
                                alertState = AlertInfo(
                                    title = "消息提醒未开启",
                                    message = "你为此事项设置了提醒，但系统设置中已关闭了“消息提醒”总开关，该提醒将不会生效。\n\n是否仍要保存？",
                                    showCancel = true,
                                    confirmLabel = "仍要保存",
                                    onConfirm = {
                                        alertState = null
                                        performSave()
                                    }
                                )
                                return@TextButton
                            }

                            if (repeatType == "none") {
                                val todoDateTime = LocalDateTime.of(date, startTime)
                                val rValue = reminderValue.toInt()
                                val offsetDuration = when (reminderUnit) {
                                    "minute" -> java.time.Duration.ofMinutes(rValue.toLong())
                                    "hour" -> java.time.Duration.ofHours(rValue.toLong())
                                    "day" -> java.time.Duration.ofDays(rValue.toLong())
                                    "week" -> java.time.Duration.ofDays(rValue.toLong() * 7)
                                    "month" -> java.time.Duration.ofDays(rValue.toLong() * 30)
                                    "year" -> java.time.Duration.ofDays(rValue.toLong() * 365)
                                    else -> java.time.Duration.ZERO
                                }
                                if (todoDateTime.minus(offsetDuration).isBefore(LocalDateTime.now())) {
                                    alertState = AlertInfo("提醒时间无效", "设置的提醒时间早于当前时刻。\n请调整提前时间或修改日程开始时间。")
                                    return@TextButton
                                }
                            }
                        }

                        performSave()
                    }) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                    if (isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("输入标题", style = MaterialTheme.typography.headlineSmall, color = Color.Gray) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("添加描述", style = MaterialTheme.typography.bodyLarge, color = Color.Gray) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                )
            }
            HorizontalDivider()

            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text("分类标签", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_TAGS.forEach { tag ->
                        val isSelected = selectedTagValue == tag.value
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTagValue = tag.value
                                if (tag.isSpecial) {
                                    repeatType = "yearly"
                                    startTime = LocalTime.of(10, 0)
                                    if (tag.value == "birthday") selectedColor = "#E91E63"
                                }
                            },
                            label = { Text(tag.label) },
                            leadingIcon = { Icon(tag.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                if (selectedTagValue == "custom") {
                    OutlinedTextField(
                        value = customTagName, onValueChange = { customTagName = it },
                        label = { Text("自定义标签名称") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("日期", style = MaterialTheme.typography.titleMedium)
                    }
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), border = null
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("时间", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { showStartTimePicker = true },
                            label = { Text(startTime.format(DateTimeFormatter.ofPattern("HH:mm"))) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), border = null
                        )
                        Text("-", color = Color.Gray)
                        AssistChip(
                            onClick = { showEndTimePicker = true },
                            label = { Text(endTime.format(DateTimeFormatter.ofPattern("HH:mm"))) },
                            leadingIcon = { Icon(Icons.Default.Stop, null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), border = null
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            val isRepeatSet = repeatType != "none"
            Column {
                ListItem(
                    headlineContent = { Text("重复规则", fontWeight = if(isRepeatSet) FontWeight.Bold else FontWeight.Normal) },
                    trailingContent = {
                        Button(
                            onClick = { showRepeatDialog = true },
                            colors = if (isRepeatSet) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.textButtonColors(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(REPEAT_OPTIONS.find { it.first == repeatType }?.second ?: "不重复", color = if (isRepeatSet) Color.White else MaterialTheme.colorScheme.primary)
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Refresh, null, tint = if (isRepeatSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                AnimatedVisibility(visible = isRepeatSet, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp)
                    ) {
                        Text("结束条件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        REPEAT_END_OPTIONS.forEach { (type, label) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { repeatEndType = type }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = repeatEndType == type, onClick = null)
                                Text(label)
                            }
                        }
                        if (repeatEndType == "date") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("选择结束日期")
                                AssistChip(onClick = { showEndDateDatePicker = true }, label = { Text(repeatEndDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))) })
                            }
                        } else if (repeatEndType == "count") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            OutlinedTextField(value = repeatCount, onValueChange = { if (it.all { c -> c.isDigit() }) repeatCount = it }, label = { Text("重复次数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            Column {
                ListItem(
                    headlineContent = { Text("开启提醒") },
                    leadingContent = { Icon(if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null, tint = if (hasReminder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Switch(checked = hasReminder, onCheckedChange = { hasReminder = it }) }
                )
                AnimatedVisibility(visible = hasReminder, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp)
                    ) {
                        val limit = REMINDER_LIMITS[reminderUnit] ?: 60f
                        Text("提前时间: ${reminderValue.toInt()} ${REMINDER_UNITS_ROW1.plus(REMINDER_UNITS_ROW2).find { it.first == reminderUnit }?.second}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            REMINDER_UNITS_ROW1.forEach { (u, l) -> UnitChip(l, reminderUnit == u) { reminderUnit = u; if (reminderValue > (REMINDER_LIMITS[u] ?: 60f)) reminderValue = REMINDER_LIMITS[u] ?: 60f } }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            REMINDER_UNITS_ROW2.forEach { (u, l) -> UnitChip(l, reminderUnit == u) { reminderUnit = u; if (reminderValue > (REMINDER_LIMITS[u] ?: 60f)) reminderValue = REMINDER_LIMITS[u] ?: 60f } }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if(reminderValue > 1) reminderValue -= 1 }) { Icon(Icons.Default.Remove, null) }
                            Slider(value = reminderValue, onValueChange = { reminderValue = it }, valueRange = 1f..limit, steps = if(limit<=1) 0 else (limit-2).toInt(), modifier = Modifier.weight(1f))
                            IconButton(onClick = { if(reminderValue < limit) reminderValue += 1 }) { Icon(Icons.Default.Add, null) }
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Column(modifier = Modifier.padding(16.dp)) {
                Text("标记颜色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EXTENDED_COLOR_OPTIONS.chunked(5).forEach { rowColors ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowColors.forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                val isSelected = selectedColor.equals(colorHex, ignoreCase = true)
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(color)
                                        .clickable { selectedColor = colorHex }
                                        .border(3.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            repeat(5 - rowColors.size) { Spacer(modifier = Modifier.size(40.dp)) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEndDateDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = repeatEndDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDateDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        repeatEndDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showEndDateDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEndDateDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showStartTimePicker) {
        val timeState = rememberTimePickerState(initialHour = startTime.hour, initialMinute = startTime.minute, is24Hour = true)
        TimePickerDialog(onDismissRequest = { showStartTimePicker = false }, onConfirm = { startTime = LocalTime.of(timeState.hour, timeState.minute); showStartTimePicker = false }) { TimePicker(state = timeState) }
    }
    if (showEndTimePicker) {
        val timeState = rememberTimePickerState(initialHour = endTime.hour, initialMinute = endTime.minute, is24Hour = true)
        TimePickerDialog(onDismissRequest = { showEndTimePicker = false }, onConfirm = { endTime = LocalTime.of(timeState.hour, timeState.minute); showEndTimePicker = false }) { TimePicker(state = timeState) }
    }

    // 🔥 重复规则弹窗：使用自定义样式
    if (showRepeatDialog) {
        CustomAlertDialog(
            title = "重复规则",
            onDismiss = { showRepeatDialog = false },
            showCancel = true,
            onConfirm = { showRepeatDialog = false },
            confirmLabel = "确定"
        ) {
            Column {
                REPEAT_OPTIONS.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repeatType = value }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = repeatType == value, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontWeight = if (repeatType == value) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }

    // 🔥 统一的提示/警告弹窗
    alertState?.let { info ->
        CustomAlertDialog(
            title = info.title,
            onDismiss = {
                // 如果没有取消按钮（比如只有“我知道了”），点击外部也应该关闭
                // 如果有取消按钮，点击外部通常不关闭，或者视为取消
                if (!info.showCancel) {
                    alertState = null
                    info.onConfirm() // 可选：点击外部是否视为确认？通常视为取消。
                } else {
                    alertState = null // 点击取消/外部
                }
            },
            showCancel = info.showCancel,
            confirmLabel = info.confirmLabel,
            onConfirm = {
                // 🔥🔥🔥 关键修复：先执行逻辑，然后关闭弹窗
                info.onConfirm()
                alertState = null
            }
        ) {
            Text(info.message, style = MaterialTheme.typography.bodyMedium)
        }
    }

    // 🔥 删除确认弹窗：使用自定义样式
    if (showDeleteDialog) {
        val originalRepeatType = todoToEdit?.repeatType ?: "none"
        val isOriginalRepeating = originalRepeatType != "none" || (todoToEdit?.isYearly == true)

        CustomAlertDialog(
            title = if(isOriginalRepeating) "删除重复事项" else "确认删除",
            onDismiss = { showDeleteDialog = false },
            showCancel = true,
            onConfirm = {
                if (!isOriginalRepeating && !isNavigating) {
                    isNavigating = true
                    if (todoToEdit != null) viewModel.deleteTodo(todoToEdit)
                    onBack(deleteTargetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
            },
            showConfirmButton = !isOriginalRepeating // 只有非重复事项才显示默认的“确定”按钮
        ) {
            if (isOriginalRepeating && todoToEdit != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                showDeleteDialog = false
                                val newExcluded = if (todoToEdit.excludedDates.isEmpty()) deleteTargetDate.format(DateTimeFormatter.ISO_LOCAL_DATE) else "${todoToEdit.excludedDates},${deleteTargetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                                viewModel.updateTodo(todoToEdit.copy(excludedDates = newExcluded))
                                onBack(deleteTargetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("仅删除本次") }

                    OutlinedButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                showDeleteDialog = false
                                val newEndDate = deleteTargetDate.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                                viewModel.updateTodo(todoToEdit.copy(repeatEndType = "date", repeatEndDate = newEndDate))
                                onBack(deleteTargetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("删除本次及以后") }

                    TextButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                showDeleteDialog = false
                                viewModel.deleteTodo(todoToEdit)
                                onBack(deleteTargetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("删除所有") }
                }
            } else {
                Text("确定要删除此事项吗？此操作无法撤销。")
            }
        }
    }
}

// 辅助类：用于统一管理提示弹窗的信息
data class AlertInfo(
    val title: String,
    val message: String,
    val showCancel: Boolean = false,
    val confirmLabel: String = "我知道了",
    val onConfirm: () -> Unit = {}
)

// 🔥 新增：美化的通用 Android 原生风格 Dialog 组件
@Composable
fun CustomAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    showCancel: Boolean = false,
    confirmLabel: String = "确定",
    onConfirm: () -> Unit,
    showConfirmButton: Boolean = true,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        },
        confirmButton = {
            if (showConfirmButton) {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (showCancel) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        shape = RoundedCornerShape(24.dp), // 更圆润的角，符合 Material 3 规范
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, textAlign = TextAlign.Center, modifier = Modifier.width(30.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White))
}

@Composable
fun TimePickerDialog(onDismissRequest: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismissRequest) { Text("取消") } }, text = { content() })
}