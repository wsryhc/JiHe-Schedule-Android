package com.example.jihe_schedule.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.jihe_schedule.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToScheduleManager: () -> Unit,
    onNavigateToCourseTimeManager: () -> Unit,
    onNavigateToTodoManager: () -> Unit,
    onNavigateToNotificationManager: () -> Unit
) {
    val showSat by viewModel.showSaturday.collectAsState()
    val showSun by viewModel.showSunday.collectAsState()

    val pMorning by viewModel.periodMorning.collectAsState()
    val pAfternoon by viewModel.periodAfternoon.collectAsState()
    val pEvening by viewModel.periodEvening.collectAsState()

    val bgUri by viewModel.bgImageUri.collectAsState()
    val bgOpacity by viewModel.bgOpacity.collectAsState()
    val borderOpacity by viewModel.borderOpacity.collectAsState()
    val courseOpacity by viewModel.courseOpacity.collectAsState()

    val todoBgUri by viewModel.todoBgImageUri.collectAsState()
    val todoBgOpacity by viewModel.todoBgOpacity.collectAsState()
    val todoCalendarOpacity by viewModel.todoCalendarOpacity.collectAsState()
    val todoCardOpacity by viewModel.todoCardOpacity.collectAsState()

    val scheduleTransparentHeader by viewModel.scheduleTransparentHeader.collectAsState()
    val todoTransparentHeader by viewModel.todoTransparentHeader.collectAsState()

    val scheduleForceDark by viewModel.scheduleForceDark.collectAsState()
    val todoForceDark by viewModel.todoForceDark.collectAsState()

    val themeMode by viewModel.themeMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()

    // 🔥 新增：显示设置状态
    val showCourseInApp by viewModel.showCourseInApp.collectAsState()
    val showTodoInApp by viewModel.showTodoInApp.collectAsState()
    val separateMode by viewModel.separateMode.collectAsState()
    val showCourseInWidget by viewModel.showCourseInWidget.collectAsState()
    val showTodoInWidget by viewModel.showTodoInWidget.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> viewModel.setBackgroundImage(uri) }

    val todoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> viewModel.setTodoBackgroundImage(uri) }

    val themeColors = listOf("#6650a4", "#F44336", "#E91E63", "#9C27B0", "#2196F3", "#009688", "#4CAF50", "#FF9800", "#795548", "#607D8B")

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ⚙️ 功能管理
            Text("⚙️ 功能管理", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManageCard("课表管理", Icons.Default.DateRange, MaterialTheme.colorScheme.primaryContainer, onNavigateToScheduleManager)
                ManageCard("待办管理", Icons.Default.List, MaterialTheme.colorScheme.secondaryContainer, onNavigateToTodoManager)
                ManageCard("消息提醒", Icons.Default.Notifications, MaterialTheme.colorScheme.tertiaryContainer, onNavigateToNotificationManager)
            }

            // 🔥🔥 新增：显示设置
            Text("👁️ 显示设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📱 应用内显示", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("显示今日课程")
                        Switch(checked = showCourseInApp, onCheckedChange = { viewModel.updateAppDisplaySettings(it, showTodoInApp, separateMode) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("显示待办事项")
                        Switch(checked = showTodoInApp, onCheckedChange = { viewModel.updateAppDisplaySettings(showCourseInApp, it, separateMode) })
                    }

                    // 只有当课程和待办都开启时，才显示分离选项
                    AnimatedVisibility(visible = showCourseInApp && showTodoInApp, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("分离课程与待办", fontWeight = FontWeight.Bold)
                                    Text(if(separateMode) "课程在顶部，待办在底部" else "按时间顺序混合排列", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Switch(checked = separateMode, onCheckedChange = { viewModel.updateAppDisplaySettings(showCourseInApp, showTodoInApp, it) })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("🧩 小组件显示", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("显示今日课程")
                        Switch(checked = showCourseInWidget, onCheckedChange = { viewModel.updateWidgetDisplaySettings(it, showTodoInWidget) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("显示待办事项")
                        Switch(checked = showTodoInWidget, onCheckedChange = { viewModel.updateWidgetDisplaySettings(showCourseInWidget, it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 提示：在桌面长按小组件并稍微拖动，松手后即可调整大小。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 🎨 课程表外观
            Text("🎨 课程表外观", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("背景图片", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            if (bgUri != null) AsyncImage(model = bgUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text("无", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { launcher.launch("image/*") }) { Text(if(bgUri==null) "选择图片" else "更换图片") }
                        if (bgUri != null) { Spacer(modifier = Modifier.width(8.dp)); OutlinedButton(onClick = { viewModel.setBackgroundImage(null) }) { Text("清除") } }
                    }

                    if (bgUri != null) {
                        Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(8.dp))
                        Text("背景不透明度: ${(bgOpacity * 100).toInt()}%")
                        DebouncedSlider(value = bgOpacity, onValueChangeFinished = { viewModel.updateOpacities(it, borderOpacity, courseOpacity) })
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("沉浸式头部导航", fontWeight = FontWeight.Bold); Text("仅课表生效", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                            Switch(checked = scheduleTransparentHeader, onCheckedChange = { viewModel.updateScheduleBgSettings(it, scheduleForceDark) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("深色背景适配", fontWeight = FontWeight.Bold); Text("仅课表生效", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                            Switch(checked = scheduleForceDark, onCheckedChange = { viewModel.updateScheduleBgSettings(scheduleTransparentHeader, it) })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("表格边框透明度: ${(borderOpacity * 100).toInt()}%")
                    DebouncedSlider(value = borderOpacity, onValueChangeFinished = { viewModel.updateOpacities(bgOpacity, it, courseOpacity) })
                    Text("课程色块不透明度: ${(courseOpacity * 100).toInt()}%")
                    DebouncedSlider(value = courseOpacity, onValueChangeFinished = { viewModel.updateOpacities(bgOpacity, borderOpacity, it) })
                }
            }

            // 📝 待办页面外观
            Text("📝 待办页面外观", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("背景图片", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            if (todoBgUri != null) AsyncImage(model = todoBgUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text("无", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { todoLauncher.launch("image/*") }) { Text(if(todoBgUri==null) "选择图片" else "更换图片") }
                        if (todoBgUri != null) { Spacer(modifier = Modifier.width(8.dp)); OutlinedButton(onClick = { viewModel.setTodoBackgroundImage(null) }) { Text("清除") } }
                    }

                    if (todoBgUri != null) {
                        Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(8.dp))
                        Text("背景图片不透明度: ${(todoBgOpacity * 100).toInt()}%")
                        DebouncedSlider(value = todoBgOpacity, onValueChangeFinished = { viewModel.updateTodoOpacity(it) })
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("日历区域不透明度: ${(todoCalendarOpacity * 100).toInt()}%")
                        DebouncedSlider(value = todoCalendarOpacity, onValueChangeFinished = { viewModel.updateTodoSpecificOpacities(it, todoCardOpacity) })
                        Text("待办卡片不透明度: ${(todoCardOpacity * 100).toInt()}%")
                        DebouncedSlider(value = todoCardOpacity, onValueChangeFinished = { viewModel.updateTodoSpecificOpacities(todoCalendarOpacity, it) })
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("沉浸式头部导航", fontWeight = FontWeight.Bold); Text("仅待办生效", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                            Switch(checked = todoTransparentHeader, onCheckedChange = { viewModel.updateTodoBgSettings(it, todoForceDark) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("深色背景适配", fontWeight = FontWeight.Bold); Text("仅待办生效", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                            Switch(checked = todoForceDark, onCheckedChange = { viewModel.updateTodoBgSettings(todoTransparentHeader, it) })
                        }
                    }
                }
            }

            // 📅 课表参数
            Text("📅 课表参数", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("周末显示", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showSat, onCheckedChange = { viewModel.updateWeekend(it, showSun) }); Text("周六")
                        Spacer(modifier = Modifier.width(24.dp))
                        Checkbox(checked = showSun, onCheckedChange = { viewModel.updateWeekend(showSat, it) }); Text("周日")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("每天节数设置 (总计: ${pMorning + pAfternoon + pEvening}节)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PeriodSelectorRow("☀️ 上午", pMorning) { viewModel.updatePeriods(it, pAfternoon, pEvening) }
                    PeriodSelectorRow("🌤️ 下午", pAfternoon) { viewModel.updatePeriods(pMorning, it, pEvening) }
                    PeriodSelectorRow("🌙 晚上", pEvening) { viewModel.updatePeriods(pMorning, pAfternoon, it) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth().clickable { onNavigateToCourseTimeManager() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("课程时间管理", fontWeight = FontWeight.Bold); Icon(Icons.Default.ChevronRight, contentDescription = "进入")
                    }
                }
            }

            // 🌈 主题设置
            Text("🌈 主题设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("夜间模式", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("light" to "浅色", "auto" to "自动", "dark" to "深色").forEach { (mode, label) ->
                            FilterChip(selected = themeMode == mode, onClick = { viewModel.updateTheme(mode, themeColor) }, label = { Text(label) })
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("主题颜色", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(themeColors) { color ->
                            val isSelected = themeColor.equals(color, ignoreCase = true)
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(color))).then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier).clickable { viewModel.updateTheme(themeMode, color) })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

// 辅助组件
@Composable
fun RowScope.ManageCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.weight(1f).height(100.dp), colors = CardDefaults.cardColors(containerColor = color), onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DebouncedSlider(value: Float, onValueChangeFinished: (Float) -> Unit) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { onValueChangeFinished(sliderValue) })
}

@Composable
fun PeriodSelectorRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..6).forEach { num ->
                Box(modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(if(value == num) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { onValueChange(num) }, contentAlignment = Alignment.Center) {
                    Text(text = num.toString(), color = if(value == num) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}