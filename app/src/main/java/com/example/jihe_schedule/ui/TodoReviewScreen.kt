package com.example.jihe_schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jihe_schedule.model.Todo
import com.example.jihe_schedule.viewmodel.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoReviewScreen(
    viewModel: TodoViewModel,
    onBack: () -> Unit
) {
    val pendingData by viewModel.pendingReviewTodos.collectAsState()
    var todos by remember { mutableStateOf(pendingData ?: emptyList()) }

    if (pendingData == null && todos.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    BackHandler {
        viewModel.clearPendingReviewTodos()
        onBack()
    }

    // 全局错误检查
    val hasError = todos.any {
        val startStr = it.startTime ?: "00:00"
        val endStr = it.endTime ?: "23:59"
        val start = try { LocalTime.parse(startStr) } catch (e: Exception) { LocalTime.MIN }
        val end = try { LocalTime.parse(endStr) } catch (e: Exception) { LocalTime.MIN }
        end.isBefore(start) || (it.title.isBlank())
    }

    // --- 全局弹窗状态 (提升性能，不放在 Item 里) ---
    var activeTodoId by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf<String?>(null) } // "date", "startTime", "endTime", "repeatEnd", "repeat"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入校对") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearPendingReviewTodos()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "放弃")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveReviewedTodos(todos)
                            onBack()
                        },
                        enabled = !hasError,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("保存全部")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {

            if (hasError) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("存在时间错误或标题为空，请修正！", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text("共导入 ${todos.size} 条数据，请确认信息无误。", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(todos, key = { it.id }) { todo ->
                    ReviewTodoItem(
                        todo = todo,
                        onUpdate = { updated -> todos = todos.map { if (it.id == updated.id) updated else it } },
                        onDelete = { todos = todos.filter { it.id != todo.id } },
                        onEditField = { field ->
                            activeTodoId = todo.id
                            activeField = field
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(50.dp)) }
            }
        }
    }

    // --- 全局弹窗逻辑 ---
    val currentTodo = todos.find { it.id == activeTodoId }

    if (currentTodo != null && activeField != null) {
        when (activeField) {
            "date" -> {
                val initDate = try { LocalDate.parse(currentTodo.date) } catch (e: Exception) { LocalDate.now() }
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                DatePickerDialog(
                    onDismissRequest = { activeTodoId = null },
                    confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let {
                        val newDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        val updated = currentTodo.copy(date = newDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        todos = todos.map { t -> if (t.id == currentTodo.id) updated else t }
                    }; activeTodoId = null }) { Text("确定") } },
                    dismissButton = { TextButton(onClick = { activeTodoId = null }) { Text("取消") } }
                ) { DatePicker(state = datePickerState) }
            }
            "startTime" -> {
                val time = try { LocalTime.parse(currentTodo.startTime) } catch (e: Exception) { LocalTime.of(9,0) }
                val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
                TimePickerDialog(
                    onDismissRequest = { activeTodoId = null },
                    onConfirm = {
                        val newTime = LocalTime.of(timeState.hour, timeState.minute).format(DateTimeFormatter.ofPattern("HH:mm"))
                        val updated = currentTodo.copy(startTime = newTime)
                        todos = todos.map { t -> if (t.id == currentTodo.id) updated else t }
                        activeTodoId = null
                    }
                ) { TimePicker(state = timeState) }
            }
            "endTime" -> {
                val time = try { LocalTime.parse(currentTodo.endTime) } catch (e: Exception) { LocalTime.of(10,0) }
                val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
                TimePickerDialog(
                    onDismissRequest = { activeTodoId = null },
                    onConfirm = {
                        val newTime = LocalTime.of(timeState.hour, timeState.minute).format(DateTimeFormatter.ofPattern("HH:mm"))
                        val updated = currentTodo.copy(endTime = newTime)
                        todos = todos.map { t -> if (t.id == currentTodo.id) updated else t }
                        activeTodoId = null
                    }
                ) { TimePicker(state = timeState) }
            }
            "repeatEnd" -> {
                val initDate = try { LocalDate.parse(currentTodo.repeatEndDate ?: LocalDate.now().toString()) } catch (e: Exception) { LocalDate.now() }
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                DatePickerDialog(
                    onDismissRequest = { activeTodoId = null },
                    confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let {
                        val newDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        val updated = currentTodo.copy(repeatEndDate = newDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        todos = todos.map { t -> if (t.id == currentTodo.id) updated else t }
                    }; activeTodoId = null }) { Text("确定") } },
                    dismissButton = { TextButton(onClick = { activeTodoId = null }) { Text("取消") } }
                ) { DatePicker(state = datePickerState) }
            }
            "repeat" -> {
                // 重复规则选择弹窗
                AlertDialog(
                    onDismissRequest = { activeTodoId = null },
                    title = { Text("重复规则") },
                    text = { Column { REPEAT_OPTIONS.forEach { (value, label) -> Row(modifier = Modifier.fillMaxWidth().clickable {
                        val updated = currentTodo.copy(repeatType = value, isYearly = value=="yearly", repeatEndType = if(value=="none") "never" else currentTodo.repeatEndType)
                        todos = todos.map { t -> if (t.id == currentTodo.id) updated else t }
                        activeTodoId = null
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentTodo.repeatType == value, onClick = null); Spacer(modifier = Modifier.width(8.dp)); Text(label, fontWeight = if(currentTodo.repeatType == value) FontWeight.Bold else FontWeight.Normal) } } } },
                    confirmButton = { TextButton(onClick = { activeTodoId = null }) { Text("取消") } }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewTodoItem(
    todo: Todo,
    onUpdate: (Todo) -> Unit,
    onDelete: () -> Unit,
    onEditField: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // 数据清洗
    val startStr = todo.startTime ?: "00:00"
    val endStr = todo.endTime ?: "23:59"
    val colorStr = todo.color ?: EXTENDED_COLOR_OPTIONS[0]
    val tagStr = todo.tag ?: "默认"
    val tagTypeStr = todo.tagType ?: "default"
    val dateStr = todo.date ?: ""

    val startT = try { LocalTime.parse(startStr) } catch (e: Exception) { LocalTime.MIN }
    val endT = try { LocalTime.parse(endStr) } catch (e: Exception) { LocalTime.MAX }
    val isTimeError = !endT.isAfter(startT)
    val isTitleError = todo.title.isBlank()

    val cardBgColor = if (isTimeError || isTitleError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().border(
            width = if(isTimeError || isTitleError) 1.dp else 0.dp,
            color = if(isTimeError || isTitleError) MaterialTheme.colorScheme.error else Color.Transparent,
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(try { Color(android.graphics.Color.parseColor(colorStr)) } catch(e:Exception){ Color.Gray }))
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(todo.title.ifBlank { "标题为空 (点击修改)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if(isTitleError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$dateStr $startStr-$endStr | $tagStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isTimeError) Text("时间错误: 结束 < 开始", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(if(isExpanded) Icons.Default.ExpandLess else Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.outline)
                }
            }

            // 展开编辑区 (仿 TodoEditScreen)
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 1. 标题
                OutlinedTextField(
                    value = todo.title,
                    onValueChange = { onUpdate(todo.copy(title = it)) },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isTitleError
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 2. 标签选择 (参照 TodoEditScreen)
                Text("分类标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    PRESET_TAGS.forEach { tag ->
                        val isSelected = tagTypeStr == tag.value
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val tagName = if (tag.value == "custom") todo.tag else tag.label
                                var updated = todo.copy(tagType = tag.value, tag = tagName)
                                if (tag.isSpecial) {
                                    updated = updated.copy(repeatType = "yearly", startTime = "10:00")
                                    if(tag.value=="birthday") updated = updated.copy(color = "#E91E63")
                                }
                                onUpdate(updated)
                            },
                            label = { Text(tag.label) },
                            leadingIcon = { Icon(tag.icon, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                if (tagTypeStr == "custom") {
                    OutlinedTextField(
                        value = todo.tag ?: "",
                        onValueChange = { onUpdate(todo.copy(tag = it)) },
                        label = { Text("自定义标签名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // 3. 日期和时间 (Chip 样式)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onEditField("date") },
                        label = { Text(dateStr.ifBlank { "选择日期" }) },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onEditField("startTime") },
                        label = { Text(startStr) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f),
                        colors = if(isTimeError) AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error) else AssistChipDefaults.assistChipColors()
                    )
                    Text("-", modifier = Modifier.align(Alignment.CenterVertically))
                    AssistChip(
                        onClick = { onEditField("endTime") },
                        label = { Text(endStr) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f),
                        colors = if(isTimeError) AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error) else AssistChipDefaults.assistChipColors()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // 4. 重复规则
                val isRepeatSet = todo.repeatType != "none"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onEditField("repeat") }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, null, tint = if(isRepeatSet) MaterialTheme.colorScheme.primary else Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(REPEAT_OPTIONS.find { it.first == todo.repeatType }?.second ?: "不重复")
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                        // 重复详情 (结束条件)
                        AnimatedVisibility(visible = isRepeatSet) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                REPEAT_END_OPTIONS.forEach { (type, label) ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { onUpdate(todo.copy(repeatEndType = type)) }, verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = todo.repeatEndType == type, onClick = null, modifier = Modifier.size(30.dp))
                                        Text(label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (todo.repeatEndType == "date") {
                                    AssistChip(onClick = { onEditField("repeatEnd") }, label = { Text("截止: ${todo.repeatEndDate ?: "请选择"}") })
                                } else if (todo.repeatEndType == "count") {
                                    OutlinedTextField(
                                        value = todo.repeatCount?.toString() ?: "10",
                                        onValueChange = { if(it.all{c->c.isDigit()}) onUpdate(todo.copy(repeatCount = it.toIntOrNull())) },
                                        label = { Text("次数") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 5. 提醒
                val hasReminder = todo.reminderValue != null
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if(hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null, tint = if(hasReminder) MaterialTheme.colorScheme.primary else Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("提醒")
                            }
                            Switch(
                                checked = hasReminder,
                                onCheckedChange = { onUpdate(todo.copy(reminderValue = if(it) 15 else null, reminderUnit = if(it) "minute" else null)) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                        // 提醒详情
                        AnimatedVisibility(visible = hasReminder) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val rVal = todo.reminderValue?.toFloat() ?: 15f
                                val rUnit = todo.reminderUnit ?: "minute"
                                val limit = REMINDER_LIMITS[rUnit] ?: 60f
                                Text("提前: ${rVal.toInt()} ${REMINDER_UNITS_ROW1.plus(REMINDER_UNITS_ROW2).find{it.first==rUnit}?.second}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = rVal,
                                    onValueChange = { onUpdate(todo.copy(reminderValue = it.toInt())) },
                                    valueRange = 1f..limit,
                                    steps = if(limit<=1) 0 else (limit-2).toInt()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    REMINDER_UNITS_ROW1.forEach { (u, l) ->
                                        FilterChip(selected = rUnit==u, onClick = { onUpdate(todo.copy(reminderUnit = u, reminderValue = 1)) }, label = { Text(l) })
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // 6. 颜色选择 (网格)
                Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top=8.dp)) {
                    EXTENDED_COLOR_OPTIONS.chunked(5).forEach { rowColors ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowColors.forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                val isSelected = colorStr.equals(colorHex, ignoreCase = true)
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                                        .clickable { onUpdate(todo.copy(color = colorHex)) }
                                        .border(2.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            repeat(5 - rowColors.size) { Spacer(modifier = Modifier.size(36.dp)) }
                        }
                    }
                }
            }
        }
    }
}