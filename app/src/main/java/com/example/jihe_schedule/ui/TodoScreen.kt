package com.example.jihe_schedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.jihe_schedule.data.CourseTime
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.model.Todo
import com.example.jihe_schedule.ui.theme.JiHeScheduleTheme
import com.example.jihe_schedule.viewmodel.ScheduleViewModel
import com.example.jihe_schedule.viewmodel.SettingsViewModel
import com.example.jihe_schedule.viewmodel.TodoViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val WEEK_DAYS = listOf("日", "一", "二", "三", "四", "五", "六")
private val BASE_SUNDAY = LocalDate.of(1999, 12, 26)
private val EPOCH_MONTH = YearMonth.of(2000, 1)
private const val INITIAL_PAGE_OFFSET = 50000
private val ROW_HEIGHT = 60.dp

// 🔥 用于统一列表排序的接口
sealed interface DisplayItem {
    val sortTime: LocalTime
}
data class CourseItemWrapper(val course: Course, val timeStr: String, val start: LocalTime, val end: LocalTime) : DisplayItem {
    override val sortTime: LocalTime = start
}
data class TodoItemWrapper(val todo: Todo, override val sortTime: LocalTime) : DisplayItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
    initialDate: LocalDate? = null,
    onNavigateToEdit: (Todo?, LocalDate?) -> Unit
) {
    val todoList by viewModel.allTodos.collectAsState()
    val allCourses by scheduleViewModel.currentCourses.collectAsState()
    val currentSchedule by scheduleViewModel.currentSchedule.collectAsState()
    val courseTimes by settingsViewModel.courseTimes.collectAsState()

    // 🔥 显示设置
    val showCourseInApp by settingsViewModel.showCourseInApp.collectAsState()
    val showTodoInApp by settingsViewModel.showTodoInApp.collectAsState()
    val separateMode by settingsViewModel.separateMode.collectAsState()

    val todoBgUri by settingsViewModel.todoBgImageUri.collectAsState()
    val todoBgOpacity by settingsViewModel.todoBgOpacity.collectAsState()
    val todoCalendarOpacity by settingsViewModel.todoCalendarOpacity.collectAsState()
    val todoCardOpacity by settingsViewModel.todoCardOpacity.collectAsState()
    val todoTransparentHeader by settingsViewModel.todoTransparentHeader.collectAsState()
    val todoForceDark by settingsViewModel.todoForceDark.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val themeColorHex by settingsViewModel.themeColor.collectAsState()

    val primaryColor = remember(themeColorHex) {
        try { Color(android.graphics.Color.parseColor(themeColorHex)) } catch (e: Exception) { Color(0xFF6650a4) }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initialDate ?: LocalDate.now()) }

    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            selectedDate = initialDate
        }
    }

    // 周次计算
    val weekOfSelectedDate = remember(currentSchedule, selectedDate) {
        val schedule = currentSchedule
        if (schedule != null) {
            try {
                val start = LocalDate.parse(schedule.termStartDate)
                val diff = ChronoUnit.DAYS.between(start, selectedDate)
                if (diff < 0) 0 else (diff / 7).toInt() + 1
            } catch (e: Exception) { 1 }
        } else { 1 }
    }

    // 筛选当天课程
    val todaysCourses = remember(allCourses, selectedDate, weekOfSelectedDate) {
        val dayOfWeek = selectedDate.dayOfWeek.value
        allCourses.filter { course ->
            course.day == dayOfWeek && course.weeks.contains(weekOfSelectedDate)
        }.sortedBy { it.startPeriod }
    }

    // 筛选当天待办
    val currentDayTodos = remember(todoList, selectedDate) {
        todoList.filter { todo -> checkTodoOnDate(todo, selectedDate) }.sortedBy { it.startTime }
    }

    // 🔥 混合模式列表计算
    val mixedList = remember(todaysCourses, currentDayTodos, courseTimes, showCourseInApp, showTodoInApp) {
        val list = mutableListOf<DisplayItem>()

        if (showCourseInApp) {
            todaysCourses.forEach { course ->
                val timeInfo = courseTimes.getOrNull(course.startPeriod - 1)
                val timeStr = timeInfo?.start ?: "00:00"
                val startTime = try { LocalTime.parse(timeInfo?.start ?: "00:00") } catch(e:Exception) { LocalTime.MIN }
                val endTime = try { LocalTime.parse(timeInfo?.end ?: "00:00") } catch(e:Exception) { LocalTime.MAX }
                list.add(CourseItemWrapper(course, timeStr, startTime, endTime))
            }
        }

        if (showTodoInApp) {
            currentDayTodos.forEach { todo ->
                val startTime = try { LocalTime.parse(todo.startTime) } catch(e:Exception) { LocalTime.MIN }
                list.add(TodoItemWrapper(todo, startTime))
            }
        }

        list.sortedBy { it.sortTime }
    }

    val isAppDark = when (themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val useDarkForTodo = if (todoBgUri != null && todoForceDark) true else isAppDark
    val solidSurfaceColor = if (useDarkForTodo) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    val customColorScheme = if (useDarkForTodo) darkColorScheme(primary = primaryColor, surface = if (todoBgUri != null) Color.Transparent else solidSurfaceColor) else lightColorScheme(primary = primaryColor, surface = if (todoBgUri != null) Color.Transparent else solidSurfaceColor)

    val today = remember { LocalDate.now() }
    var isMonthView by remember { mutableStateOf(false) }
    val currentMonth = YearMonth.from(selectedDate)
    val initialMonthPage = INITIAL_PAGE_OFFSET + ChronoUnit.MONTHS.between(EPOCH_MONTH, currentMonth).toInt()
    val monthPagerState = rememberPagerState(initialPage = initialMonthPage) { Int.MAX_VALUE }
    val initialWeekPage = INITIAL_PAGE_OFFSET + ChronoUnit.WEEKS.between(BASE_SUNDAY, selectedDate).toInt()
    val weekPagerState = rememberPagerState(initialPage = initialWeekPage) { Int.MAX_VALUE }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minHeightPx = with(density) { ROW_HEIGHT.toPx() }
    val maxHeightPx = with(density) { (ROW_HEIGHT * 6).toPx() }
    val heightAnim = remember { Animatable(if (isMonthView) maxHeightPx else minHeightPx) }
    val draggableState = rememberDraggableState { delta -> scope.launch { heightAnim.snapTo((heightAnim.value + delta).coerceIn(minHeightPx, maxHeightPx)) } }

    LaunchedEffect(isMonthView) { heightAnim.animateTo(if (isMonthView) maxHeightPx else minHeightPx, tween(300)) }

    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.isScrollInProgress }.filter { !it }.collect {
            if (!isMonthView) {
                val weekDiff = weekPagerState.currentPage - INITIAL_PAGE_OFFSET
                val newWeekStart = BASE_SUNDAY.plusWeeks(weekDiff.toLong())
                val currentWeekStart = BASE_SUNDAY.plusWeeks(ChronoUnit.WEEKS.between(BASE_SUNDAY, selectedDate))
                val newDate = newWeekStart.plusDays(ChronoUnit.DAYS.between(currentWeekStart, selectedDate))
                if (newDate != selectedDate) { selectedDate = newDate; val monthDiff = ChronoUnit.MONTHS.between(EPOCH_MONTH, YearMonth.from(newDate)).toInt(); if (monthPagerState.currentPage != INITIAL_PAGE_OFFSET + monthDiff) monthPagerState.scrollToPage(INITIAL_PAGE_OFFSET + monthDiff) }
            }
        }
    }
    LaunchedEffect(monthPagerState) {
        snapshotFlow { monthPagerState.isScrollInProgress }.filter { !it }.collect {
            if (isMonthView) {
                val pageDiff = monthPagerState.currentPage - INITIAL_PAGE_OFFSET
                val targetMonth = EPOCH_MONTH.plusMonths(pageDiff.toLong())
                if (targetMonth != YearMonth.from(selectedDate)) {
                    val newDate = targetMonth.atDay(selectedDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
                    selectedDate = newDate; val weekDiff = ChronoUnit.WEEKS.between(BASE_SUNDAY, newDate).toInt(); if (weekPagerState.currentPage != INITIAL_PAGE_OFFSET + weekDiff) weekPagerState.scrollToPage(INITIAL_PAGE_OFFSET + weekDiff)
                }
            }
        }
    }
    LaunchedEffect(selectedDate) {
        if (!monthPagerState.isScrollInProgress && !weekPagerState.isScrollInProgress) {
            val monthDiff = ChronoUnit.MONTHS.between(EPOCH_MONTH, YearMonth.from(selectedDate)).toInt()
            val weekDiff = ChronoUnit.WEEKS.between(BASE_SUNDAY, selectedDate).toInt()
            if (monthPagerState.currentPage != INITIAL_PAGE_OFFSET + monthDiff) monthPagerState.scrollToPage(INITIAL_PAGE_OFFSET + monthDiff)
            if (weekPagerState.currentPage != INITIAL_PAGE_OFFSET + weekDiff) weekPagerState.scrollToPage(INITIAL_PAGE_OFFSET + weekDiff)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (todoBgUri != null) AsyncImage(model = todoBgUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = todoBgOpacity) else Spacer(modifier = Modifier.fillMaxSize().background(solidSurfaceColor))

        JiHeScheduleTheme(darkTheme = useDarkForTodo, colorSchemeOverride = customColorScheme) {
            val containerColor = if (todoBgUri != null) Color.Transparent else MaterialTheme.colorScheme.background

            Scaffold(
                containerColor = containerColor,
                floatingActionButton = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedVisibility(visible = selectedDate != today, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            SmallFloatingActionButton(onClick = { selectedDate = today }, containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = primaryColor, modifier = Modifier.padding(bottom = 16.dp)) { Text("今", fontWeight = FontWeight.Bold) }
                        }
                        FloatingActionButton(onClick = { onNavigateToEdit(null, selectedDate) }, containerColor = primaryColor) { Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White) }
                    }
                }
            ) { innerPadding ->
                Column(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()).fillMaxSize()) {
                    val headerBgColor = if (todoBgUri != null && todoTransparentHeader) Color.Transparent else if (todoBgUri != null) solidSurfaceColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface

                    Box(modifier = Modifier.fillMaxWidth().background(headerBgColor)) {
                        Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.Bottom) {
                                Text(text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy / MM")), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.clickable { showDatePicker = true })
                                Spacer(modifier = Modifier.width(12.dp)); Text("第${selectedDate.get(WeekFields.of(Locale.getDefault()).weekOfMonth())}周", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                val daysDiff = ChronoUnit.DAYS.between(today, selectedDate)
                                if (daysDiff != 0L) Text(if (daysDiff > 0) "${daysDiff}天后" else "${Math.abs(daysDiff)}天前", fontSize = 14.sp, color = primaryColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) { WEEK_DAYS.forEach { day -> Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                    }

                    val progress = (heightAnim.value - minHeightPx) / (maxHeightPx - minHeightPx)
                    val currentMonthPage = EPOCH_MONTH.plusMonths((monthPagerState.currentPage - INITIAL_PAGE_OFFSET).toLong())
                    val offsetRows = calculateSelectedRowIndex(currentMonthPage, selectedDate)
                    val monthOffsetY = -offsetRows * minHeightPx * (1 - progress)
                    val calendarBgColor = if (todoBgUri != null) MaterialTheme.colorScheme.surface.copy(alpha = todoCalendarOpacity) else MaterialTheme.colorScheme.surface

                    Box(modifier = Modifier.fillMaxWidth().height(with(density) { heightAnim.value.toDp() }).clipToBounds().background(calendarBgColor).draggable(state = draggableState, orientation = Orientation.Vertical, onDragStopped = { velocity -> val target = if (velocity > 500 || heightAnim.value > (maxHeightPx + minHeightPx) / 2) maxHeightPx else minHeightPx; scope.launch { heightAnim.animateTo(target, tween(300)); isMonthView = target == maxHeightPx } })) {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationY = monthOffsetY; alpha = progress }.zIndex(1f)) { HorizontalPager(state = monthPagerState, modifier = Modifier.requiredHeight(with(density) { maxHeightPx.toDp() }), verticalAlignment = Alignment.Top) { page -> CalendarMonthPage(EPOCH_MONTH.plusMonths((page - INITIAL_PAGE_OFFSET).toLong()), selectedDate, primaryColor, todoList) { selectedDate = it } } }
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1 - progress }.zIndex(if (progress < 0.5f) 2f else 0f)) { HorizontalPager(state = weekPagerState, modifier = Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.Top) { page -> CalendarWeekPage(BASE_SUNDAY.plusWeeks((page - INITIAL_PAGE_OFFSET).toLong()), selectedDate, primaryColor, todoList) { selectedDate = it } } }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(calendarBgColor).draggable(state = draggableState, orientation = Orientation.Vertical, onDragStopped = { velocity -> val target = if (velocity > 500 || heightAnim.value > (maxHeightPx + minHeightPx) / 2) maxHeightPx else minHeightPx; scope.launch { heightAnim.animateTo(target, tween(300)); isMonthView = target == maxHeightPx } }).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { isMonthView = !isMonthView }, contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    // 🔥🔥 列表核心显示逻辑
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp, start = 20.dp, end = 20.dp)) {
                        item { Text(text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 10.dp)) }

                        if (mixedList.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if(todoBgUri!=null) todoCardOpacity else 1f)).clickable { onNavigateToEdit(null, selectedDate) }, contentAlignment = Alignment.CenterStart) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                                        Text("${selectedDate.dayOfMonth}", fontSize = 24.sp, color = primaryColor, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(4.dp))
                                        Text("周${WEEK_DAYS[selectedDate.dayOfWeek.value % 7]}", fontSize = 12.sp, color = primaryColor, modifier = Modifier.padding(top = 8.dp)); Spacer(modifier = Modifier.width(16.dp))
                                        Text("无安排，点击 + 创建", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                }
                            }
                        } else {
                            // 🔥 模式 A：分离模式
                            if (separateMode && showCourseInApp && showTodoInApp) {
                                // 1. 课程
                                if (todaysCourses.isNotEmpty()) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryColor)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("今日课程 (第${weekOfSelectedDate}周)", style = MaterialTheme.typography.labelMedium, color = primaryColor)
                                        }
                                    }
                                    items(todaysCourses) { course ->
                                        val timeInfo = courseTimes.getOrNull(course.startPeriod - 1)
                                        val startTime = try { LocalTime.parse(timeInfo?.start ?: "00:00") } catch(e:Exception) { LocalTime.MIN }
                                        val endTime = try { LocalTime.parse(timeInfo?.end ?: "00:00") } catch(e:Exception) { LocalTime.MAX }

                                        CourseTimelineItem(
                                            course = course,
                                            timeStr = timeInfo?.start ?: "",
                                            start = startTime,
                                            end = endTime,
                                            selectedDate = selectedDate,
                                            primaryColor = primaryColor,
                                            cardOpacity = if(todoBgUri != null) todoCardOpacity else 1f
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f)) }
                                }

                                // 2. 待办
                                if (currentDayTodos.isNotEmpty()) {
                                    item { Text("待办事项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
                                    itemsIndexed(currentDayTodos) { index, todo ->
                                        TimelineItem(
                                            todo = todo, isFirst = index == 0, isLast = index == currentDayTodos.lastIndex, primaryColor = primaryColor, isTransparent = todoBgUri != null, cardOpacity = if(todoBgUri != null) todoCardOpacity else 1f, onCheckedChange = { viewModel.toggleComplete(todo) }, onClick = { onNavigateToEdit(todo, selectedDate) }
                                        )
                                    }
                                }
                            }
                            // 🔥 模式 B：混合模式 (或单显示模式)
                            else {
                                itemsIndexed(mixedList) { index, item ->
                                    when (item) {
                                        is CourseItemWrapper -> {
                                            CourseTimelineItem(
                                                course = item.course,
                                                timeStr = item.timeStr,
                                                start = item.start,
                                                end = item.end,
                                                selectedDate = selectedDate,
                                                primaryColor = primaryColor,
                                                cardOpacity = if(todoBgUri != null) todoCardOpacity else 1f
                                            )
                                        }
                                        is TodoItemWrapper -> {
                                            TimelineItem(
                                                todo = item.todo,
                                                isFirst = index == 0 && !separateMode,
                                                isLast = index == mixedList.lastIndex && !separateMode,
                                                primaryColor = primaryColor,
                                                isTransparent = todoBgUri != null,
                                                cardOpacity = if(todoBgUri != null) todoCardOpacity else 1f,
                                                onCheckedChange = { viewModel.toggleComplete(item.todo) },
                                                onClick = { onNavigateToEdit(item.todo, selectedDate) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }; showDatePicker = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }
}

// 🔥 核心修改：课程 Item 支持状态显示 (已结束、正在上课)
// 🔥 核心修改：课程 Item 样式统一为 Todo 卡片样式
@Composable
fun CourseTimelineItem(
    course: Course,
    timeStr: String,
    start: LocalTime,
    end: LocalTime,
    selectedDate: LocalDate,
    primaryColor: Color,
    cardOpacity: Float
) {
    val now = remember { LocalTime.now() }
    val today = remember { LocalDate.now() }

    // 判断状态
    val isToday = selectedDate == today
    val isEnded = isToday && now.isAfter(end)
    val isOngoing = isToday && now.isAfter(start) && now.isBefore(end)

    // 颜色逻辑：已结束显示灰色，否则显示课程颜色（若解析失败则用主色）
    val displayColor = remember(course.color, isEnded) {
        if (isEnded) Color.Gray
        else try { Color(android.graphics.Color.parseColor(course.color)) } catch (e: Exception) { primaryColor }
    }

    val textColor = if (isEnded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f) else MaterialTheme.colorScheme.onSurface

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 1. 左侧时间列 (保持不变)
        Column(modifier = Modifier.width(50.dp).padding(top = 16.dp), horizontalAlignment = Alignment.End) {
            Text(timeStr, fontWeight = FontWeight.Bold, color = textColor, textDecoration = if (isEnded) TextDecoration.LineThrough else null)
            Text("第${course.startPeriod}节", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 2. 中间时间轴线 (保持不变)
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            // 课程依旧使用实心圆点，区别于 Todo 的空心/实心逻辑，但在视觉上大小位置对齐
            Box(modifier = Modifier.padding(top = 20.dp).size(12.dp).clip(CircleShape).background(if(isOngoing) primaryColor else displayColor).zIndex(1f))
        }

        // 3. 右侧卡片 (核心修改区域)
        Card(
            modifier = Modifier.weight(1f).padding(bottom = 12.dp, top = 4.dp),
            colors = CardDefaults.cardColors(
                // 修改点：非正在上课时，背景色改为 surfaceVariant (与 Todo 一致)，不再是淡色背景
                containerColor = if (isOngoing) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = cardOpacity)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isOngoing) {
                    // --- 正在上课 (保持高亮样式，因为这是重要状态) ---
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("正在上课", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.background(Color.White.copy(alpha=0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("@${course.classroom}  |  ${course.teacher}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha=0.9f))
                    }
                } else {
                    // --- 普通状态 (修改为 Todo 样式) ---
                    // 1. 左侧颜色条 (与 Todo 保持一致)
                    Box(modifier = Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(displayColor))

                    Spacer(modifier = Modifier.width(12.dp))

                    // 2. 文本内容
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textDecoration = if(isEnded) TextDecoration.LineThrough else null
                        )
                        // 课程地点和老师作为副标题，类似于 Todo 的描述
                        Text(
                            text = "@${course.classroom}  |  ${course.teacher}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// 辅助函数 (完整展开)
fun checkTodoOnDate(todo: Todo, targetDate: LocalDate): Boolean {
    val start = LocalDate.parse(todo.date)
    if (targetDate.isBefore(start)) return false

    if (todo.excludedDates.isNotEmpty()) {
        val excludedList = todo.excludedDates.split(",")
        val targetStr = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (excludedList.contains(targetStr)) return false
    }

    if (todo.repeatEndType == "date" && todo.repeatEndDate != null) {
        val endDate = LocalDate.parse(todo.repeatEndDate)
        if (targetDate.isAfter(endDate)) return false
    }

    val type = todo.repeatType.takeIf { it != "none" } ?: if (todo.isYearly) "yearly" else "none"
    val isMatch = when (type) {
        "none" -> targetDate == start
        "daily" -> true
        "weekly" -> ChronoUnit.DAYS.between(start, targetDate) % 7 == 0L
        "monthly" -> {
            val startDay = start.dayOfMonth
            val targetDay = targetDate.dayOfMonth
            if (startDay == targetDay) true
            else {
                val targetMonthDays = targetDate.lengthOfMonth()
                startDay > targetMonthDays && targetDay == targetMonthDays
            }
        }
        "yearly" -> {
            val startMonth = start.monthValue
            val startDay = start.dayOfMonth
            val targetMonth = targetDate.monthValue
            val targetDay = targetDate.dayOfMonth
            if (startMonth == targetMonth && startDay == targetDay) true
            else {
                startMonth == 2 && startDay == 29 && !targetDate.isLeapYear && targetMonth == 2 && targetDay == 28
            }
        }
        else -> false
    }

    if (!isMatch) return false

    if (todo.repeatEndType == "count" && todo.repeatCount != null) {
        val count = todo.repeatCount
        val occurrenceIndex = when (type) {
            "daily" -> ChronoUnit.DAYS.between(start, targetDate)
            "weekly" -> ChronoUnit.WEEKS.between(start, targetDate)
            "monthly" -> ChronoUnit.MONTHS.between(start, targetDate)
            "yearly" -> ChronoUnit.YEARS.between(start, targetDate)
            else -> 0L
        }
        if (occurrenceIndex >= count) return false
    }
    return true
}

fun calculateSelectedRowIndex(month: YearMonth, selectedDate: LocalDate): Int {
    if (YearMonth.from(selectedDate) != month) return 0
    val firstDay = month.atDay(1)
    val firstDayOfWeek = firstDay.dayOfWeek.value % 7
    return (selectedDate.dayOfMonth + firstDayOfWeek - 1) / 7
}

@Composable
fun CalendarMonthPage(yearMonth: YearMonth, selectedDate: LocalDate, primaryColor: Color, todoList: List<Todo>, onDateSelected: (LocalDate) -> Unit) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7
    val days = remember(yearMonth) {
        val list = mutableListOf<LocalDate>()
        for (i in firstDayOfWeek downTo 1) list.add(yearMonth.atDay(1).minusDays(i.toLong()))
        for (i in 1..daysInMonth) list.add(yearMonth.atDay(i))
        val remaining = 42 - list.size
        val lastDay = yearMonth.atEndOfMonth()
        for (i in 1..remaining) list.add(lastDay.plusDays(i.toLong()))
        list
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f)))
        Column(modifier = Modifier.weight(1f)) {
            days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                    week.forEach { date -> DayCell(date, selectedDate, YearMonth.from(date) == yearMonth, primaryColor, todoList, onDateSelected) }
                }
            }
        }
        Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f)))
    }
}

@Composable
fun CalendarWeekPage(weekStart: LocalDate, selectedDate: LocalDate, primaryColor: Color, todoList: List<Todo>, onDateSelected: (LocalDate) -> Unit) {
    val weekDays = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        weekDays.forEach { date -> DayCell(date, selectedDate, true, primaryColor, todoList, onDateSelected) }
    }
}

