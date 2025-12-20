// src/utils/scheduleHelpers.ts

// 🔥 1. 精选的“好看”颜色池 (莫兰迪/Material风格)
const NICE_COLORS = [
    '#7986CB', // 靛青
    '#64B5F6', // 蓝色
    '#4DD0E1', // 青色
    '#4DB6AC', // 蓝绿
    '#81C784', // 绿色
    '#AED581', // 浅绿
    '#FFB74D', // 橙色
    '#FF8A65', // 深橙
    '#A1887F', // 褐色
    '#90A4AE', // 蓝灰
    '#9575CD', // 紫色
    '#F06292', // 粉色
];

const getRandomColor = () => {
    const randomIndex = Math.floor(Math.random() * NICE_COLORS.length);
    return NICE_COLORS[randomIndex];
};

export const AI_PROMPT_TEMPLATE = `请帮我识别这张课程表图片，提取所有课程信息，并严格按照以下 JSON 格式输出纯文本（不要使用 Markdown 代码块）。

【字段说明】
1. "info": 包含 name (课表名称) 和 termStartDate (开学日期，格式 YYYY-MM-DD，若无法识别请默认今天)。
2. "courses": 课程数组。
   - "name": 课程名称。
   - "day": 0代表周一，1代表周二... 6代表周日。
   - "startPeriod": 开始节次（数字，如 1）。
   - "endPeriod": 结束节次（数字，如 2）。
   - "weeks": 周次数组，必须是数字数组。例如 [1,2,3,4,5,6,7,8]。
     * 如果写着"1-16周"，请展开为 [1,2,3...16]。
     * 如果写着"单周"，请只保留单数 [1,3,5...]。
     * 如果写着"双周"，请只保留双数 [2,4,6...]。
   - "classroom": 教室地点 (字符串)。
   - "teacher": 教师姓名 (字符串，可选)。
   - "color": 请随机给一个十六进制颜色 (如 "#2196F3")。

【JSON 模板示例】
{
  "info": { "name": "我的课表", "termStartDate": "2025-02-24" },
  "courses": [
    { "name": "高等数学", "day": 0, "startPeriod": 1, "endPeriod": 2, "weeks": [1,2,3,4,5,6,7,8], "classroom": "A101", "teacher": "张三", "color": "#FF5722" }
  ]
}

请直接输出 JSON 字符串，不要包含 \`\`\`json 标记。可能会存在重复的课程，请全部列出。在不同周的同一时间段有不同课程的情况，也请全部列出。`;

/**
 * 获取当前日期所在周的周一
 * 修复了周日(0)的问题，并避免修改传入的原始对象
 */
export const getMonday = (d: Date) => {
    const date = new Date(d); // 🔥 关键修复：创建副本，不要修改原对象
    const day = date.getDay();
    // 逻辑：如果是周日(0)，回退6天；否则回退 day-1 天
    const diff = date.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(date.setDate(diff));
};

/**
 * 格式化日期为 YYYY-MM-DD
 * 修复了 toISOString 导致的时区偏差问题（比如周一变成周日）
 */
export const formatDate = (date: Date) => {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = (d.getMonth() + 1).toString().padStart(2, '0');
    const day = d.getDate().toString().padStart(2, '0');
    return `${year}-${month}-${day}`;
};

