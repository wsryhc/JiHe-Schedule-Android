package com.example.jihe_schedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jihe_schedule.data.CourseTime
import com.example.jihe_schedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseTimeManagerScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val pMorning by viewModel.periodMorning.collectAsState()
    val pAfternoon by viewModel.periodAfternoon.collectAsState()
    val pEvening by viewModel.periodEvening.collectAsState()
    val totalPeriods = pMorning + pAfternoon + pEvening

    val savedTimes by viewModel.courseTimes.collectAsState()

    // 全局配置
    var classDuration by remember { mutableStateOf("45") }
    var breakDuration by remember { mutableStateOf("10") }

    // 用于显示错误提示 (Snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 🔥 核心修改：记录具体哪一节课冲突 (Index)
    var conflictIndices by remember { mutableStateOf(emptySet<Int>()) }

    // 当前编辑的时间列表
    var currentTimes by remember(savedTimes, totalPeriods) {
        mutableStateOf(
            if (savedTimes.isNotEmpty() && savedTimes.size == totalPeriods) {
                savedTimes
            } else {
                List(totalPeriods) { CourseTime("00:00", "00:00") }
            }
        )
    }

    // 控制对话框显示
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    // --- 核心逻辑方法 ---

    // 🔥 0. 冲突检测逻辑：全量扫描
    fun checkConflicts(times: List<CourseTime>): Set<Int> {
        val conflicts = mutableSetOf<Int>()
        if (times.isEmpty()) return conflicts

        for (i in 0 until times.size - 1) {
            val prevEnd = times[i].getEndTimeAsLocalTime()
            val nextStart = times[i + 1].getStartTimeAsLocalTime()

            // 如果 下一节开始 < 上一节结束，标记下一节为冲突
            if (nextStart.isBefore(prevEnd)) {
                conflicts.add(i + 1)
            }
        }
        return conflicts
    }

    // 1. 级联更新（修改开始时间时使用） + 午夜检查
    fun updateStartTimeAndCascade(index: Int, newStartTime: LocalTime) {
        val newTimes = currentTimes.toMutableList()
        val duration = classDuration.toLongOrNull() ?: 45L
        val breakDur = breakDuration.toLongOrNull() ?: 10L
        var currentTime = newStartTime

        // 确定当前是哪个时段
        val morningEnd = pMorning - 1
        val afternoonEnd = pMorning + pAfternoon - 1

        val (startIndex, endIndex) = when (index) {
            in 0..morningEnd -> index to morningEnd
            in pMorning..afternoonEnd -> index to afternoonEnd
            else -> index to totalPeriods - 1
        }

        var hitMidnight = false

        for (i in startIndex..endIndex) {
            if (i < newTimes.size) {
                // 计算结束时间
                val endTime = currentTime.plusMinutes(duration)

                // 🔥 午夜检查
                if (endTime.isBefore(currentTime) || endTime == LocalTime.MIDNIGHT) {
                    newTimes[i] = CourseTime.from(currentTime, LocalTime.of(23, 59))
                    hitMidnight = true
                    break
                } else {
                    newTimes[i] = CourseTime.from(currentTime, endTime)
                    val nextStart = endTime.plusMinutes(breakDur)
                    if (nextStart.isBefore(endTime)) {
                        hitMidnight = true
                        break
                    }
                    currentTime = nextStart
                }
            }
        }
        currentTimes = newTimes
        // 🔥 每次修改后立即检查冲突
        conflictIndices = checkConflicts(newTimes)

        if (hitMidnight) {
            scope.launch { snackbarHostState.showSnackbar("提示：推算时间已超过午夜，自动截止于 23:59") }
        }
    }

    // 2. 单独更新结束时间
    fun updateEndTimeOnly(index: Int, newEndTime: LocalTime) {
        val newTimes = currentTimes.toMutableList()
        val currentStart = newTimes[index].getStartTimeAsLocalTime()

        if (newEndTime.isAfter(currentStart)) {
            newTimes[index] = CourseTime.from(currentStart, newEndTime)
            currentTimes = newTimes
            // 🔥 每次修改后立即检查冲突
            conflictIndices = checkConflicts(newTimes)
        } else {
            scope.launch { snackbarHostState.showSnackbar("结束时间必须晚于开始时间") }
        }
    }

    // 3. 重置某一时段
    fun resetSection(sectionStartHour: Int, startIndex: Int, count: Int) {
        val newTimes = currentTimes.toMutableList()
        val duration = classDuration.toLongOrNull() ?: 45L
        val breakDur = breakDuration.toLongOrNull() ?: 10L

        var currentTime = LocalTime.of(sectionStartHour, 0)
        var hitMidnight = false

        for (i in 0 until count) {
            val globalIndex = startIndex + i
            if (globalIndex < newTimes.size) {
                val endTime = currentTime.plusMinutes(duration)

                if (endTime.isBefore(currentTime) || endTime == LocalTime.MIDNIGHT) {
                    newTimes[globalIndex] = CourseTime.from(currentTime, LocalTime.of(23, 59))
                    hitMidnight = true
                    break
                } else {
                    newTimes[globalIndex] = CourseTime.from(currentTime, endTime)
                    val nextStart = endTime.plusMinutes(breakDur)
                    if (nextStart.isBefore(endTime)) {
                        hitMidnight = true
                        break
                    }
                    currentTime = nextStart
                }
            }
        }
        currentTimes = newTimes
        // 🔥 每次修改后立即检查冲突
        conflictIndices = checkConflicts(newTimes)

        if (hitMidnight) {
            scope.launch { snackbarHostState.showSnackbar("提示：重置时间超过午夜，部分课程已截止于 23:59") }
        }
    }

    // 4. 保存前的校验逻辑 (最终防线)
    fun validateAndSave() {
        val conflicts = checkConflicts(currentTimes)

        if (conflicts.isNotEmpty()) {
            conflictIndices = conflicts // 更新高亮
            scope.launch { snackbarHostState.showSnackbar("保存失败：存在时间冲突（后一节课开始时间早于前一节结束），请检查标红课程。") }
        } else {
            viewModel.saveCourseTimes(currentTimes)
            onBack()
        }
    }

    // 初始化时检查一次
    LaunchedEffect(currentTimes) {
        conflictIndices = checkConflicts(currentTimes)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("课程时间管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { validateAndSave() }) {
                        Text("保存", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 全局设置卡片
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("全局默认设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingInput(
                                label = "每节课时长",
                                value = classDuration,
                                max = 60,
                                onValueChange = { classDuration = it },
                                modifier = Modifier.weight(1f)
                            )
                            SettingInput(
                                label = "课间休息",
                                value = breakDuration,
                                max = 30,
                                onValueChange = { breakDuration = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(14.dp).padding(top=2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "此处设置仅作为推算基准。修改数值将影响“一键重置”及“修改开始时间”后的自动计算结果。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // 2. 上午时段
            if (pMorning > 0) {
                item {
                    SectionGroup(
                        title = "上午课程",
                        subtitle = "建议 08:00 开始",
                        iconColor = Color(0xFFFF9800),
                        onReset = { resetSection(8, 0, pMorning) }
                    ) {
                        Column {
                            repeat(pMorning) { i ->
                                TimeSlotItem(
                                    index = i,
                                    time = currentTimes.getOrElse(i) { CourseTime("00:00", "00:00") },
                                    isLast = i == pMorning - 1,
                                    isConflict = conflictIndices.contains(i), // 🔥 传入冲突状态
                                    onClick = { editingIndex = i }
                                )
                            }
                        }
                    }
                }
            }

            // 3. 下午时段
            if (pAfternoon > 0) {
                item {
                    SectionGroup(
                        title = "下午课程",
                        subtitle = "建议 13:00 或 14:00 开始",
                        iconColor = Color(0xFF2196F3),
                        onReset = { resetSection(13, pMorning, pAfternoon) }
                    ) {
                        Column {
                            repeat(pAfternoon) { i ->
                                val realIndex = pMorning + i
                                TimeSlotItem(
                                    index = realIndex,
                                    time = currentTimes.getOrElse(realIndex) { CourseTime("00:00", "00:00") },
                                    isLast = i == pAfternoon - 1,
                                    isConflict = conflictIndices.contains(realIndex), // 🔥 传入冲突状态
                                    onClick = { editingIndex = realIndex }
                                )
                            }
                        }
                    }
                }
            }

            // 4. 晚上时段
            if (pEvening > 0) {
                item {
                    SectionGroup(
                        title = "晚上课程",
                        subtitle = "建议 18:00 或 19:00 开始",
                        iconColor = Color(0xFF673AB7),
                        onReset = { resetSection(18, pMorning + pAfternoon, pEvening) }
                    ) {
                        Column {
                            repeat(pEvening) { i ->
                                val realIndex = pMorning + pAfternoon + i
                                TimeSlotItem(
                                    index = realIndex,
                                    time = currentTimes.getOrElse(realIndex) { CourseTime("00:00", "00:00") },
                                    isLast = i == pEvening - 1,
                                    isConflict = conflictIndices.contains(realIndex), // 🔥 传入冲突状态
                                    onClick = { editingIndex = realIndex }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        // --- 弹窗逻辑 ---
        if (editingIndex != null) {
            val idx = editingIndex!!
            val currentTime = currentTimes.getOrElse(idx) { CourseTime("08:00", "08:45") }

            EditTimeDialog(
                title = "编辑第 ${idx + 1} 节时间",
                startTime = currentTime.getStartTimeAsLocalTime(),
                endTime = currentTime.getEndTimeAsLocalTime(),
                onDismiss = { editingIndex = null },
                onStartTimeConfirm = { newStart ->
                    updateStartTimeAndCascade(idx, newStart)
                    editingIndex = null
                },
                onEndTimeConfirm = { newEnd ->
                    updateEndTimeOnly(idx, newEnd)
                    editingIndex = null
                }
            )
        }
    }
}

// --- 组件封装 ---

@Composable
fun SectionGroup(
    title: String,
    subtitle: String,
    iconColor: Color,
    onReset: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(iconColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = "重置", tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
fun TimeSlotItem(
    index: Int,
    time: CourseTime,
    isLast: Boolean,
    isConflict: Boolean, // 🔥 新增：是否冲突
    onClick: () -> Unit
) {
    // 🔥 如果冲突，背景变为淡红色
    val bgColor = if (isConflict) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color.Transparent
    val timeColor = if (isConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor) // 应用背景色
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if(isConflict) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if(isConflict) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("第 ${index + 1} 节", style = MaterialTheme.typography.bodyLarge)
                    // 🔥 显示冲突提示
                    if (isConflict) {
                        Text("⚠️ 时间冲突", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${time.start} - ${time.end}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = timeColor // 应用时间颜色
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SettingInput(
    label: String,
    value: String,
    max: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.isEmpty()) {
                onValueChange(input)
                return@OutlinedTextField
            }
            if (input.all { it.isDigit() }) {
                val num = input.toIntOrNull()
                if (num != null && num <= max) {
                    onValueChange(input)
                }
            }
        },
        label = { Text("$label (Max $max)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

// 复杂弹窗和按钮组件保持不变
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTimeDialog(
    title: String,
    startTime: LocalTime,
    endTime: LocalTime,
    onDismiss: () -> Unit,
    onStartTimeConfirm: (LocalTime) -> Unit,
    onEndTimeConfirm: (LocalTime) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        TimePickerDialog(
            initialTime = startTime,
            onDismissRequest = { showStartPicker = false },
            onConfirm = {
                showStartPicker = false
                onStartTimeConfirm(it)
            }
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            initialTime = endTime,
            onDismissRequest = { showEndPicker = false },
            onConfirm = {
                showEndPicker = false
                onEndTimeConfirm(it)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("请选择要修改的时间点", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimeDisplayButton(label = "开始时间", time = startTime, onClick = { showStartPicker = true })
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.align(Alignment.CenterVertically).rotate(180f), tint = MaterialTheme.colorScheme.outline)
                    TimeDisplayButton(label = "结束时间", time = endTime, onClick = { showEndPicker = true })
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("💡 提示：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("• 修改开始时间会自动推算后续课程", style = MaterialTheme.typography.bodySmall)
                        Text("• 修改结束时间仅改变当前课程时长", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun TimeDisplayButton(label: String, time: LocalTime, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

fun Modifier.rotate(degrees: Float) = this.then(Modifier.graphicsLayer(rotationZ = degrees))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialTime: LocalTime, onDismissRequest: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val timePickerState = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("取消") } }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TimePicker(state = timePickerState)
        }
    }
}