@Composable
fun RowScope.DayCell(date: LocalDate, selectedDate: LocalDate, isCurrentMonth: Boolean, primaryColor: Color, todoList: List<Todo>, onDateSelected: (LocalDate) -> Unit) {
    val isSelected = date == selectedDate
    val isToday = date == LocalDate.now()
    val hasTodo = remember(todoList, date) { todoList.any { checkTodoOnDate(it, date) && !it.completed } }

    Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable { onDateSelected(date) }
                .background(if (isSelected) primaryColor else Color.Transparent)
                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, primaryColor, RoundedCornerShape(14.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = date.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal, color = when { isSelected -> Color.White; isToday -> primaryColor; !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f); else -> MaterialTheme.colorScheme.onSurface })
                Text(text = if (isToday) "今天" else "日程", fontSize = 10.sp, color = if (isSelected) Color.White.copy(0.8f) else if (isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        if (hasTodo) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)) else Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun TimelineItem(
    todo: Todo,
    isFirst: Boolean,
    isLast: Boolean,
    primaryColor: Color,
    isTransparent: Boolean,
    cardOpacity: Float,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val displayColor = remember(todo.color) { try { if (!todo.color.isNullOrEmpty()) Color(android.graphics.Color.parseColor(todo.color)) else primaryColor } catch (e: Exception) { primaryColor } }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(modifier = Modifier.width(50.dp).padding(top = 16.dp), horizontalAlignment = Alignment.End) {
            Text(todo.startTime, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.TopCenter) {
            if (!isFirst) Box(modifier = Modifier.width(2.dp).height(26.dp).align(Alignment.TopCenter).background(MaterialTheme.colorScheme.outlineVariant))
            if (!isLast) Box(modifier = Modifier.width(2.dp).fillMaxHeight().padding(top = 26.dp).align(Alignment.TopCenter).background(MaterialTheme.colorScheme.outlineVariant))
            Box(modifier = Modifier.padding(top = 20.dp).size(12.dp).clip(CircleShape).border(2.dp, if (todo.completed) MaterialTheme.colorScheme.outline else displayColor, CircleShape).background(if (todo.completed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.background).zIndex(1f))
        }
        Card(
            modifier = Modifier.weight(1f).padding(bottom = 16.dp, top = 4.dp).clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = cardOpacity)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(if(todo.completed) Color.Gray else displayColor))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(todo.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textDecoration = if (todo.completed) TextDecoration.LineThrough else null, color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (!todo.description.isNullOrEmpty()) Text(todo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (todo.tag != null && todo.tag != "默认") Text("#${todo.tag}", style = MaterialTheme.typography.labelSmall, color = displayColor)
                }
                Checkbox(checked = todo.completed, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = displayColor))
            }
        }
    }
}