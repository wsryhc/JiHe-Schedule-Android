package com.example.jihe_schedule.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jihe_schedule.data.AppDatabase
import com.example.jihe_schedule.model.Course
import com.example.jihe_schedule.model.ScheduleInfo
import com.example.jihe_schedule.util.ScheduleOcrParser
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// 完整备份数据结构
data class ScheduleExportData(
    val scheduleInfo: ScheduleInfo?,
    val courses: List<Course>?
)

// 仅课程的导入结构（专门用于 AI 导入）
data class AiImportData(
    val courses: List<Course>?
)

// 编辑包装类
data class EditableCourseWrapper(
    val tempId: String = UUID.randomUUID().toString(),
    val course: Course,
    val hasConflict: Boolean = false,
    val conflictReason: String = ""
)

class ScheduleManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val scheduleDao = db.scheduleDao()
    private val courseDao = db.courseDao()

    private val _schedules = MutableStateFlow<List<ScheduleInfo>>(emptyList())
    val schedules = _schedules.asStateFlow()

    private val _pendingReviewData = MutableStateFlow<Pair<ScheduleInfo, List<EditableCourseWrapper>>?>(null)
    val pendingReviewData = _pendingReviewData.asStateFlow()

    init {
        loadSchedules()
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            scheduleDao.getAllSchedules().collect { list ->
                _schedules.value = list
                checkAndFixDuplicateSelection(list)
            }
        }
    }

    private fun checkAndFixDuplicateSelection(list: List<ScheduleInfo>) {
        val selected = list.filter { it.isSelected }
        if (selected.size > 1) {
            viewModelScope.launch(Dispatchers.IO) {
                for (i in 1 until selected.size) {
                    scheduleDao.updateSchedule(selected[i].copy(isSelected = false))
                }
            }
        }
    }

    fun setActiveSchedule(targetSchedule: ScheduleInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleDao.switchActiveSchedule(targetSchedule.id)
        }
    }

    private fun adjustToMonday(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr)
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        } catch (e: Exception) {
            dateStr
        }
    }

    fun upsertSchedule(schedule: ScheduleInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val fixedSchedule = schedule.copy(termStartDate = adjustToMonday(schedule.termStartDate))
            scheduleDao.insertSchedule(fixedSchedule)
        }
    }

    fun deleteSchedule(schedule: ScheduleInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            courseDao.deleteCoursesByScheduleId(schedule.id)
            scheduleDao.deleteSchedule(schedule)
        }
    }

    // 🔥 新增：删除所有课表
    fun deleteAllSchedules() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _schedules.value
            list.forEach { schedule ->
                courseDao.deleteCoursesByScheduleId(schedule.id)
                scheduleDao.deleteSchedule(schedule)
            }
        }
    }

    fun checkConflicts(courses: List<Course>): List<EditableCourseWrapper> {
        val wrappers = courses.map { EditableCourseWrapper(course = it) }.toMutableList()
        for (i in wrappers.indices) {
            for (j in i + 1 until wrappers.size) {
                val c1 = wrappers[i].course
                val c2 = wrappers[j].course
                if (c1.day == c2.day) {
                    if (max(c1.startPeriod, c2.startPeriod) <= min(c1.endPeriod, c2.endPeriod)) {
                        if (c1.weeks.intersect(c2.weeks.toSet()).isNotEmpty()) {
                            wrappers[i] = wrappers[i].copy(hasConflict = true, conflictReason = "冲突: ${c2.name}")
                            wrappers[j] = wrappers[j].copy(hasConflict = true, conflictReason = "冲突: ${c1.name}")
                        }
                    }
                }
            }
        }
        return wrappers
    }

    fun importFromJson(jsonString: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gson = Gson()
                val newId = UUID.randomUUID().toString()
                var parsedSchedule: ScheduleInfo? = null
                var parsedCourses: List<Course> = emptyList()

                try {
                    val fullData = gson.fromJson(jsonString, ScheduleExportData::class.java)
                    if (fullData?.scheduleInfo != null && !fullData.courses.isNullOrEmpty()) {
                        val fixedStartDate = adjustToMonday(fullData.scheduleInfo.termStartDate)
                        parsedSchedule = fullData.scheduleInfo.copy(
                            id = newId,
                            name = "${fullData.scheduleInfo.name} (导入)",
                            termStartDate = fixedStartDate,
                            isSelected = false
                        )
                        parsedCourses = fullData.courses
                    }
                } catch (e: Exception) {
                }

                if (parsedSchedule == null) {
                    try {
                        val aiData = gson.fromJson(jsonString, AiImportData::class.java)
                        if (!aiData?.courses.isNullOrEmpty()) {
                            val today = LocalDate.now().toString()
                            parsedSchedule = ScheduleInfo(
                                id = newId,
                                name = "AI 导入课表",
                                termStartDate = adjustToMonday(today),
                                totalWeeks = 20,
                                isSelected = false
                            )
                            parsedCourses = aiData.courses
                        }
                    } catch (e: Exception) {
                    }
                }

                if (parsedSchedule != null && parsedCourses.isNotEmpty()) {
                    val finalCourses = parsedCourses.map {
                        it.copy(
                            id = 0,
                            scheduleId = newId,
                            color = if (it.color.isNullOrEmpty()) "#2196F3" else it.color
                        )
                    }
                    val checked = checkConflicts(finalCourses)
                    _pendingReviewData.value = parsedSchedule to checked
                    viewModelScope.launch(Dispatchers.Main) { onSuccess() }
                } else {
                    throw Exception("无法识别 JSON 格式，请确认包含了 'courses' 列表")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) { onError("导入失败: ${e.message}") }
            }
        }
    }

    fun importFromFile(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { it.readText() }
                importFromJson(jsonString, onSuccess, onError)
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) { onError("文件读取失败: ${e.message}") }
            }
        }
    }

    fun parseOcrImage(context: Context, imageUri: Uri, targetScheduleId: String, onError: (String) -> Unit) {
        val scheduleId = if (targetScheduleId.isNullOrEmpty()) UUID.randomUUID().toString() else targetScheduleId
        ScheduleOcrParser.parseImage(
            context = context,
            uri = imageUri,
            scheduleId = scheduleId,
            onResult = { courses ->
                if (courses.isNotEmpty()) {
                    val today = LocalDate.now().toString()
                    val tempSchedule = ScheduleInfo(
                        id = scheduleId,
                        name = "OCR 导入课表",
                        termStartDate = adjustToMonday(today),
                        totalWeeks = 20,
                        isSelected = false
                    )
                    val checked = checkConflicts(courses)
                    _pendingReviewData.value = tempSchedule to checked
                } else {
                    onError("未能识别到有效课程，请确保图片清晰且包含'星期'表头")
                }
            },
            onError = { errorMessage -> onError(errorMessage) }
        )
    }

    fun saveReviewedSchedule(schedule: ScheduleInfo, wrappers: List<EditableCourseWrapper>, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fixedSchedule = schedule.copy(
                termStartDate = adjustToMonday(schedule.termStartDate),
                isSelected = false
            )
            scheduleDao.insertSchedule(fixedSchedule)
            wrappers.forEach {
                val finalCourse = it.course.copy(scheduleId = fixedSchedule.id)
                courseDao.insertCourse(finalCourse)
            }
            _pendingReviewData.value = null
            viewModelScope.launch(Dispatchers.Main) { onSuccess() }
        }
    }

    fun discardReview() {
        _pendingReviewData.value = null
    }

    fun exportAsImage(
        context: Context,
        schedule: ScheduleInfo,
        week: Int,
        themeMode: String,
        periods: Triple<Int, Int, Int> = Triple(4, 4, 4)
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val courses = courseDao.getCoursesDirect(schedule.id)
                val bitmap = drawScheduleImage(context, schedule, courses, week, themeMode, periods)
                saveBitmapToGallery(context, bitmap, "${schedule.name}_第${week}周")
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveJsonToUri(context: Context, uri: Uri, schedule: ScheduleInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val courses = courseDao.getCoursesDirect(schedule.id)
                val exportData = ScheduleExportData(schedule, courses)
                val jsonString = Gson().toJson(exportData)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun copyJsonToClipboard(context: Context, schedule: ScheduleInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val courses = courseDao.getCoursesDirect(schedule.id)
            val exportData = ScheduleExportData(schedule, courses)
            val jsonString = Gson().toJson(exportData)
            viewModelScope.launch(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Schedule JSON", jsonString)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "JSON 已复制", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawScheduleImage(
        context: Context,
        schedule: ScheduleInfo,
        courses: List<Course>,
        targetWeek: Int,
        themeMode: String,
        periods: Triple<Int, Int, Int>
    ): Bitmap {
        val (pMorning, pAfternoon, pEvening) = periods
        val maxPeriods = pMorning + pAfternoon + pEvening

        val width = 1080
        val headerHeight = 150
        val cellHeight = 120
        val breakHeight = 80
        val sidebarWidth = 100
        val colWidth = (width - sidebarWidth) / 7

        val periodYOffsets = mutableMapOf<Int, Int>()
        var currentY = headerHeight
        for (i in 1..maxPeriods) {
            periodYOffsets[i] = currentY
            currentY += cellHeight
            if (i == pMorning) currentY += breakHeight
            if (i == pMorning + pAfternoon) currentY += breakHeight
        }
        val height = currentY + 100

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isDark = themeMode == "dark"
        val bgColor = if (isDark) Color.parseColor("#1C1B1F") else Color.WHITE
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val gridColor = if (isDark) Color.parseColor("#444444") else Color.LTGRAY
        val subTextColor = if (isDark) Color.LTGRAY else Color.GRAY

        canvas.drawColor(bgColor)

        val textPaint = Paint().apply { color = textColor; textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val smallTextPaint = Paint().apply { color = subTextColor; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val linePaint = Paint().apply { color = gridColor; strokeWidth = 2f }
        val coursePaint = Paint().apply { style = Paint.Style.FILL }

        val courseTextPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }

        textPaint.textSize = 50f
        textPaint.isFakeBoldText = true
        canvas.drawText("${schedule.name} (第${targetWeek}周)", width / 2f, 80f, textPaint)
        textPaint.isFakeBoldText = false

        val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")
        textPaint.textSize = 30f
        for (i in 0..6) {
            val x = sidebarWidth + i * colWidth + colWidth / 2f
            canvas.drawText("周${weekDays[i]}", x, headerHeight - 30f, textPaint)
        }
        canvas.drawLine(0f, headerHeight.toFloat(), width.toFloat(), headerHeight.toFloat(), linePaint)

        for (i in 1..maxPeriods) {
            val y = periodYOffsets[i]!!
            val centerY = y + cellHeight / 2f

            textPaint.textSize = 30f
            canvas.drawText(i.toString(), sidebarWidth / 2f, centerY + 10f, textPaint)

            val lineY = y.toFloat()
            canvas.drawLine(0f, lineY, width.toFloat(), lineY, linePaint)

            val bottomY = (y + cellHeight).toFloat()
            canvas.drawLine(0f, bottomY, width.toFloat(), bottomY, linePaint)

            if (i == pMorning) {
                val breakCenterY = bottomY + breakHeight / 2f + 10f
                smallTextPaint.textSize = 28f
                canvas.drawText("午休", width / 2f, breakCenterY, smallTextPaint)
            }
            if (i == pMorning + pAfternoon) {
                val breakCenterY = bottomY + breakHeight / 2f + 10f
                smallTextPaint.textSize = 28f
                canvas.drawText("晚休", width / 2f, breakCenterY, smallTextPaint)
            }
        }

        val break1Top = (periodYOffsets[pMorning]!! + cellHeight).toFloat()
        val break1Bottom = break1Top + breakHeight
        val break2Top = (periodYOffsets[pMorning + pAfternoon]!! + cellHeight).toFloat()
        val break2Bottom = break2Top + breakHeight

        for (i in 0..7) {
            val x = (sidebarWidth + i * colWidth).toFloat()
            canvas.drawLine(x, headerHeight.toFloat(), x, break1Top, linePaint)
            canvas.drawLine(x, break1Bottom, x, break2Top, linePaint)
            canvas.drawLine(x, break2Bottom, x, (periodYOffsets[maxPeriods]!! + cellHeight).toFloat(), linePaint)
        }

        val weekCourses = courses.filter { it.weeks.contains(targetWeek) }
        weekCourses.forEach { course ->
            val dayIdx = course.day - 1
            if (dayIdx in 0..6) {
                val startP = course.startPeriod
                val endP = course.endPeriod
                val startY = periodYOffsets[startP] ?: return@forEach
                var endY = 0
                if (endP > startP) {
                    val lastPStart = periodYOffsets[endP] ?: startY
                    endY = lastPStart + cellHeight
                } else {
                    endY = startY + cellHeight
                }

                val left = sidebarWidth + dayIdx * colWidth + 5f
                val top = startY + 5f
                val right = left + colWidth - 10f
                val bottom = endY - 5f

                try {
                    coursePaint.color = Color.parseColor(course.color)
                } catch (e: Exception) { coursePaint.color = Color.BLUE }

                val rect = RectF(left, top.toFloat(), right, bottom.toFloat())
                canvas.drawRoundRect(rect, 16f, 16f, coursePaint)

                val textWidth = (colWidth - 20).toInt()
                if (textWidth > 0) {
                    canvas.save()
                    canvas.translate(left + 10f, top.toFloat() + 10f)

                    courseTextPaint.textSize = 28f
                    courseTextPaint.isFakeBoldText = true
                    val nameLayout = StaticLayout.Builder.obtain(course.name, 0, course.name.length, courseTextPaint, textWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                    nameLayout.draw(canvas)

                    canvas.translate(0f, nameLayout.height + 10f)

                    if (course.classroom.isNotEmpty()) {
                        courseTextPaint.textSize = 24f
                        courseTextPaint.isFakeBoldText = false
                        val roomLayout = StaticLayout.Builder.obtain("@${course.classroom}", 0, course.classroom.length + 1, courseTextPaint, textWidth)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .build()
                        roomLayout.draw(canvas)
                    }
                    canvas.restore()
                }
            }
        }
        return bitmap
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String) {
        val filename = "$title.png"
        var fos: java.io.OutputStream? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JiHeSchedule")
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = resolver.openOutputStream(imageUri!!)
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }
        fos?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}