package com.example.jihe_schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.viewmodel.EditableCourseWrapper
import com.example.jihe_schedule.viewmodel.ScheduleManagementViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleReviewScreen(
    viewModel: ScheduleManagementViewModel,
    onBack: () -> Unit
) {
    val pendingData by viewModel.pendingReviewData.collectAsState()

    BackHandler {
        viewModel.discardReview()
    }

    if (pendingData == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val (initialSchedule, initialCourses) = pendingData!!

    var scheduleName by remember { mutableStateOf(initialSchedule.name) }
    var totalWeeks by remember { mutableFloatStateOf(initialSchedule.totalWeeks.toFloat()) }

    val courseWrappers = remember { mutableStateListOf<EditableCourseWrapper>().apply { addAll(initialCourses) } }

    val hasError = courseWrappers.any { it.hasConflict || it.course.weeks.isEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 🔥 修改：使用 Column 包裹标题和副标题
                    Column {
                        Text("结果校对")
                        Text(
                            text = "开学时间请编辑好后在课表管理修改",
                            style = MaterialTheme.typography.labelSmall, // 使用较小的字号
                            color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用次级文本颜色（灰色）
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val finalSchedule = initialSchedule.copy(
                                name = scheduleName.ifBlank { "未命名课表" },
                                totalWeeks = totalWeeks.toInt()
                            )
                            val cleanedWrappers = courseWrappers.map { wrapper ->
                                val validWeeks = wrapper.course.weeks.filter { it <= totalWeeks.toInt() }
                                wrapper.copy(course = wrapper.course.copy(weeks = validWeeks))
                            }
                            viewModel.saveReviewedSchedule(finalSchedule, cleanedWrappers) { }
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
        Column(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(16.dp)) {

            // 顶部设置区
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = scheduleName,
                        onValueChange = { scheduleName = it },
                        label = { Text("课表名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前学期总周数", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("${totalWeeks.toInt()} 周", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { if (totalWeeks > 1) totalWeeks-- }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                            Icon(Icons.Default.Remove, null)
                        }
                        Slider(
                            value = totalWeeks,
                            onValueChange = { totalWeeks = it },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.width(120.dp).padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { if (totalWeeks < 30) totalWeeks++ }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                }
            }

            if (hasError) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("存在冲突或未设置周次的课程", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items = courseWrappers, key = { it.tempId }) { wrapper ->
                    ReviewCourseItem(
                        wrapper = wrapper,
                        totalWeeks = totalWeeks.toInt(),
                        onUpdate = { newCourse ->
                            val idx = courseWrappers.indexOfFirst { it.tempId == wrapper.tempId }
                            if (idx != -1) {
                                val currentList = courseWrappers.map { it.course }.toMutableList()
                                currentList[idx] = newCourse
                                val checkedWrappers = checkConflictLocally(currentList, courseWrappers.map { it.tempId })
                                courseWrappers.clear()
                                courseWrappers.addAll(checkedWrappers)
                            }
                        },
                        onDelete = {
                            val tempList = courseWrappers.filter { it.tempId != wrapper.tempId }.map { it.course }
                            val tempIds = courseWrappers.filter { it.tempId != wrapper.tempId }.map { it.tempId }
                            val checked = checkConflictLocally(tempList, tempIds)
                            courseWrappers.clear()
                            courseWrappers.addAll(checked)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
fun ReviewCourseItem(
    wrapper: EditableCourseWrapper,
    totalWeeks: Int,
    onUpdate: (Course) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val course = wrapper.course

    var showWeekDialog by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    val hasZeroWeeks = course.weeks.isEmpty()
    val isError = wrapper.hasConflict || hasZeroWeeks

    val cardBgColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer
    val conflictTextColor = MaterialTheme.colorScheme.error
    val normalTextColor = MaterialTheme.colorScheme.onSurface

    val validWeeksCount = course.weeks.count { it <= totalWeeks }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize().border(
            width = if(isError) 1.dp else 0.dp,
            color = if(isError) MaterialTheme.colorScheme.error else Color.Transparent,
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(try { Color(android.graphics.Color.parseColor(course.color)) } catch(e:Exception){ Color.Gray })
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.name.ifBlank { "未知课程" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if(isError) conflictTextColor else normalTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "周${mapIntToChineseDay(course.day)} ${course.startPeriod}-${course.endPeriod}节 | ${course.teacher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isError) {
                        val errorMsg = if (wrapper.hasConflict) wrapper.conflictReason else "未设置上课周次"
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = conflictTextColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if(isExpanded) Icons.Default.ExpandLess else Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.outline)
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // 🔥 布局调整：课程名称独占一行
                OutlinedTextField(
                    value = course.name,
                    onValueChange = { onUpdate(course.copy(name = it)) },
                    label = { Text("课程名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 🔥 布局调整：教室和教师在同一行
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = course.classroom,
                        onValueChange = { onUpdate(course.copy(classroom = it)) },
                        label = { Text("教室") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = course.teacher,
                        onValueChange = { onUpdate(course.copy(teacher = it)) },
                        label = { Text("教师") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrettySelector(
                        icon = Icons.Outlined.AccessTime,
                        text = "周${mapIntToChineseDay(course.day)} ${course.startPeriod}-${course.endPeriod}节",
                        modifier = Modifier.weight(1f),
                        onClick = { showPeriodDialog = true }
                    )

                    PrettySelector(
                        icon = Icons.Outlined.DateRange,
                        text = "含${validWeeksCount}周",
                        modifier = Modifier.weight(0.7f),
                        onClick = { showWeekDialog = true }
                    )

                    Box(modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { showColorDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(try { Color(android.graphics.Color.parseColor(course.color)) } catch(e:Exception){ Color.Blue })
                        )
                    }
                }
            }
        }
    }

    // 弹窗逻辑

    // 1. 时间选择
    if (showPeriodDialog) {
        var selectedDay by remember { mutableIntStateOf(course.day) }
        var startP by remember { mutableFloatStateOf(course.startPeriod.toFloat()) }
        var endP by remember { mutableFloatStateOf(course.endPeriod.toFloat()) }

        AlertDialog(
            onDismissRequest = { showPeriodDialog = false },
            title = { Text("上课时间") },
            text = {
                Column {
                    Text("选择星期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (1..7).forEach { d ->
                            val isSelected = selectedDay == d
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedDay = d },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    mapIntToChineseDay(d),
                                    color = if(isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("选择节次 (${startP.toInt()} - ${endP.toInt()}节)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    RangeSlider(
                        value = startP..endP,
                        onValueChange = {
                            startP = it.start
                            endP = it.endInclusive
                        },
                        valueRange = 1f..18f, // 改为 1-18
                        steps = 17
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(course.copy(day = selectedDay, startPeriod = startP.toInt(), endPeriod = endP.toInt()))
                    showPeriodDialog = false
                }) { Text("完成") }
            }
        )
    }

    // 2. 周次选择
    if (showWeekDialog) {
        val tempWeeks = remember { course.weeks.toMutableStateList() }

        AlertDialog(
            onDismissRequest = { showWeekDialog = false },
            title = { Text("上课周次 (共${totalWeeks}周)") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewThemeButton(text = "全选", modifier = Modifier.weight(1f)) { tempWeeks.clear(); tempWeeks.addAll(1..totalWeeks) }
                        ReviewThemeButton(text = "单周", modifier = Modifier.weight(1f)) { tempWeeks.clear(); tempWeeks.addAll((1..totalWeeks).filter { it % 2 != 0 }) }
                        ReviewThemeButton(text = "双周", modifier = Modifier.weight(1f)) { tempWeeks.clear(); tempWeeks.addAll((1..totalWeeks).filter { it % 2 == 0 }) }
                        ReviewThemeButton(text = "清空", modifier = Modifier.weight(1f), isDestructive = true) { tempWeeks.clear() }
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rows = (totalWeeks + 4) / 5
                        items(rows) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (col in 0 until 5) {
                                    val w = row * 5 + col + 1
                                    if (w <= totalWeeks) {
                                        val isSelected = tempWeeks.contains(w)
                                        Box(
                                            modifier = Modifier
                                                .size(width = 52.dp, height = 40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { if (isSelected) tempWeeks.remove(w) else tempWeeks.add(w) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$w",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(52.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(course.copy(weeks = tempWeeks.sorted()))
                    showWeekDialog = false
                }) { Text("完成") }
            }
        )
    }

    if (showColorDialog) {
        // 同步 AddCourseScreen 的颜色
        val colors = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
            "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
            "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
            "#FF5722", "#F06292", "#BA68C8", "#4DD0E1", "#AED581"
        )
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("课程颜色") },
            text = {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    val rows = colors.chunked(5)
                    items(rows) { rowColors ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowColors.forEach { c ->
                                Box(modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(c)))
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .clickable { onUpdate(course.copy(color = c)); showColorDialog = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// 辅助组件 (ReviewThemeButton, PrettySelector, mapIntToChineseDay, checkConflictLocally)
@Composable
fun ReviewThemeButton(
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
                style = MaterialTheme.typography.labelMedium,
                color = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun PrettySelector(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(50.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
}

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

fun checkConflictLocally(courses: List<Course>, ids: List<String>): List<EditableCourseWrapper> {
    val res = mutableListOf<EditableCourseWrapper>()
    for (i in courses.indices) {
        var conflict = false
        var reason = ""
        val c1 = courses[i]
        for (j in courses.indices) {
            if (i == j) continue
            val c2 = courses[j]
            if (c1.day == c2.day) {
                val hasTimeOverlap = max(c1.startPeriod, c2.startPeriod) <= min(c1.endPeriod, c2.endPeriod)
                if (hasTimeOverlap) {
                    val weekIntersect = c1.weeks.intersect(c2.weeks.toSet())
                    if (weekIntersect.isNotEmpty()) {
                        conflict = true
                        reason = "与【${c2.name}】周${mapIntToChineseDay(c2.day)}冲突"
                        break
                    }
                }
            }
        }
        res.add(EditableCourseWrapper(tempId = ids[i], course = c1, hasConflict = conflict, conflictReason = reason))
    }
    return res
}