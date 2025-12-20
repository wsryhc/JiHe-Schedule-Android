// src/components/ScheduleList.tsx
import React, { useMemo } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Dimensions } from 'react-native';
import { useTheme, setAlpha } from '../context/ThemeContext';
import { useSchedule, TimeSlot, ScheduleInfo } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';

const SCREEN_WIDTH = Dimensions.get('window').width;
const SIDEBAR_WIDTH = 45;
const CELL_HEIGHT = 60;
const BREAK_HEIGHT = 36;

interface ScheduleListProps {
    weekIndex: number;
    onCellPress?: (day: number, period: number) => void;
    theme?: any;
    targetSchedule?: ScheduleInfo | null;
}

const ScheduleList = ({ weekIndex, onCellPress, theme: propTheme, targetSchedule }: ScheduleListProps) => {
    const { theme: contextTheme, isDarkMode, customSettings } = useTheme();
    const theme = propTheme || contextTheme;

    const {
        courseList, currentSchedule, periodList, timeLayout,
        weekendConfig, periodConfig
    } = useSchedule();

    const activeSchedule = targetSchedule || currentSchedule;

    const getCardColor = () => customSettings.backgroundImage ? 'transparent' : theme.card;
    const getBorderColor = () => setAlpha(theme.border, customSettings.borderOpacity ?? 0.1);

    const getDateString = (startDateStr: string | undefined, wIndex: number, dIndex: number) => {
        const safeDateStr = startDateStr || new Date().toISOString();
        const start = new Date(safeDateStr);
        const offsetDays = (wIndex - 1) * 7 + dIndex;
        start.setDate(start.getDate() + offsetDays);
        const m = (start.getMonth() + 1).toString().padStart(2, '0');
        const d = start.getDate().toString().padStart(2, '0');
        return `${m}/${d}`;
    };

    const { displayDays, colIndexMapping } = useMemo(() => {
        const base = ['周一', '周二', '周三', '周四', '周五'];
        const mapping = [0, 1, 2, 3, 4];
        if (weekendConfig.saturday) { base.push('周六'); mapping.push(5); }
        if (weekendConfig.sunday) { base.push('周日'); mapping.push(6); }
        return { displayDays: base, colIndexMapping: mapping };
    }, [weekendConfig]);

    const renderCellContent = (dayIndex: number, periodId: number) => {
        const allCourses = courseList.filter(c =>
            c.scheduleId === activeSchedule?.id && c.day === dayIndex && periodId >= c.startPeriod && periodId <= c.endPeriod
        );
        if (allCourses.length === 0) return null;

        let targetCourse = allCourses.find(c => c.weeks.includes(weekIndex));
        let isCurrentWeekCourse = true;

        if (!targetCourse) {
            targetCourse = allCourses[0];
            isCurrentWeekCourse = false;
        }

        if (targetCourse.startPeriod === periodId) {
            const duration = targetCourse.endPeriod - targetCourse.startPeriod + 1;
            const totalHeight = duration * CELL_HEIGHT;
            const baseColor = isCurrentWeekCourse ? targetCourse.color : (isDarkMode ? '#333333' : '#E0E0E0');
            const finalColor = setAlpha(baseColor, customSettings.courseOpacity ?? 0.85);

            return (
                <View style={[styles.courseBlockMerged, { backgroundColor: finalColor, height: totalHeight, zIndex: 999, borderColor: theme.background, borderWidth: 1 }]}>
                    <Text style={[styles.courseName, !isCurrentWeekCourse && { color: theme.subText, fontWeight: 'normal' }]} numberOfLines={2}>{targetCourse.name}</Text>
                    <Text style={[styles.courseLoc, !isCurrentWeekCourse && { color: theme.subText }]} numberOfLines={1}>{isCurrentWeekCourse ? targetCourse.classroom : '(非本周)'}</Text>
                </View>
            );
        }
        return null;
    };

    const BreakRow = ({ label, icon }: { label: string, icon: any }) => (
        <View style={[styles.breakRowContainer, { backgroundColor: getCardColor() }]}>
            <View style={[styles.breakSidebar, { width: SIDEBAR_WIDTH }]}>
                <Ionicons name={icon} size={14} color={theme.subText} />
                <Text style={{ fontSize: 9, color: theme.subText, marginTop: 1 }}>{label}</Text>
            </View>
            <View style={[styles.breakLineContainer, { borderColor: getBorderColor() }]}><View style={[styles.breakLine, { backgroundColor: getBorderColor() }]} /></View>
        </View>
    );

    const renderRows = () => {
        const rows: any[] = [];
        const cellColor = getCardColor();
        const borderColor = getBorderColor();

        periodList.forEach((periodId: number, index: number) => {
            const timeInfo = timeLayout.find((t: TimeSlot) => t.id === periodId);
            const rowZIndex = 100 - index;

            rows.push(
                <View key={`row-${periodId}`} style={[styles.rowContainer, { zIndex: rowZIndex }]}>
                    <View style={[styles.sidebarCell, { width: SIDEBAR_WIDTH, backgroundColor: cellColor, borderRightColor: borderColor }]}>
                        <Text style={[styles.sidebarNum, { color: theme.text }]}>{periodId}</Text>
                        <Text style={[styles.sidebarTime, { color: theme.subText }]}>{timeInfo ? timeInfo.startTime : ''}</Text>
                        <Text style={[styles.sidebarTime, { color: theme.subText }]}>{timeInfo ? timeInfo.endTime : ''}</Text>
                    </View>
                    {displayDays.map((_, i) => {
                        const realDayIndex = colIndexMapping[i];
                        let hideBottomBorder = false;

                        const coveringCourse = courseList.find(c =>
                            c.scheduleId === activeSchedule?.id && c.day === realDayIndex &&
                            c.startPeriod <= periodId && c.endPeriod > periodId
                        );
                        if (coveringCourse) hideBottomBorder = true;

                        return (
                            <TouchableOpacity
                                key={i}
                                style={[styles.gridCell, { borderColor: borderColor, backgroundColor: cellColor, borderBottomWidth: hideBottomBorder ? 0 : 1 }]}
                                onPress={(e) => {
                                    if (onCellPress) {
                                        // 🔥 核心修改：通过点击坐标计算实际节次
                                        // locationY 是点击位置相对于当前格子顶部的距离
                                        // CELL_HEIGHT 是每节课的标准高度 (60)
                                        const offsetY = e.nativeEvent.locationY;
                                        const periodOffset = Math.floor(offsetY / CELL_HEIGHT);
                                        // 实际点击节次 = 当前格子节次 + 偏移量
                                        onCellPress(realDayIndex, periodId + periodOffset);
                                    }
                                }}
                                activeOpacity={0.7}
                                disabled={!onCellPress}
                            >
                                {renderCellContent(realDayIndex, periodId)}
                            </TouchableOpacity>
                        );
                    })}
                </View>
            );
            if (periodId === periodConfig.morning && periodConfig.afternoon > 0) rows.push(<BreakRow key="break-lunch" label="午休" icon="restaurant-outline" />);
            if (periodId === (periodConfig.morning + periodConfig.afternoon) && periodConfig.evening > 0) rows.push(<BreakRow key="break-dinner" label="晚休" icon="moon-outline" />);
        });
        return rows;
    };

    return (
        <View style={{ width: SCREEN_WIDTH, flex: 1 }}>
            <View style={[styles.headerContainer, { backgroundColor: getCardColor(), borderBottomColor: getBorderColor(), borderBottomWidth: 1 }]}>
                <View style={{ width: SIDEBAR_WIDTH }} />
                {displayDays.map((day, index) => {
                    const realDayIndex = colIndexMapping[index];
                    const dateStr = getDateString(activeSchedule?.termStartDate, weekIndex, realDayIndex);
                    const today = new Date();
                    const isToday = (today.getMonth() + 1).toString().padStart(2, '0') + '/' + today.getDate().toString().padStart(2, '0') === dateStr;
                    return (
                        <View key={index} style={[styles.headerCell, isToday && { backgroundColor: theme.primary + '15', borderRadius: 4 }]}>
                            <Text style={[styles.headerText, { color: isToday ? theme.primary : theme.text }]}>{day}</Text>
                            <Text style={[styles.headerDate, { color: isToday ? theme.primary : theme.subText }]}>{dateStr}</Text>
                        </View>
                    );
                })}
            </View>
            <ScrollView style={styles.bodyScroll} showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 100 }}>
                {renderRows()}
            </ScrollView>
        </View>
    );
};

