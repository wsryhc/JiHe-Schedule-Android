package com.example.jihe_schedule.util

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.example.jihe_schedule.model.Course
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.IOException
import java.util.regex.Pattern
import kotlin.math.abs

object ScheduleOcrParser {

    // 星期映射
    private val WEEK_DAYS_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7
    )

    /**
     * 对外暴露的解析方法 (异步)
     */
    fun parseImage(
        context: Context,
        uri: Uri,
        scheduleId: String,
        onResult: (List<Course>) -> Unit,
        onError: (String) -> Unit
    ) {
        val image: InputImage
        try {
            image = InputImage.fromFilePath(context, uri)
        } catch (e: IOException) {
            onError("无法读取图片: ${e.message}")
            return
        }

        // 使用中文识别器
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                try {
                    val courses = analyzeLayoutAndParse(visionText, scheduleId)
                    onResult(courses)
                } catch (e: Exception) {
                    onError("解析逻辑错误: ${e.message}")
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                onError("文字识别失败: ${e.message}")
            }
    }

    /**
     * 核心算法：基于几何位置的分析 + 缺失表头推断
     */
    private fun analyzeLayoutAndParse(visionText: Text, scheduleId: String): List<Course> {
        val courses = mutableListOf<Course>()
        val allLines = visionText.textBlocks.flatMap { it.lines }

        if (allLines.isEmpty()) return emptyList()

        // 1. 寻找显式表头（OCR 识别出的星期几）
        val detectedHeaders = mutableListOf<Pair<Int, Rect>>() // (Day 1-7, BoundingBox)
        val headerPattern = Pattern.compile(".*(星期|周)([一二三四五六日]).*")
        val simpleDayPattern = Pattern.compile("^[一二三四五六日]$")

        for (line in allLines) {
            val text = line.text.trim()
            val box = line.boundingBox ?: continue

            // 过滤：表头通常在页面上方（假设前 1/3 区域），防止把底部的备注当表头
            // 这里取整张图高度的估算值，如果没有全图尺寸，暂时忽略，主要靠正则

            var dayVal = 0
            val matcher = headerPattern.matcher(text)
            if (matcher.find()) {
                dayVal = mapChineseDayToInt(matcher.group(2))
            } else if (simpleDayPattern.matcher(text).matches()) {
                dayVal = mapChineseDayToInt(text)
            }

            if (dayVal > 0) {
                val exists = detectedHeaders.find { it.first == dayVal }
                if (exists == null) {
                    detectedHeaders.add(dayVal to box)
                } else {
                    // 如果重复，取 Y 坐标更小的（更靠上的）
                    if (box.top < exists.second.top) {
                        detectedHeaders.remove(exists)
                        detectedHeaders.add(dayVal to box)
                    }
                }
            }
        }

        if (detectedHeaders.isEmpty()) return emptyList()

        // 按 X 坐标排序
        detectedHeaders.sortBy { it.second.centerX() }

        // --- 🔥 关键修改：推断缺失的表头 (Virtual Headers) ---
        // 比如识别到了周一、周三，没识别到周二、周四，我们需要根据间距补全
        val completeHeaders = inferMissingHeaders(detectedHeaders)

        // 2. 遍历剩余文本，归类到最近的“星期列”
        val dayGroups = mutableMapOf<Int, MutableList<Text.Line>>()

        // 计算表头的下边界，只有在这个下面的才算课程内容
        val headerBottomLimit = completeHeaders.minOf { it.second.bottom }

        for (line in allLines) {
            val box = line.boundingBox ?: continue

            // 跳过已经是表头的行 (用引用或者位置判断)
            // 这里简单用位置判断：如果该行中心点落在某个表头框内，跳过
            var isHeader = false
            for (header in completeHeaders) {
                if (header.second.contains(box.centerX(), box.centerY())) {
                    isHeader = true
                    break
                }
            }
            if (isHeader) continue

            // 只有位于表头下方的文字才算
            if (box.centerY() < headerBottomLimit) continue

            // 寻找 X 轴中心点距离最近的表头
            val centerX = box.centerX()
            var closestDay = -1
            var minDistance = Int.MAX_VALUE

            for ((day, headerBox) in completeHeaders) {
                val distance = abs(centerX - headerBox.centerX())
                if (distance < minDistance) {
                    minDistance = distance
                    closestDay = day
                }
            }

            // 🔥 增加一个阈值判断：如果离最近的列还是太远（比如超过平均列宽），可能是侧边栏的时间（8:00-9:00），忽略之
            // 这里为了容错率，暂时不做严格剔除，直接归类
            if (closestDay != -1) {
                dayGroups.getOrPut(closestDay) { mutableListOf() }.add(line)
            }
        }

        // 3. 列内排序与合并
        for ((day, lines) in dayGroups) {
            lines.sortBy { it.boundingBox?.top ?: 0 }
            val mergedBlocks = mergeLinesToBlocks(lines)

            // 节次估算逻辑
            var currentStartPeriod = 1
            for (block in mergedBlocks) {
                val course = parseBlockToCourse(block, day, currentStartPeriod, scheduleId)
                if (course != null) {
                    courses.add(course)
                    // 假设每门课占 2 节
                    currentStartPeriod += 2
                }
            }
        }

        return courses
    }

    /**
     * 🔥 智能推断缺失表头
     * 就算OCR没识别出"周四"，只要有"周一"和"周二"，算出间距就能猜出"周四"在哪里
     */
    private fun inferMissingHeaders(detected: List<Pair<Int, Rect>>): List<Pair<Int, Rect>> {
        if (detected.size < 2) return detected // 只有一个参考点，没法算间距，原样返回

        val result = detected.toMutableList()
        val first = detected.first()
        val last = detected.last()

        // 计算平均列宽 (LastX - FirstX) / (LastDayIndex - FirstDayIndex)
        // 例如：周三(index 3) X=300，周一(index 1) X=100。 间距 = (300-100) / (3-1) = 100
        val daySpan = last.first - first.first
        if (daySpan == 0) return detected

        val avgWidth = (last.second.centerX() - first.second.centerX()) / daySpan

        if (avgWidth <= 0) return detected

        // 1. 补全中间缺失的 (比如有周一、周三，补周二)
        for (day in first.first + 1 until last.first) {
            if (result.none { it.first == day }) {
                // 计算理论上的中心点 X
                val virtualCenterX = first.second.centerX() + (day - first.first) * avgWidth
                // 创建一个虚拟的 Rect
                val virtualRect = Rect(
                    virtualCenterX - avgWidth / 2, first.second.top,
                    virtualCenterX + avgWidth / 2, first.second.bottom
                )
                result.add(day to virtualRect)
            }
        }

        // 2. 补全右侧缺失的 (比如只有周一到周三，补全周四、周五、周六、周日)
        // 假设最大到周日(7)
        for (day in last.first + 1..7) {
            if (result.none { it.first == day }) {
                val virtualCenterX = first.second.centerX() + (day - first.first) * avgWidth
                val virtualRect = Rect(
                    virtualCenterX - avgWidth / 2, first.second.top,
                    virtualCenterX + avgWidth / 2, first.second.bottom
                )
                result.add(day to virtualRect)
            }
        }

        // 再次排序确保顺序
        result.sortBy { it.second.centerX() }
        return result
    }

    private fun mergeLinesToBlocks(sortedLines: List<Text.Line>): List<String> {
        val blocks = mutableListOf<String>()
        if (sortedLines.isEmpty()) return blocks

        var currentBlock = StringBuilder(sortedLines[0].text)
        var lastBottom = sortedLines[0].boundingBox?.bottom ?: 0

        // 阈值：行高的 1.2 倍。如果两行间隔超过这个值，视为不同课程
        val mergeThreshold = (sortedLines[0].boundingBox?.height() ?: 50) * 1.2

        for (i in 1 until sortedLines.size) {
            val line = sortedLines[i]
            val top = line.boundingBox?.top ?: 0
            val height = line.boundingBox?.height() ?: 50

            if (top - lastBottom < mergeThreshold) {
                currentBlock.append("\n").append(line.text)
            } else {
                blocks.add(currentBlock.toString())
                currentBlock = StringBuilder(line.text)
            }
            lastBottom = line.boundingBox?.bottom ?: (top + height)
        }
        blocks.add(currentBlock.toString())
        return blocks
    }

    /**
     * 🔥 优化解析逻辑：区分课程名与老师
     */
    private fun parseBlockToCourse(text: String, day: Int, startPeriod: Int, scheduleId: String): Course? {
        // 过滤极短文本
        if (text.length < 2) return null

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        var name = ""
        var teacher = ""
        var classroom = ""
        var weeksList = listOf<Int>()

        // --- 策略修改：基于位置和特征 ---

        // 1. 第一行通常是课程名 (除非它是纯周次 "1-16周")
        val firstLine = lines[0]
        if (isWeekInfo(firstLine)) {
            // 极其罕见的情况：第一行就是周次，说明可能没识别到课名，或者课名在上一块
            val (_, w) = parseTeacherAndWeeks(firstLine)
            weeksList = w
            name = "未知课程"
        } else {
            name = firstLine
        }

        // 2. 遍历剩下的行
        for (i in 1 until lines.size) {
            val line = lines[i]

            if (isWeekInfo(line)) {
                // 如果包含周次信息
                val (t, w) = parseTeacherAndWeeks(line)
                if (w.isNotEmpty()) weeksList = w
                // 有时候 "张三 1-16周" 写在一行，提取出的 teacher 赋值
                if (teacher.isEmpty() && t.isNotEmpty()) teacher = t
            } else if (isClassroom(line)) {
                classroom = line
            } else {
                // 既不是周次也不是教室，且不是第一行 -> 极大概率是老师
                // 防止把长的课程名（被截断成两行）当成老师
                // 简单的启发式：老师名字通常较短 (2-4字)
                if (teacher.isEmpty()) {
                    if (line.length <= 4 || line.endsWith("师")) {
                        teacher = line
                    } else {
                        // 可能是课程名的第二行，追加到名字
                        name += line
                    }
                }
            }
        }

        // 兜底周次
        if (weeksList.isEmpty()) {
            weeksList = (1..16).toList()
        }

        return Course(
            id = 0,
            name = name,
            teacher = teacher,
            classroom = classroom,
            day = day,
            startPeriod = startPeriod,
            endPeriod = startPeriod + 1,
            weeks = weeksList,
            color = getRandomColor(),
            scheduleId = scheduleId
        )
    }

    // 辅助判断：是否是周次信息
    private fun isWeekInfo(line: String): Boolean {
        return line.contains("周") || line.matches(Regex(".*\\d+-\\d+.*")) || line.matches(Regex(".*[单双]周.*"))
    }

    // 辅助判断：是否是教室
    private fun isClassroom(line: String): Boolean {
        return line.contains("楼") || line.contains("室") || line.contains("区") || line.matches(Regex(".*\\d{3}.*"))
    }

    private fun parseTeacherAndWeeks(line: String): Pair<String, List<Int>> {
        val weekRegex = Regex("(\\d+)-(\\d+)")
        val match = weekRegex.find(line)
        var weeks = listOf<Int>()

        // 移除数字、周、特殊符号，剩下的认为是老师名
        // 比如 "张三 1-15周" -> "张三"
        val teacherPart = line.replace(Regex("[\\d\\-()\\[\\]周单双节]"), "").trim()

        if (match != null) {
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            val isOdd = line.contains("单")
            val isEven = line.contains("双")
            val list = mutableListOf<Int>()
            for (i in start..end) {
                if (isOdd && i % 2 == 0) continue
                if (isEven && i % 2 != 0) continue
                list.add(i)
            }
            weeks = list
        } else {
            val singleRegex = Regex("(\\d+)周")
            val singleMatch = singleRegex.find(line)
            if (singleMatch != null) {
                weeks = listOf(singleMatch.groupValues[1].toInt())
            }
        }
        return teacherPart to weeks
    }

    private fun mapChineseDayToInt(day: String?): Int {
        return WEEK_DAYS_MAP[day] ?: 0
    }

    private fun getRandomColor(): String {
        val colors = listOf(
            "#FF8A80", "#FFD180", "#FFFF8D", "#CCFF90", "#A7FFEB",
            "#80D8FF", "#82B1FF", "#B388FF", "#F48FB1", "#FFAB91"
        )
        return colors.random()
    }
}