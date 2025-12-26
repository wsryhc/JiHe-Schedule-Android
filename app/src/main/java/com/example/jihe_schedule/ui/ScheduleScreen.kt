package com.example.jihe_schedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.ui.theme.JiHeScheduleTheme
import com.example.jihe_schedule.viewmodel.ScheduleViewModel
import com.example.jihe_schedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val PERIOD_HEIGHT = 65.dp
val BREAK_HEIGHT = 40.dp
val HEADER_HEIGHT = 50.dp
val SIDEBAR_WIDTH = 45.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = viewModel(),
    settingsViewModel: SettingsViewModel,
    onManageCourse: (Int, Int, Course?) -> Unit,
    onNavigateToScheduleManager: () -> Unit // 🔥 新增参数：跳转到课表管理
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val currentSchedule by viewModel.currentSchedule.collectAsState()

    // 🔥 如果没有课表，直接显示空状态页
    if (currentSchedule == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "当前没有课表",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "创建一个课表以开始使用",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateToScheduleManager,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("立即创建")
                }
            }
        }
        return // 结束渲染，不显示后面的课表UI
    }

    // --- 以下是正常的课表显示逻辑 ---

    val showSat by settingsViewModel.showSaturday.collectAsState()
    val showSun by settingsViewModel.showSunday.collectAsState()

    val pMorning by settingsViewModel.periodMorning.collectAsState()
    val pAfternoon by settingsViewModel.periodAfternoon.collectAsState()
    val pEvening by settingsViewModel.periodEvening.collectAsState()

    val bgUri by settingsViewModel.bgImageUri.collectAsState()
    val bgOpacity by settingsViewModel.bgOpacity.collectAsState()
    val borderOpacity by settingsViewModel.borderOpacity.collectAsState()
    val courseOpacity by settingsViewModel.courseOpacity.collectAsState()

    val scheduleTransparentHeader by settingsViewModel.scheduleTransparentHeader.collectAsState()
    val scheduleForceDark by settingsViewModel.scheduleForceDark.collectAsState()

    val themeMode by settingsViewModel.themeMode.collectAsState()
    val themeColorHex by settingsViewModel.themeColor.collectAsState()

    val currentWeek by viewModel.selectedWeek.collectAsState()
    val courseList by viewModel.currentCourses.collectAsState()
    val courseTimes by settingsViewModel.courseTimes.collectAsState()

    var selectedSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showWeekDialog by remember { mutableStateOf(false) }

    val totalWeeks = currentSchedule!!.totalWeeks // 此时已确认不为 null
    val initialPage = (currentWeek - 1).coerceIn(0, maxOf(0, totalWeeks - 1))
    val pagerState = rememberPagerState(initialPage = initialPage) { totalWeeks }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .filter { !it }
            .collect {
                val newWeek = pagerState.currentPage + 1
                if (newWeek != currentWeek) {
                    viewModel.updateSelectedWeek(newWeek)
                }
            }
    }

    LaunchedEffect(currentWeek) {
        if (!pagerState.isScrollInProgress && (currentWeek - 1) != pagerState.currentPage) {
            pagerState.animateScrollToPage((currentWeek - 1).coerceIn(0, totalWeeks - 1))
        }
    }

    val weekDaysMap = remember(showSat, showSun) {
        val list = mutableListOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五")
        if (showSat) list.add(6 to "周六")
        if (showSun) list.add(7 to "周日")
        list
    }

    val daysToShow = weekDaysMap.size
    val dayWidth = (screenWidth - SIDEBAR_WIDTH) / daysToShow.coerceAtLeast(1)

    val realCurrentWeek = remember(currentSchedule) {
        val start = LocalDate.parse(currentSchedule!!.termStartDate)
        val now = LocalDate.now()
        val diff = ChronoUnit.DAYS.between(start, now)
        (diff / 7).toInt() + 1
    }

    val maxPeriods = pMorning + pAfternoon + pEvening
    val periodOffsets = remember(pMorning, pAfternoon, pEvening) {
        val offsets = mutableMapOf<Int, androidx.compose.ui.unit.Dp>()
        var currentY = 0.dp
        for (i in 1..maxPeriods) {
            offsets[i] = currentY
            currentY += PERIOD_HEIGHT
            if (i == pMorning) currentY += BREAK_HEIGHT
            if (i == pMorning + pAfternoon) currentY += BREAK_HEIGHT
        }
        offsets[maxPeriods + 1] = currentY
        offsets
    }
    val totalHeight = periodOffsets[maxPeriods + 1] ?: (PERIOD_HEIGHT * maxPeriods)

    Box(modifier = Modifier.fillMaxSize()) {
        if (bgUri != null) {
            AsyncImage(
                model = bgUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = bgOpacity
            )
        }

        val isAppDark = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
        val useDarkForSchedule = if (bgUri != null && scheduleForceDark) true else isAppDark

        val primaryColor = remember(themeColorHex) {
            try { Color(android.graphics.Color.parseColor(themeColorHex)) } catch (e: Exception) { Color(0xFF6650a4) }
        }
        val solidSurfaceColor = if (useDarkForSchedule) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)

        val customColorScheme = if (useDarkForSchedule) {
            darkColorScheme(primary = primaryColor, onPrimary = Color.White, secondary = primaryColor, tertiary = primaryColor, primaryContainer = primaryColor.copy(alpha = 0.3f), secondaryContainer = primaryColor.copy(alpha = 0.3f), surface = if (bgUri != null) Color.Transparent else solidSurfaceColor)
        } else {
            lightColorScheme(primary = primaryColor, onPrimary = Color.White, secondary = primaryColor, tertiary = primaryColor, primaryContainer = primaryColor.copy(alpha = 0.3f), secondaryContainer = primaryColor.copy(alpha = 0.3f), surface = if (bgUri != null) Color.Transparent else solidSurfaceColor)
        }

        JiHeScheduleTheme(darkTheme = useDarkForSchedule, colorSchemeOverride = customColorScheme) {
            val containerColor = if (bgUri != null) Color.Transparent else MaterialTheme.colorScheme.background

            Scaffold(containerColor = containerColor) { innerPadding ->
                Column(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()).fillMaxSize()) {

                    val headerBgColor = if (bgUri != null && scheduleTransparentHeader) Color.Transparent else if (bgUri != null) solidSurfaceColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface

                    CenterAlignedTopAppBar(
                        title = { Text(currentSchedule?.name ?: "课程表", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = headerBgColor, titleContentColor = MaterialTheme.colorScheme.onSurface)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                            Text(text = todayStr, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                            val viewingWeek = pagerState.currentPage + 1
                            val isCurrent = viewingWeek == realCurrentWeek
                            val statusText = if (isCurrent) "本周" else "非本周 (第${realCurrentWeek}周)"
                            val statusColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(statusColor))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor, fontWeight = FontWeight.Medium)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一周", tint = MaterialTheme.colorScheme.onSurface) }
                            TextButton(onClick = { showWeekDialog = true }) { Text("第 ${pagerState.currentPage + 1} 周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(totalWeeks - 1)) } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一周", tint = MaterialTheme.colorScheme.onSurface) }
                        }
                    }

                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        val weekNum = page + 1
                        val activeCourses = courseList.filter { it.weeks.contains(weekNum) }
                        val ghostCourses = courseList.filter { !it.weeks.contains(weekNum) }.filter { ghost ->
                            val isBlocked = activeCourses.any { active -> active.day == ghost.day && active.startPeriod <= ghost.endPeriod && active.endPeriod >= ghost.startPeriod }
                            !isBlocked
                        }.distinctBy { it.day to it.startPeriod }

                        val termStart = currentSchedule?.let { LocalDate.parse(it.termStartDate) } ?: LocalDate.now()
                        val weekMonday = termStart.plusDays(((weekNum - 1) * 7).toLong())
                        val todayDate = LocalDate.now()

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).background(MaterialTheme.colorScheme.surface.copy(alpha = borderOpacity))) {
                                Spacer(modifier = Modifier.width(SIDEBAR_WIDTH))
                                weekDaysMap.forEachIndexed { _, (dayVal, dayName) ->
                                    val date = weekMonday.plusDays((dayVal - 1).toLong())
                                    val dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd"))
                                    val isToday = date.isEqual(todayDate)
                                    Column(
                                        modifier = Modifier.width(dayWidth).fillMaxHeight().background(Color.Transparent),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = dayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        Text(text = dateStr, fontSize = 10.sp, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Column(modifier = Modifier.width(SIDEBAR_WIDTH).height(totalHeight)) {
                                    for (i in 1..maxPeriods) {
                                        val timeInfo = courseTimes.getOrNull(i - 1)
                                        val startStr = timeInfo?.start ?: "--:--"
                                        val endStr = timeInfo?.end ?: "--:--"

                                        Box(modifier = Modifier.height(PERIOD_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text(text = i.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text(text = startStr, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 10.sp)
                                                Text(text = endStr, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 10.sp)
                                            }
                                        }
                                        if (i == pMorning) Box(modifier = Modifier.height(BREAK_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("午休", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                                        if (i == pMorning + pAfternoon) Box(modifier = Modifier.height(BREAK_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("晚休", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                                    }
                                }

                                Box(
                                    modifier = Modifier.fillMaxWidth().height(totalHeight)
                                        .pointerInput(Unit) {
                                            detectTapGestures { offset ->
                                                val col = (offset.x / dayWidth.toPx()).toInt()
                                                if (col in weekDaysMap.indices) {
                                                    val clickedDayIndex = weekDaysMap[col].first
                                                    var clickedPeriod = -1
                                                    val y = offset.y / density.density
                                                    for ((p, topDp) in periodOffsets) {
                                                        if (p > maxPeriods) break
                                                        if (y >= topDp.value && y < (topDp + PERIOD_HEIGHT).value) {
                                                            clickedPeriod = p
                                                            break
                                                        }
                                                    }
                                                    if (clickedPeriod != -1) selectedSlot = clickedDayIndex to clickedPeriod
                                                }
                                            }
                                        }
                                ) {
                                    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = borderOpacity)

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val colWidthPx = dayWidth.toPx()

                                        val break1Top = periodOffsets[pMorning]!!.toPx() + PERIOD_HEIGHT.toPx()
                                        val break1Bottom = break1Top + BREAK_HEIGHT.toPx()

                                        val break2Top = periodOffsets[pMorning + pAfternoon]!!.toPx() + PERIOD_HEIGHT.toPx()
                                        val break2Bottom = break2Top + BREAK_HEIGHT.toPx()

                                        for (i in 1..daysToShow) {
                                            val x = i * colWidthPx
                                            drawLine(lineColor, Offset(x, 0f), Offset(x, break1Top), 1.dp.toPx())
                                            drawLine(lineColor, Offset(x, break1Bottom), Offset(x, break2Top), 1.dp.toPx())
                                            drawLine(lineColor, Offset(x, break2Bottom), Offset(x, size.height), 1.dp.toPx())
                                        }

                                        for (dayIdx in 0 until daysToShow) {
                                            val dayReal = weekDaysMap[dayIdx].first
                                            val leftX = dayIdx * colWidthPx
                                            val rightX = (dayIdx + 1) * colWidthPx

                                            for (i in 1..maxPeriods) {
                                                val topPx = periodOffsets[i]!!.toPx()
                                                val bottomPx = topPx + PERIOD_HEIGHT.toPx()

                                                val isBlockedTop = activeCourses.any { it.day == dayReal && it.startPeriod < i && it.endPeriod >= i } ||
                                                        ghostCourses.any { it.day == dayReal && it.startPeriod < i && it.endPeriod >= i }
                                                if (!isBlockedTop) drawLine(lineColor, Offset(leftX, topPx), Offset(rightX, topPx), 1.dp.toPx())

                                                val isBlockedBottom = activeCourses.any { it.day == dayReal && it.startPeriod <= i && it.endPeriod > i } ||
                                                        ghostCourses.any { it.day == dayReal && it.startPeriod <= i && it.endPeriod > i }
                                                if (!isBlockedBottom) drawLine(lineColor, Offset(leftX, bottomPx), Offset(rightX, bottomPx), 1.dp.toPx())
                                            }
                                        }
                                    }

                                    @Composable
                                    fun CourseCard(course: Course, isGhost: Boolean) {
                                        val colIndex = weekDaysMap.indexOfFirst { it.first == course.day }
                                        if (colIndex != -1) {
                                            val topOffset = periodOffsets[course.startPeriod] ?: return
                                            if (course.startPeriod > maxPeriods) return

                                            var height = PERIOD_HEIGHT
                                            if (course.endPeriod > course.startPeriod) {
                                                val endTop = periodOffsets[course.endPeriod]
                                                if (endTop != null) {
                                                    height = (endTop - topOffset) + PERIOD_HEIGHT
                                                }
                                            }
                                            val leftOffset = dayWidth * colIndex
                                            val cardColor = if (isGhost) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else (try { Color(android.graphics.Color.parseColor(course.color)) } catch (_: Exception) { MaterialTheme.colorScheme.primaryContainer }).copy(alpha = courseOpacity)
                                            val textColor = if (isGhost) MaterialTheme.colorScheme.onSurfaceVariant else Color.White

                                            Card(
                                                modifier = Modifier.padding(1.dp).width(dayWidth - 2.dp).height(height - 2.dp).offset(x = leftOffset, y = topOffset),
                                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(2.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(if (isGhost) "${course.name}" else course.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = textColor, maxLines = 3)
                                                    if (course.classroom.isNotEmpty()) {
                                                        Text("@${course.classroom}", fontSize = 10.sp, color = textColor.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    ghostCourses.forEach { CourseCard(it, isGhost = true) }
                                    activeCourses.forEach { CourseCard(it, isGhost = false) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // ... 底部 Dialog 代码
    if (selectedSlot != null) {
        val (dayIndex, period) = selectedSlot!!
        val weekDaysAll = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val viewingWeek = pagerState.currentPage + 1

        val allCoursesInSlot = courseList.filter {
            it.day == dayIndex &&
                    period >= it.startPeriod &&
                    period <= it.endPeriod
        }.sortedBy {
            if (it.weeks.contains(viewingWeek)) 0 else 1
        }

        Dialog(onDismissRequest = { selectedSlot = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${weekDaysAll.getOrElse(dayIndex - 1) { "" }} - 第${period}节",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedSlot = null }) {
                            Icon(Icons.Default.Add, contentDescription = "Close", modifier = Modifier.rotate(45f), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (allCoursesInSlot.isEmpty()) {
                        Text(
                            "此处没有任何周有课程",
                            modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(allCoursesInSlot) { course ->
                                val isCurrentWeek = course.weeks.contains(viewingWeek)
                                val cardBg = if (isCurrentWeek) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                val titleColor = if (isCurrentWeek) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedSlot = null; onManageCourse(dayIndex, period, course) }
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.width(4.dp).height(30.dp).background(try { Color(android.graphics.Color.parseColor(course.color)) } catch (_:Exception) { Color.Blue }))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (!isCurrentWeek) {
                                                    Text("(非本周) ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                                }
                                                Text(course.name, fontWeight = FontWeight.Bold, color = titleColor)
                                            }

                                            val weeksText = if (course.weeks.isNotEmpty()) {
                                                val start = course.weeks.minOrNull()
                                                val end = course.weeks.maxOrNull()
                                                "第$start-${end}周"
                                            } else ""

                                            Text("${course.classroom} | ${course.teacher} | $weeksText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedSlot = null; onManageCourse(dayIndex, period, null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加课程")
                    }
                }
            }
        }
    }

    if (showWeekDialog) {
        val totalWeeks = currentSchedule!!.totalWeeks
        Dialog(onDismissRequest = { showWeekDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(400.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    Text("跳转到指定周", modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    HorizontalDivider()
                    LazyColumn {
                        items(totalWeeks) { i ->
                            val weekNum = i + 1
                            val isCurrent = weekNum == realCurrentWeek
                            val isSelected = weekNum == (pagerState.currentPage + 1)
                            ListItem(
                                headlineContent = {
                                    Row {
                                        Text("第 $weekNum 周", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        if (isCurrent) { Spacer(modifier = Modifier.width(8.dp)); Text("(本周)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically)) }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    scope.launch { pagerState.animateScrollToPage(i) }
                                    showWeekDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}