// OCR 解析算法
export const parseOcrResultToSchedule = (blocks: any[]) => {
    const cleanBlocks = blocks.map((b: any) => ({
        text: b.text.trim(),
        x: b.frame?.left ?? b.rect?.left ?? 0,
        y: b.frame?.top ?? b.rect?.top ?? 0,
        w: b.frame?.width ?? b.rect?.width ?? 0,
        h: b.frame?.height ?? b.rect?.height ?? 0,
        cx: (b.frame?.left ?? 0) + (b.frame?.width ?? 0) / 2,
        cy: (b.frame?.top ?? 0) + (b.frame?.height ?? 0) / 2
    })).filter((b: any) => b.text.length > 0);

    const weekKeywords = ['一', '二', '三', '四', '五', '六', '日'];
    const headers: { day: number, cx: number, cy: number }[] = [];

    cleanBlocks.forEach((b: any) => {
        weekKeywords.forEach((k, idx) => {
            if (b.text.includes(`星期${k}`) || b.text.includes(`周${k}`)) {
                if (b.text.length < 6) {
                    headers.push({ day: idx, cx: b.cx, cy: b.cy });
                }
            }
        });
    });
    headers.sort((a, b) => a.cx - b.cx);

    if (headers.length === 0) return null;

    let avgWidth = 200;
    if (headers.length > 1) {
        avgWidth = (headers[headers.length - 1].cx - headers[0].cx) / (headers.length - 1);
    }

    const finalHeaders: { day: number, cx: number }[] = [];
    [0, 1, 2, 3, 4].forEach(day => {
        const found = headers.find(h => h.day === day);
        if (found) {
            finalHeaders.push(found);
        } else {
            const base = headers[0];
            const estimatedCx = base.cx + (day - base.day) * avgWidth;
            finalHeaders.push({ day, cx: estimatedCx });
        }
    });

    const colRanges: { day: number, minX: number, maxX: number }[] = [];
    finalHeaders.forEach((h, i) => {
        let minX = -99999;
        let maxX = 99999;
        if (i > 0) minX = (finalHeaders[i - 1].cx + h.cx) / 2;
        if (i < finalHeaders.length - 1) maxX = (h.cx + finalHeaders[i + 1].cx) / 2;
        colRanges.push({ day: h.day, minX, maxX });
    });

    const headerBottomY = Math.max(...headers.map(h => h.cy)) + 50;
    const rows: { cy: number, blocks: any[] }[] = [];

    cleanBlocks.forEach((b: any) => {
        if (b.cy < headerBottomY) return;
        let matchedRow = rows.find(r => Math.abs(r.cy - b.cy) < 100);
        if (matchedRow) {
            matchedRow.blocks.push(b);
            const sumCy = matchedRow.blocks.reduce((acc, blk) => acc + blk.cy, 0);
            matchedRow.cy = sumCy / matchedRow.blocks.length;
        } else {
            rows.push({ cy: b.cy, blocks: [b] });
        }
    });
    rows.sort((a, b) => a.cy - b.cy);

    const detectedCourses: any[] = [];

    rows.forEach((row, rowIndex) => {
        const startPeriod = rowIndex * 2 + 1;
        const endPeriod = startPeriod + 1;
        const colBuckets = new Map<number, any[]>();
        row.blocks.forEach((b: any) => {
            const matchedCol = colRanges.find(r => b.cx >= r.minX && b.cx < r.maxX);
            if (matchedCol) {
                const day = matchedCol.day;
                if (!colBuckets.has(day)) colBuckets.set(day, []);
                colBuckets.get(day)!.push(b);
            }
        });

        colBuckets.forEach((bucketBlocks, day) => {
            const sortedLines = bucketBlocks.sort((a, b) => a.y - b.y).map(b => b.text);
            let classroom = "";
            let weeks: number[] = Array.from({ length: 18 }, (_, i) => i + 1);

            for (let line of sortedLines) {
                if (line.includes('周') && /\d/.test(line)) {
                    const match = line.match(/(\d+)-(\d+)/);
                    if (match) {
                        const s = parseInt(match[1]);
                        const e = parseInt(match[2]);
                        const isOdd = line.includes('单');
                        const isEven = line.includes('双');
                        weeks = [];
                        for (let w = s; w <= e; w++) {
                            if (isOdd && w % 2 === 0) continue;
                            if (isEven && w % 2 !== 0) continue;
                            weeks.push(w);
                        }
                    }
                }
                if (line.includes('楼') || line.includes('室') || line.includes('机房') || line.includes('区') || /^[A-Z]\d{3}/.test(line)) {
                    classroom = line.replace(/[()（）]/g, '');
                }
            }

            const finalName = sortedLines.join('\n');
            if (finalName.length > 0) {
                detectedCourses.push({
                    name: finalName,
                    classroom: classroom,
                    day: day,
                    startPeriod: startPeriod,
                    endPeriod: endPeriod,
                    weeks: weeks,
                    teacher: '',
                    // 🔥 2. 使用随机颜色
                    color: getRandomColor(),
                });
            }
        });
    });

    return {
        // 修正：termStartDate 也使用修复后的 getMonday 和 formatDate 逻辑
        info: { name: 'OCR 导入', termStartDate: formatDate(getMonday(new Date())) },
        courses: detectedCourses
    };
};