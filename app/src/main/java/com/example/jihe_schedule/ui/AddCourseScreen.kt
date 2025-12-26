package com.example.jihe_schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.viewmodel.ScheduleViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCourseScreen(
    courseToEdit: Course? = null,
    initialDay: Int = 1,
    initialPeriod: Int = 1,
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    // --- 数据源获取 ---
    val currentSchedule by viewModel.currentSchedule.collectAsState()
    val existingCourses by viewModel.currentCourses.collectAsState() // 获取已有课程用于冲突检测
    val maxWeeks = currentSchedule?.totalWeeks ?: 25

    // --- 状态定义 ---
    var name by remember { mutableStateOf(courseToEdit?.name ?: "") }
    var teacher by remember { mutableStateOf(courseToEdit?.teacher ?: "") }
    var classroom by remember { mutableStateOf(courseToEdit?.classroom ?: "") }

    var day by remember { mutableIntStateOf(courseToEdit?.day ?: initialDay) }

    // 节次状态
    var startPeriod by remember { mutableIntStateOf(courseToEdit?.startPeriod ?: initialPeriod) }
    var endPeriod by remember { mutableIntStateOf(courseToEdit?.endPeriod ?: initialPeriod) }

    // 默认周次
    var selectedWeeks by remember { mutableStateOf(courseToEdit?.weeks ?: (1..maxWeeks).toList()) }
    var selectedColorHex by remember { mutableStateOf(courseToEdit?.color ?: "#2196F3") }

    // 冲突弹窗状态
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    // 20种鲜艳颜色
    val colorList = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
        "#FF5722", "#F06292", "#BA68C8", "#4DD0E1", "#AED581"
    )

    // 基础校验：名称不为空 且 周次不为空
    val isValid = name.isNotBlank() && selectedWeeks.isNotEmpty()

    // 保存逻辑封装
    fun performSave() {
        val newCourse = Course(
            id = courseToEdit?.id ?: 0,
            name = name,
            teacher = teacher,
            classroom = classroom,
            day = day,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weeks = selectedWeeks.sorted(),
            color = selectedColorHex,
            scheduleId = courseToEdit?.scheduleId ?: (currentSchedule?.id ?: "1")
        )

        if (courseToEdit == null) {
            viewModel.insertCourse(newCourse)
        } else {
            viewModel.updateCourse(newCourse)
        }
        onBack()
    }

    // 冲突检测逻辑
    fun checkAndSave() {
        if (!isValid) return

        val conflict = checkConflictSingle(
            newCourse = Course(
                id = courseToEdit?.id ?: 0,
                name = name,
                day = day,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weeks = selectedWeeks,
                scheduleId = "", color = "", teacher = "", classroom = ""
            ),
            existingCourses = existingCourses
        )

        if (conflict != null) {
            conflictMessage = conflict
            showConflictDialog = true
        } else {
            performSave()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (courseToEdit == null) "添加课程" else "编辑课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { checkAndSave() },
                        enabled = isValid
                    ) {
                        Text("保存")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // --- 模块 1: 基础信息 ---
            InfoCard(title = "基础信息", icon = Icons.Default.Info) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Book, null) }
                    )

                    OutlinedTextField(
                        value = classroom,
                        onValueChange = { classroom = it },
                        label = { Text("教室地点") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) }
                    )

                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("授课教师") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )
                }
            }

            // --- 模块 2: 时间设置 ---
            InfoCard(title = "上课时间", icon = Icons.Outlined.AccessTime) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 1. 星期选择
                    Column {
                        Text("星期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (1..7).forEach { d ->
                                val isSelected = day == d
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { day = d },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        mapIntToChineseDay(d),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. 节次选择 (Grid 样式替代 Slider)
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("节次选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "第 $startPeriod - $endPeriod 节",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3x6 网格布局选择节次
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            // 固定高度
                            modifier = Modifier.height(150.dp)
                        ) {
                            items(18) { index ->
                                val p = index + 1
                                val isSelected = p in startPeriod..endPeriod
                                val isStart = p == startPeriod
                                val isEnd = p == endPeriod

                                // 颜色逻辑
                                val containerColor = when {
                                    isStart || isEnd -> MaterialTheme.colorScheme.primary
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val contentColor = when {
                                    isStart || isEnd -> MaterialTheme.colorScheme.onPrimary
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(containerColor)
                                        .clickable {
                                            if (p < startPeriod) {
                                                // 往前扩展
                                                startPeriod = p
                                            } else if (p > endPeriod) {
                                                // 往后扩展
                                                endPeriod = p
                                            } else {
                                                // 点击中间或端点，重置为单选该点，方便重新划范围
                                                startPeriod = p
                                                endPeriod = p
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$p",
                                        color = contentColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Text(
                            text = "提示：点击两端可连选，点击中间重置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // --- 模块 3: 周次设置 ---
            InfoCard(title = "上课周次 (共${maxWeeks}周)", icon = Icons.Outlined.DateRange) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 快捷按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeButton(text = "全选", modifier = Modifier.weight(1f)) {
                            selectedWeeks = (1..maxWeeks).toList()
                        }
                        ThemeButton(text = "单周", modifier = Modifier.weight(1f)) {
                            selectedWeeks = (1..maxWeeks).filter { it % 2 != 0 }
                        }
                        ThemeButton(text = "双周", modifier = Modifier.weight(1f)) {
                            selectedWeeks = (1..maxWeeks).filter { it % 2 == 0 }
                        }
                        ThemeButton(text = "清空", modifier = Modifier.weight(1f), isDestructive = true) {
                            selectedWeeks = emptyList()
                        }
                    }

                    // 周次网格
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..maxWeeks).forEach { w ->
                            val isSelected = selectedWeeks.contains(w)
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val currentList = selectedWeeks.toMutableList()
                                        if (currentList.contains(w)) currentList.remove(w) else currentList.add(w)
                                        selectedWeeks = currentList.sorted()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$w",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    if (selectedWeeks.isEmpty()) {
                        Text(
                            "⚠ 请至少选择一个周次",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            "已选 ${selectedWeeks.size} 周",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // --- 模块 4: 颜色选择 ---
            InfoCard(title = "课程卡片颜色", icon = Icons.Outlined.Palette) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 5 // 每行5个
                ) {
                    colorList.forEach { colorHex ->
                        val isSelected = colorHex == selectedColorHex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- 删除按钮 ---
            if (courseToEdit != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.deleteCourse(courseToEdit)
                        onBack()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除此课程")
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // --- 冲突提示弹窗 (无强制保存选项) ---
    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("课程时间冲突") },
            text = { Text("检测到时间冲突：\n$conflictMessage\n\n请修改时间或周次后重试。") },
            confirmButton = {
                TextButton(onClick = { showConflictDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

// --- 辅助逻辑与组件 ---

/**
 * 检查单个课程是否与现有列表冲突
 * 返回冲突原因字符串，若无冲突返回 null
 */
fun checkConflictSingle(newCourse: Course, existingCourses: List<Course>): String? {
    for (old in existingCourses) {
        // 排除自身（编辑模式下）
        if (old.id.toLong() != 0L && old.id == newCourse.id) continue

        // 1. 星期相同
        if (old.day == newCourse.day) {
            // 2. 节次重叠
            // 逻辑：max(start1, start2) <= min(end1, end2) 代表有交集
            val startMax = max(old.startPeriod, newCourse.startPeriod)
            val endMin = min(old.endPeriod, newCourse.endPeriod)

            if (startMax <= endMin) {
                // 3. 周次有交集
                val intersect = old.weeks.intersect(newCourse.weeks.toSet())
                if (intersect.isNotEmpty()) {
                    return "与【${old.name}】在周${mapIntToChineseDay(old.day)} ${old.startPeriod}-${old.endPeriod}节冲突"
                }
            }
        }
    }
    return null
}

@Composable
fun InfoCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
fun ThemeButton(
    text: String,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// 确保引用到了 mapIntToChineseDay，如果没有全局定义，可以取消下面的注释
/*
fun mapIntToChineseDay(day: Int): String {
    return when (day) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> ""
    }
}
*/