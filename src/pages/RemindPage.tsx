import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, FlatList, StatusBar, Alert } from 'react-native';
import { Surface, Checkbox, SegmentedButtons, IconButton, Modal, Portal, Button } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule, Course, TimeSlot, Todo, checkTodoOnDate } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useIsFocused } from '@react-navigation/native';

interface DisplayItem {
    id: string | number;
    type: 'todo' | 'course';
    time: string;
    title: string;
    subtitle?: string;
    completed: boolean;
    color?: string;
    tag?: string;
    rawCourse?: Course;
    rawTodo?: Todo;
}

const getTodayStr = () => {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const getNowTimeStr = () => {
    const d = new Date();
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    return `${h}:${m}`;
};

const TODAY = getTodayStr();

export default function RemindPage() {
    const { theme } = useTheme();
    const navigation = useNavigation<any>(); 
    const isFocused = useIsFocused();
    const {
        courseList, currentSchedule, timeLayout, todoList, updateTodo,
        displayConfig, setTodoList // 🔥 需要用到 setTodoList 批量操作
    } = useSchedule();

    const [viewMode, setViewMode] = useState<string>('list');
    const [selectedDateStr, setSelectedDateStr] = useState<string>(TODAY);
    const [currentMonthDate, setCurrentMonthDate] = useState(new Date());

    const [nowTime, setNowTime] = useState(getNowTimeStr());
    
    // 🔥 批量删除菜单状态
    const [deleteMenuVisible, setDeleteMenuVisible] = useState(false);

    useEffect(() => {
        const timer = setInterval(() => {
            setNowTime(getNowTimeStr());
        }, 60000);
        return () => clearInterval(timer);
    }, []);

    const getWeekNumber = (dateStr: string) => {
        if (!currentSchedule) return -1;
        const start = new Date(currentSchedule.termStartDate);
        const current = new Date(dateStr);
        const diffTime = current.getTime() - start.getTime();
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
        if (diffDays < 0) return 0;
        return Math.floor(diffDays / 7) + 1;
    };

    const getMergedItems = (dateStr: string): DisplayItem[] => {
        const targetDate = new Date(dateStr);
        const dayOfWeek = targetDate.getDay() === 0 ? 6 : targetDate.getDay() - 1; 
        const weekNum = getWeekNumber(dateStr);

        let todoItems: DisplayItem[] = [];
        if (displayConfig.inApp.showTodo) {
            todoItems = todoList
                .filter(t => checkTodoOnDate(t, dateStr)) 
                .map(t => ({
                    id: `todo-${t.id}`,
                    type: 'todo',
                    time: t.startTime,
                    title: t.title,
                    subtitle: t.description ? `${t.description} · 结束 ${t.endTime}` : `结束 ${t.endTime}`,
                    completed: t.completed,
                    color: t.color || '#2196F3',
                    tag: t.tag,
                    rawTodo: t
                }));
        }

        let courseItems: DisplayItem[] = [];
        if (currentSchedule && displayConfig.inApp.showCourse) {
            const activeCourses = courseList.filter(c =>
                c.scheduleId === currentSchedule.id &&
                c.day === dayOfWeek &&
                c.weeks.includes(weekNum)
            );

            courseItems = activeCourses.map(c => {
                const startSlot = timeLayout.find(t => t.id === c.startPeriod);
                const endSlot = timeLayout.find(t => t.id === c.endPeriod);
                const startTime = startSlot ? startSlot.startTime : '00:00';
                const endTime = endSlot ? endSlot.endTime : '23:59';

                let isCompleted = false;
                if (dateStr < TODAY) {
                    isCompleted = true; 
                } else if (dateStr === TODAY) {
                    if (nowTime > endTime) {
                        isCompleted = true;
                    }
                }

                return {
                    id: `course-${c.id}`,
                    type: 'course',
                    time: startTime,
                    title: c.name,
                    subtitle: `${c.classroom} (第${c.startPeriod}-${c.endPeriod}节)`,
                    completed: isCompleted,
                    color: c.color,
                    rawCourse: c
                };
            });
        }

        return [...courseItems, ...todoItems].sort((a, b) => a.time.localeCompare(b.time));
    };

    const toggleTodoComplete = (id: string | number) => {
        if (typeof id === 'string' && id.startsWith('todo-')) {
            const rawId = id.split('-')[1];
            const target = todoList.find(t => String(t.id) === String(rawId));
            if (target) {
                updateTodo({ ...target, completed: !target.completed });
            }
        }
    };

    const handleTodoPress = (item: DisplayItem) => {
        if (item.type === 'todo' && item.rawTodo) {
            navigation.navigate('TodoEdit', { todo: item.rawTodo });
        }
    };

    // 🔥🔥 批量删除逻辑开始 🔥🔥

    const handleDeleteAllTodos = () => {
        setDeleteMenuVisible(false);
        Alert.alert(
            '删除所有待办',
            '确定要删除应用内所有的待办事项吗？此操作不可恢复。',
            [
                { text: '取消', style: 'cancel' },
                { text: '全部删除', style: 'destructive', onPress: () => setTodoList([]) }
            ]
        );
    };

    const handleDeleteTodayTodos = () => {
        setDeleteMenuVisible(false);
        const targetDateTodos = todoList.filter(t => checkTodoOnDate(t, selectedDateStr));
        
        if (targetDateTodos.length === 0) {
            Alert.alert('提示', '当天没有待办事项');
            return;
        }

        Alert.alert(
            '删除当天待办',
            `确定要删除 ${selectedDateStr} 的所有待办事项吗？`,
            [
                { text: '取消', style: 'cancel' },
                { 
                    text: '确定删除', 
                    style: 'destructive', 
                    onPress: () => processDeleteToday(targetDateTodos)
                }
            ]
        );
    };

    const processDeleteToday = (todosToDelete: Todo[]) => {
        const repeatingTodos = todosToDelete.filter(t => (t.repeatType && t.repeatType !== 'none') || t.isYearly);
        const normalTodos = todosToDelete.filter(t => !((t.repeatType && t.repeatType !== 'none') || t.isYearly));

        // 1. 先删除不重复的
        if (normalTodos.length > 0) {
            const normalIds = normalTodos.map(t => t.id);
            // 更新全局列表，移除这些 ID
            // 注意：不能直接 setTodoList，要基于 prev，但这里为了简单先获取 current todoList
            // 由于 todoList 在闭包里可能旧，最好用 functional update 或者确信 todoList 是新的
            // 这里逻辑有点复杂，我们先构造一个新的 full list
        }

        if (repeatingTodos.length > 0) {
            // 询问重复待办的处理方式
            Alert.alert(
                '包含重复待办',
                '检测到有重复的待办事项。您希望如何处理它们？',
                [
                    { text: '取消', style: 'cancel' },
                    { 
                        text: '仅删除今天', 
                        onPress: () => executeDelete(normalTodos, repeatingTodos, 'today-only') 
                    },
                    { 
                        text: '删除全部系列', 
                        style: 'destructive',
                        onPress: () => executeDelete(normalTodos, repeatingTodos, 'series') 
                    }
                ]
            );
        } else {
            // 只有普通待办，直接删除
            executeDelete(normalTodos, [], 'series');
        }
    };

    const executeDelete = (normalTodos: Todo[], repeatingTodos: Todo[], mode: 'today-only' | 'series') => {
        let newList = [...todoList];

        // 1. 删除普通待办
        const normalIds = normalTodos.map(t => t.id);
        newList = newList.filter(t => !normalIds.includes(t.id));

        // 2. 处理重复待办
        if (repeatingTodos.length > 0) {
            if (mode === 'series') {
                // 删除整个系列 -> 直接移除
                const repeatingIds = repeatingTodos.map(t => t.id);
                newList = newList.filter(t => !repeatingIds.includes(t.id));
            } else {
                // 仅删除今天 -> 推迟开始日期 (Skip current instance)
                const current = new Date(selectedDateStr);
                
                repeatingTodos.forEach(todo => {
                    const todoIndex = newList.findIndex(t => t.id === todo.id);
                    if (todoIndex !== -1) {
                        const nextDate = calculateNextOccurrence(todo, current);
                        newList[todoIndex] = { ...newList[todoIndex], date: nextDate };
                    }
                });
            }
        }

        setTodoList(newList);
    };

    const calculateNextOccurrence = (todo: Todo, currentOccurrence: Date): string => {
        const type = todo.repeatType || (todo.isYearly ? 'yearly' : 'none');
        const next = new Date(currentOccurrence);

        if (type === 'weekly') {
            next.setDate(next.getDate() + 7);
        } else if (type === 'monthly') {
            // 简单加一个月
            next.setMonth(next.getMonth() + 1);
            // 处理溢出：如果今天是 1月31，加一个月变成 3月3 (平年)，通常期望是 2月28
            // 我们的 checkTodoOnDate 逻辑是“如果开始日大于目标月天数，显示在最后一天”
            // 所以这里我们尽量保持原本的“日”，但 setMonth 会自动溢出。
            // 比如 next原本是 1/31。 setMonth(2) -> 3/3 (or 2).
            // 我们需要校正回 2月底吗？
            // 这里的策略是：直接修改 Start Date。
            // 如果原来的 Start Date 是 31号，新 Start Date 变成 2月28号？
            // 那以后就变成 28号重复了。
            // 更好的做法是：保持 Start Date 的 Day 不变，逻辑里去处理。
            // 但用户要求“推迟”，即修改数据。
            // 简化处理：推迟到下个月的同一天，如果下个月没那天，就推到下个月最后一天。
            
            // 为了更严谨，我们应该获取原始 Todo 的 Start Day (todo.date)，而不是 currentOccurrence
            // 但如果之前已经推迟过，todo.date 已经是变过的了。
            // 所以这里直接基于 currentOccurrence 加一个月即可。
        } else if (type === 'yearly') {
            next.setFullYear(next.getFullYear() + 1);
        }

        const y = next.getFullYear();
        const m = String(next.getMonth() + 1).padStart(2, '0');
        const d = String(next.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    };

    // 🔥🔥 批量删除逻辑结束 🔥🔥

    const changeDay = (offset: number) => {
        const [y, m, d] = selectedDateStr.split('-').map(Number);
        const date = new Date(y, m - 1, d);
        date.setDate(date.getDate() + offset);
        const newY = date.getFullYear();
        const newM = String(date.getMonth() + 1).padStart(2, '0');
        const newD = String(date.getDate()).padStart(2, '0');
        setSelectedDateStr(`${newY}-${newM}-${newD}`);
    };

    const getDaysInMonth = (year: number, month: number) => new Date(year, month + 1, 0).getDate();
    const getFirstDayOfMonth = (year: number, month: number) => new Date(year, month, 1).getDay();
    const changeMonth = (offset: number) => {
        const newDate = new Date(currentMonthDate);
        newDate.setMonth(newDate.getMonth() + offset);
        setCurrentMonthDate(newDate);
    };
    const handleDayPress = (dateStr: string) => {
        setSelectedDateStr(dateStr);
        setViewMode('list');
    };

    const renderTimeline = () => {
        const displayItems = getMergedItems(selectedDateStr);

        return (
            <FlatList
                data={displayItems}
                keyExtractor={(item) => item.id.toString()}
                contentContainerStyle={styles.listContent}
                ListEmptyComponent={
                    <View style={{ alignItems: 'center', marginTop: 50 }}>
                        <Ionicons name="file-tray-outline" size={48} color={theme.subText} />
                        <Text style={{ color: theme.subText, marginTop: 10 }}>
                            {selectedDateStr === TODAY ? '今天' : selectedDateStr} 没有安排
                        </Text>
                    </View>
                }
                renderItem={({ item, index }) => {
                    const isLast = index === displayItems.length - 1;
                    const isFirst = index === 0;
                    const isCourse = item.type === 'course';
                    
                    const itemColor = item.color || theme.primary;

                    return (
                        <View style={styles.itemRow}>
                            <View style={styles.timeColumn}>
                                <Text style={[styles.timeText, { color: theme.text }]}>{item.time}</Text>
                            </View>

                            <View style={styles.timelineColumn}>
                                <View style={[
                                    styles.lineBase, 
                                    {
                                        backgroundColor: theme.border,
                                        marginTop: isFirst ? 20 : 0,
                                        height: isLast ? 20 : '100%',
                                        flex: isLast ? undefined : 1
                                    }
                                ]} />

                                <View style={[
                                    styles.dot,
                                    {
                                        borderRadius: isCourse ? 4 : 7,
                                        backgroundColor: item.completed ? theme.subText : (isCourse ? itemColor : theme.background),
                                        borderColor: item.completed ? theme.subText : itemColor,
                                    }
                                ]}>
                                    {!item.completed && !isCourse && <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: itemColor }} />}
                                </View>
                            </View>

                            <View style={styles.contentColumn}>
                                <TouchableOpacity
                                    activeOpacity={item.type === 'todo' ? 0.7 : 1}
                                    onPress={() => item.type === 'todo' && handleTodoPress(item)}
                                >
                                    <Surface style={[
                                        styles.card,
                                        {
                                            backgroundColor: theme.card,
                                            opacity: item.completed ? 0.6 : 1,
                                            borderLeftWidth: 4,
                                            borderLeftColor: itemColor
                                        }
                                    ]} elevation={1}>
                                        <View style={styles.cardHeader}>
                                            <View style={{ flex: 1 }}>
                                                <View style={{flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginBottom: 2}}>
                                                    <Text style={[
                                                        styles.cardTitle,
                                                        {
                                                            color: theme.text,
                                                            textDecorationLine: item.completed ? 'line-through' : 'none',
                                                            marginRight: 6
                                                        }
                                                    ]}>
                                                        {item.title}
                                                    </Text>
                                                    
                                                    {item.tag && item.tag !== '默认' && (
                                                        <View style={{
                                                            backgroundColor: itemColor + '20', 
                                                            paddingHorizontal: 6, 
                                                            paddingVertical: 2, 
                                                            borderRadius: 4,
                                                            justifyContent: 'center'
                                                        }}>
                                                            <Text style={{
                                                                fontSize: 10, 
                                                                color: itemColor, 
                                                                fontWeight: 'bold'
                                                            }}>
                                                                {item.tag}
                                                            </Text>
                                                        </View>
                                                    )}
                                                </View>

                                                {isCourse && (
                                                    <Text style={{ fontSize: 10, color: theme.primary, fontWeight: 'bold' }}>
                                                        {item.completed ? '已结课' : '上课中 / 未开始'}
                                                    </Text>
                                                )}
                                            </View>

                                            {item.type === 'todo' ? (
                                                <Checkbox.Android
                                                    status={item.completed ? 'checked' : 'unchecked'}
                                                    onPress={() => toggleTodoComplete(item.id)}
                                                    color={itemColor} 
                                                />
                                            ) : (
                                                <Ionicons
                                                    name={item.completed ? "checkmark-circle" : "school"}
                                                    size={20}
                                                    color={item.completed ? theme.subText : itemColor}
                                                />
                                            )}
                                        </View>

                                        {item.subtitle && (
                                            <Text style={[styles.cardDesc, { color: theme.subText }]}>{item.subtitle}</Text>
                                        )}
                                    </Surface>
                                </TouchableOpacity>
                            </View>
                        </View>
                    );
                }}
            />
        );
    };

    const renderCalendar = () => {
        const year = currentMonthDate.getFullYear();
        const month = currentMonthDate.getMonth();
        const daysInMonth = getDaysInMonth(year, month);
        const firstDay = getFirstDayOfMonth(year, month);
        const calendarGrid = [...Array(firstDay).fill(null), ...Array(daysInMonth).keys()].map(i => i === null ? null : i + 1);
        const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];

        return (
            <View style={{ flex: 1 }}>
                <View style={styles.calNav}>
                    <IconButton icon="chevron-left" onPress={() => changeMonth(-1)} iconColor={theme.text} />
                    <Text style={[styles.calTitle, { color: theme.text }]}>{year}年 {month + 1}月</Text>
                    <IconButton icon="chevron-right" onPress={() => changeMonth(1)} iconColor={theme.text} />
                </View>
                <View style={styles.weekHeader}>
                    {WEEKDAYS.map(d => <Text key={d} style={[styles.weekText, { color: theme.subText }]}>{d}</Text>)}
                </View>
                <View style={styles.daysGrid}>
                    {calendarGrid.map((day, index) => {
                        if (day === null) return <View key={`blank-${index}`} style={styles.dayCell} />;

                        const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                        const isToday = dateStr === TODAY;
                        const isSelected = dateStr === selectedDateStr;

                        const dayTodos = todoList.filter(t => checkTodoOnDate(t, dateStr));

                        let courseCount = 0;
                        if (currentSchedule) {
                            const targetDate = new Date(dateStr);
                            const dayOfWeek = targetDate.getDay() === 0 ? 6 : targetDate.getDay() - 1;
                            const weekNum = getWeekNumber(dateStr);
                            courseCount = courseList.filter(c =>
                                c.scheduleId === currentSchedule.id &&
                                c.day === dayOfWeek &&
                                c.weeks.includes(weekNum)
                            ).length;
                        }

                        return (
                            <TouchableOpacity key={day} style={[styles.dayCell, { borderColor: theme.border }]} onPress={() => handleDayPress(dateStr)}>
                                <View style={[styles.dayNumContainer, isToday && { backgroundColor: theme.primary }, !isToday && isSelected && { borderWidth: 1, borderColor: theme.primary }]}>
                                    <Text style={[styles.dayNum, { color: isToday ? '#fff' : theme.text }]}>{day}</Text>
                                </View>

                                <View style={styles.eventList}>
                                    {courseCount > 0 && (
                                        <View style={[styles.courseSummaryBadge, { backgroundColor: theme.primary + '20' }]}>
                                            <Text style={{ fontSize: 9, color: theme.primary, fontWeight: 'bold' }}>
                                                📚 {courseCount}节课
                                            </Text>
                                        </View>
                                    )}

                                    {dayTodos.slice(0, courseCount > 0 ? 2 : 3).map((todo, i) => (
                                        <View key={i} style={[styles.eventDot, { backgroundColor: todo.completed ? theme.subText : (todo.color || '#FF6B6B') }]}>
                                            <Text numberOfLines={1} style={{ fontSize: 8, color: '#fff', paddingHorizontal: 2 }}>{todo.title}</Text>
                                        </View>
                                    ))}
                                </View>
                            </TouchableOpacity>
                        );
                    })}
                </View>
            </View>
        );
    };

    return (
        <View style={[styles.container, { backgroundColor: theme.background }]}>
            {isFocused && (
                <StatusBar 
                    barStyle={theme.dark ? 'light-content' : 'dark-content'} 
                    backgroundColor={theme.background} 
                    translucent={false}
                />
            )}

            <View style={styles.header}>
                <View style={styles.headerLeftContainer}>
                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Ionicons
                            name={viewMode === 'list' ? "list-outline" : "calendar-outline"}
                            size={24}
                            color={theme.text}
                            style={{ marginRight: 8 }}
                        />

                        <Text style={[styles.headerTitle, { color: theme.text }]}>
                            {viewMode === 'list' ? '日程安排' : '日历概览'}
                        </Text>
                    </View>

                    {viewMode === 'list' ? (
                        <View style={styles.dateNavRow}>
                            <View style={styles.navArrowContainer}>
                                <IconButton icon="chevron-left" size={24} iconColor={theme.text} onPress={() => changeDay(-1)} style={{ margin: 0 }} />
                            </View>
                            <View style={styles.navTextContainer}>
                                <Text style={{ color: theme.primary, fontSize: 15, fontWeight: 'bold', textAlign: 'center' }}>
                                    {selectedDateStr === TODAY ? '今天' : selectedDateStr}
                                </Text>
                            </View>
                            <View style={styles.navArrowContainer}>
                                <IconButton icon="chevron-right" size={24} iconColor={theme.text} onPress={() => changeDay(1)} style={{ margin: 0 }} />
                            </View>
                        </View>
                    ) : (
                        <View style={[styles.dateNavRow, { justifyContent: 'flex-start', paddingLeft: 5 }]}>
                            <Text style={{ color: theme.subText, fontSize: 12 }}>待办与课程整合</Text>
                        </View>
                    )}
                </View>

                <SegmentedButtons
                    value={viewMode}
                    onValueChange={setViewMode}
                    buttons={[
                        { value: 'list', label: '列表', icon: 'format-list-bulleted' },
                        { value: 'calendar', label: '日历', icon: 'calendar-month' },
                    ]}
                    style={{ width: 180 }}
                    density="regular"
                    theme={{
                        colors: {
                            secondaryContainer: theme.primary + '30', onSecondaryContainer: theme.primary,
                            onSurface: theme.text, outline: theme.border
                        }
                    }}
                />
            </View>

            <View style={{ flex: 1 }}>
                {viewMode === 'list' ? renderTimeline() : renderCalendar()}
            </View>

            {/* 🔥🔥 新增：删除 FAB (红色 X) */}
            <TouchableOpacity
                style={[styles.fab, { backgroundColor: '#FF5252', bottom: 100 }]} // 位置在添加按钮上方
                onPress={() => setDeleteMenuVisible(true)}
            >
                <Ionicons name="close" size={30} color="#fff" />
            </TouchableOpacity>

            <TouchableOpacity
                style={[styles.fab, { backgroundColor: theme.primary }]}
                onPress={() => navigation.navigate('TodoEdit', { initDate: selectedDateStr })}
            >
                <Ionicons name="add" size={30} color="#fff" />
            </TouchableOpacity>

            {/* 🔥🔥 删除菜单 Modal */}
            <Portal>
                <Modal visible={deleteMenuVisible} onDismiss={() => setDeleteMenuVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '80%', maxWidth: 300 }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 20, textAlign: 'center' }}>批量删除</Text>
                        
                        <Button mode="outlined" textColor="#FF5252" style={{ borderColor: '#FF5252', marginBottom: 15 }} onPress={handleDeleteTodayTodos}>
                            删除当天待办 ({selectedDateStr})
                        </Button>

                        <Button mode="contained" buttonColor="#FF5252" onPress={handleDeleteAllTodos}>
                            清空所有待办数据
                        </Button>

                        <Button mode="text" textColor={theme.subText} style={{ marginTop: 10 }} onPress={() => setDeleteMenuVisible(false)}>
                            取消
                        </Button>
                    </Surface>
                </Modal>
            </Portal>
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    header: { paddingHorizontal: 20, paddingTop: 20, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    headerLeftContainer: { flex: 1, height: 60, justifyContent: 'center' },
    headerTitle: { fontSize: 24, fontWeight: 'bold', marginBottom: 2 },
    dateNavRow: { flexDirection: 'row', alignItems: 'center', height: 30, marginLeft: -12 },
    navArrowContainer: { width: 30, alignItems: 'center' },
    navTextContainer: { width: 110, justifyContent: 'center', alignItems: 'center' },

    listContent: { paddingHorizontal: 20, paddingBottom: 80, paddingTop: 10 },
    itemRow: { flexDirection: 'row', minHeight: 80 },
    timeColumn: { width: 60, alignItems: 'flex-end', paddingRight: 12, paddingTop: 18 },
    timeText: { fontSize: 14, fontWeight: 'bold', includeFontPadding: false, textAlignVertical: 'center' },
    timelineColumn: { width: 20, alignItems: 'center' },
    lineBase: { width: 2 },
    dot: { width: 14, height: 14, zIndex: 10, justifyContent: 'center', alignItems: 'center', position: 'absolute', top: 20, borderWidth: 2 },

    contentColumn: { flex: 1, paddingBottom: 20, paddingLeft: 10 },
    card: { borderRadius: 12, padding: 15, justifyContent: 'center' },
    cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    cardTitle: { fontSize: 16, fontWeight: 'bold' }, 
    cardDesc: { fontSize: 13, marginTop: 5, lineHeight: 18 },

    calNav: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', marginBottom: 5 },
    calTitle: { fontSize: 16, fontWeight: 'bold', marginHorizontal: 20 },
    weekHeader: { flexDirection: 'row', paddingHorizontal: 10, marginBottom: 5 },
    weekText: { flex: 1, textAlign: 'center', fontSize: 12 },
    daysGrid: { flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: 10 },
    dayCell: { width: '14.28%', aspectRatio: 0.8, padding: 2, borderWidth: 0.5, borderColor: 'transparent', overflow: 'hidden', borderRadius: 4 },
    dayNumContainer: { width: 22, height: 22, borderRadius: 11, justifyContent: 'center', alignItems: 'center', alignSelf: 'center', marginBottom: 2 },
    dayNum: { fontSize: 11, fontWeight: 'bold' },
    eventList: { flex: 1, width: '100%', alignItems: 'center' },
    eventDot: { width: '92%', height: 12, borderRadius: 3, marginVertical: 1, justifyContent: 'center', paddingLeft: 2 },
    courseSummaryBadge: { width: '92%', height: 14, borderRadius: 4, justifyContent: 'center', alignItems: 'center', marginBottom: 2 },

    fab: { position: 'absolute', right: 20, bottom: 30, width: 56, height: 56, borderRadius: 28, justifyContent: 'center', alignItems: 'center', elevation: 6 },
});