// src/pages/RemindPage.tsx
import React, { useState, useEffect, useMemo, useLayoutEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, FlatList, StatusBar, ImageBackground, Platform, Alert } from 'react-native';
import { Surface, Checkbox, SegmentedButtons, IconButton, Modal, Portal, Button } from 'react-native-paper';
import { useTheme, setAlpha } from '../context/ThemeContext';
import { useSchedule, Course, Todo, checkTodoOnDate } from '../context/ScheduleContext';
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
    const { theme, customSettings } = useTheme();
    const navigation = useNavigation<any>();
    const isFocused = useIsFocused();
    const {
        courseList, currentSchedule, timeLayout, todoList, updateTodo,
        displayConfig, setTodoList
    } = useSchedule();

    const [viewMode, setViewMode] = useState<string>('list');
    const [selectedDateStr, setSelectedDateStr] = useState<string>(TODAY);
    const [currentMonthDate, setCurrentMonthDate] = useState(new Date());

    const [nowTime, setNowTime] = useState(getNowTimeStr());
    const [deleteMenuVisible, setDeleteMenuVisible] = useState(false);

    useLayoutEffect(() => {
        navigation.setOptions({
            headerShown: false,
        });
    }, [navigation]);

    useEffect(() => {
        const timer = setInterval(() => {
            setNowTime(getNowTimeStr());
        }, 60000);
        return () => clearInterval(timer);
    }, []);

    const shouldForceWhite = customSettings.remindBackgroundImage && customSettings.remindForceWhiteContent;
    const dynamicTheme = useMemo(() => {
        if (shouldForceWhite) {
            return { ...theme, text: '#FFFFFF', subText: 'rgba(255, 255, 255, 0.85)' };
        }
        return theme;
    }, [theme, shouldForceWhite]);

    // 自定义 Header
    const CustomHeader = () => {
        const isTransparent = customSettings.remindBackgroundImage && customSettings.remindTransparentHeader;
        const backgroundColor = isTransparent ? 'transparent' : theme.card;

        const statusBarHeight = Platform.OS === 'android' ? (StatusBar.currentHeight || 24) : 44;

        return (
            <View style={{
                width: '100%',
                backgroundColor: backgroundColor,
                paddingTop: statusBarHeight,
                zIndex: 100,
                elevation: isTransparent ? 0 : 2,
            }}>
                <View style={{
                    height: 44,
                    width: '100%',
                    justifyContent: 'center',
                    alignItems: 'center',
                }}>
                    <Text style={{
                        fontSize: 18,
                        fontWeight: 'bold',
                        color: dynamicTheme.text
                    }}>待办</Text>
                </View>
            </View>
        );
    };

    const RootContainer = ({ children }: any) => {
        let statusBarStyle: 'light-content' | 'dark-content' = theme.dark ? 'light-content' : 'dark-content';
        const isTransparentHeader = customSettings.remindBackgroundImage && customSettings.remindTransparentHeader;

        if (shouldForceWhite || isTransparentHeader) {
            statusBarStyle = 'light-content';
        }

        if (customSettings.remindBackgroundImage) {
            return (
                <ImageBackground
                    source={{ uri: customSettings.remindBackgroundImage }}
                    style={{ flex: 1 }}
                    imageStyle={{ opacity: customSettings.remindBgImageOpacity ?? 1 }}
                    resizeMode="cover"
                >
                    {isFocused && <StatusBar barStyle={statusBarStyle} backgroundColor="transparent" translucent={true} />}
                    <CustomHeader />
                    <View style={{ flex: 1 }}>{children}</View>
                </ImageBackground>
            );
        }
        return (
            <View style={{ flex: 1, backgroundColor: theme.background }}>
                {isFocused && <StatusBar barStyle={statusBarStyle} backgroundColor={theme.background} />}
                <CustomHeader />
                <View style={{ flex: 1 }}>{children}</View>
            </View>
        );
    };

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
        if (targetDateTodos.length === 0) { Alert.alert('提示', '当天没有待办事项'); return; }
        Alert.alert(
            '删除当天待办',
            `确定要删除 ${selectedDateStr} 的所有待办事项吗？`,
            [{ text: '取消', style: 'cancel' }, { text: '确定删除', style: 'destructive', onPress: () => processDeleteToday(targetDateTodos) }]
        );
    };

    const processDeleteToday = (todosToDelete: Todo[]) => {
        const repeatingTodos = todosToDelete.filter(t => (t.repeatType && t.repeatType !== 'none') || t.isYearly);
        const normalTodos = todosToDelete.filter(t => !((t.repeatType && t.repeatType !== 'none') || t.isYearly));

        if (repeatingTodos.length > 0) {
            Alert.alert(
                '包含重复待办',
                '检测到有重复的待办事项。您希望如何处理它们？',
                [
                    { text: '取消', style: 'cancel' },
                    { text: '仅删除今天', onPress: () => executeDelete(normalTodos, repeatingTodos, 'today-only') },
                    { text: '删除全部系列', style: 'destructive', onPress: () => executeDelete(normalTodos, repeatingTodos, 'series') }
                ]
            );
        } else {
            executeDelete(normalTodos, [], 'series');
        }
    };

    const executeDelete = (normalTodos: Todo[], repeatingTodos: Todo[], mode: 'today-only' | 'series') => {
        let newList = [...todoList];
        const normalIds = normalTodos.map(t => t.id);
        newList = newList.filter(t => !normalIds.includes(t.id));

        if (repeatingTodos.length > 0) {
            if (mode === 'series') {
                const repeatingIds = repeatingTodos.map(t => t.id);
                newList = newList.filter(t => !repeatingIds.includes(t.id));
            } else {
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
        if (type === 'weekly') next.setDate(next.getDate() + 7);
        else if (type === 'monthly') next.setMonth(next.getMonth() + 1);
        else if (type === 'yearly') next.setFullYear(next.getFullYear() + 1);
        const y = next.getFullYear();
        const m = String(next.getMonth() + 1).padStart(2, '0');
        const d = String(next.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    };

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
                        <Ionicons name="file-tray-outline" size={48} color={dynamicTheme.subText} />
                        <Text style={{ color: dynamicTheme.subText, marginTop: 10 }}>
                            {selectedDateStr === TODAY ? '今天' : selectedDateStr} 没有安排
                        </Text>
                    </View>
                }
                renderItem={({ item, index }) => {
                    const isLast = index === displayItems.length - 1;
                    const isFirst = index === 0;
                    const isCourse = item.type === 'course';

                    const itemColor = item.color || theme.primary;

                    // 事项卡片背景色逻辑
                    const cardBaseColor = shouldForceWhite ? '#000000' : theme.card;
                    const cardOpacity = customSettings.remindItemOpacity ?? 0.85;
                    const finalCardBg = setAlpha(cardBaseColor, cardOpacity);

                    return (
                        <View style={styles.itemRow}>
                            <View style={styles.timeColumn}>
                                <Text style={[styles.timeText, { color: dynamicTheme.text }]}>{item.time}</Text>
                            </View>

                            <View style={styles.timelineColumn}>
                                <View style={[
                                    styles.lineBase,
                                    {
                                        backgroundColor: shouldForceWhite ? 'rgba(255,255,255,0.3)' : theme.border,
                                        marginTop: isFirst ? 20 : 0,
                                        height: isLast ? 20 : '100%',
                                        flex: isLast ? undefined : 1
                                    }
                                ]} />

                                <View style={[
                                    styles.dot,
                                    {
                                        borderRadius: isCourse ? 4 : 7,
                                        backgroundColor: item.completed ? dynamicTheme.subText : (isCourse ? itemColor : (shouldForceWhite ? 'transparent' : theme.background)),
                                        borderColor: item.completed ? dynamicTheme.subText : itemColor,
                                        borderWidth: 2
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
                                            backgroundColor: finalCardBg,
                                            opacity: item.completed ? 0.6 : 1,
                                            borderLeftWidth: 4,
                                            borderLeftColor: itemColor
                                        }
                                    ]} elevation={1}>
                                        <View style={styles.cardHeader}>
                                            <View style={{ flex: 1 }}>
                                                <View style={{ flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginBottom: 2 }}>
                                                    <Text style={[
                                                        styles.cardTitle,
                                                        {
                                                            color: shouldForceWhite ? '#FFFFFF' : theme.text,
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
                                            <Text style={[styles.cardDesc, { color: shouldForceWhite ? 'rgba(255,255,255,0.7)' : theme.subText }]}>{item.subtitle}</Text>
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
                    <IconButton icon="chevron-left" onPress={() => changeMonth(-1)} iconColor={dynamicTheme.text} />
                    <Text style={[styles.calTitle, { color: dynamicTheme.text }]}>{year}年 {month + 1}月</Text>
                    <IconButton icon="chevron-right" onPress={() => changeMonth(1)} iconColor={dynamicTheme.text} />
                </View>
                <View style={styles.weekHeader}>
                    {WEEKDAYS.map(d => <Text key={d} style={[styles.weekText, { color: dynamicTheme.subText }]}>{d}</Text>)}
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

                        // 🔥 2. 逻辑修正：
                        // 如果开启强制白色 or 深色模式 -> 用黑色填充背景 (对比文字白)
                        // 否则 (普通浅色模式) -> 用白色填充背景 (对比文字黑)
                        const useDarkBacking = shouldForceWhite || theme.dark;
                        const cellBaseColor = useDarkBacking ? '#000000' : '#FFFFFF';

                        const cellOpacity = customSettings.remindCalendarCellOpacity ?? 0.1;
                        const cellBg = setAlpha(cellBaseColor, cellOpacity);

                        return (
                            <TouchableOpacity
                                key={day}
                                style={[
                                    styles.dayCell,
                                    {
                                        borderColor: shouldForceWhite ? 'rgba(255,255,255,0.2)' : theme.border,
                                        backgroundColor: cellBg
                                    }
                                ]}
                                onPress={() => handleDayPress(dateStr)}
                            >
                                <View style={[styles.dayNumContainer, isToday && { backgroundColor: theme.primary }, !isToday && isSelected && { borderWidth: 1, borderColor: theme.primary }]}>
                                    <Text style={[styles.dayNum, { color: isToday ? '#fff' : dynamicTheme.text }]}>{day}</Text>
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
                                        <View key={i} style={[styles.eventDot, { backgroundColor: todo.completed ? dynamicTheme.subText : (todo.color || '#FF6B6B') }]}>
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
        <RootContainer>
            {/* 内容区的导航栏背景色设为透明，让背景图透出来 */}
            <View style={[styles.header, { backgroundColor: 'transparent' }]}>
                <View style={styles.headerLeftContainer}>
                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Ionicons
                            name={viewMode === 'list' ? "list-outline" : "calendar-outline"}
                            size={24}
                            color={dynamicTheme.text}
                            style={{ marginRight: 8 }}
                        />

                        <Text style={[styles.headerTitle, { color: dynamicTheme.text }]}>
                            {viewMode === 'list' ? '日程安排' : '日历概览'}
                        </Text>
                    </View>

                    {viewMode === 'list' ? (
                        <View style={styles.dateNavRow}>
                            <View style={styles.navArrowContainer}>
                                <IconButton icon="chevron-left" size={24} iconColor={dynamicTheme.text} onPress={() => changeDay(-1)} style={{ margin: 0 }} />
                            </View>
                            <View style={styles.navTextContainer}>
                                <Text style={{ color: theme.primary, fontSize: 15, fontWeight: 'bold', textAlign: 'center' }}>
                                    {selectedDateStr === TODAY ? '今天' : selectedDateStr}
                                </Text>
                            </View>
                            <View style={styles.navArrowContainer}>
                                <IconButton icon="chevron-right" size={24} iconColor={dynamicTheme.text} onPress={() => changeDay(1)} style={{ margin: 0 }} />
                            </View>
                        </View>
                    ) : (
                        <View style={[styles.dateNavRow, { justifyContent: 'flex-start', paddingLeft: 5 }]}>
                            <Text style={{ color: dynamicTheme.subText, fontSize: 12 }}>待办与课程整合</Text>
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
                            onSurface: dynamicTheme.text, outline: shouldForceWhite ? 'rgba(255,255,255,0.3)' : theme.border
                        }
                    }}
                />
            </View>

            <View style={{ flex: 1 }}>
                {viewMode === 'list' ? renderTimeline() : renderCalendar()}
            </View>

            <TouchableOpacity
                style={[styles.fab, { backgroundColor: '#FF5252', bottom: 100 }]}
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
        </RootContainer>
    );
}

const styles = StyleSheet.create({
    customHeader: {
        width: '100%',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: 10,
        paddingBottom: 5,
    },
    customHeaderTitle: {
        fontSize: 18,
        fontWeight: 'bold',
    },
    header: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 10, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
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