const styles = StyleSheet.create({
    headerContainer: { flexDirection: 'row', height: 50 },
    headerCell: { flex: 1, justifyContent: 'center', alignItems: 'center' },
    headerText: { fontSize: 13, fontWeight: 'bold' },
    headerDate: { fontSize: 10, marginTop: 2 },
    bodyScroll: { flex: 1 },
    rowContainer: { flexDirection: 'row', height: CELL_HEIGHT, zIndex: 1 },
    sidebarCell: { justifyContent: 'center', alignItems: 'center', borderRightWidth: 1, paddingVertical: 5 },
    sidebarNum: { fontSize: 14, fontWeight: 'bold' },
    sidebarTime: { fontSize: 10, marginTop: 2 },
    gridCell: { flex: 1, borderBottomWidth: 1, borderRightWidth: 1, padding: 0 },
    courseBlockMerged: {
        position: 'absolute', top: 0, left: 0, right: 0,
        borderRadius: 4, padding: 4, justifyContent: 'center', alignItems: 'center',
    },
    courseName: { fontSize: 11, color: '#fff', fontWeight: 'bold', textAlign: 'center' },
    courseLoc: { fontSize: 9, color: '#eee', textAlign: 'center' },
    breakRowContainer: { flexDirection: 'row', height: BREAK_HEIGHT, alignItems: 'center' },
    breakSidebar: { justifyContent: 'center', alignItems: 'center' },
    breakLineContainer: { flex: 1, height: '100%', justifyContent: 'center', paddingHorizontal: 10, borderLeftWidth: 1, borderBottomWidth: 1 },
    breakLine: { height: 1, width: '100%', opacity: 0.5 }
});

export default React.memo(ScheduleList);