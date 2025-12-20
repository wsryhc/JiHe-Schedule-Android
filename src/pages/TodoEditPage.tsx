// src/pages/TodoEditPage.tsx
import React, { useState, useLayoutEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity, Alert, Platform, StatusBar } from 'react-native';
import { TextInput, Button, Surface, Text, Portal, Modal, IconButton, Chip } from 'react-native-paper';
import DateTimePicker from '@react-native-community/datetimepicker';
import { useTheme } from '../context/ThemeContext';
import { useSchedule, Todo } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';

// --- 配置常量 ---
const PRESET_TAGS = [
    { label: '默认', value: 'default', icon: 'checkbox-blank-circle-outline', isSpecial: false },
    { label: '生日', value: 'birthday', icon: 'cake-variant', isSpecial: true },
    { label: '纪念日', value: 'anniversary', icon: 'heart', isSpecial: true },
    { label: '考试', value: 'exam', icon: 'school', isSpecial: false },
    { label: '会议', value: 'meeting', icon: 'briefcase', isSpecial: false },
    { label: '自定义', value: 'custom', icon: 'pencil', isSpecial: false },
];

const REPEAT_OPTIONS = [
    { label: '不重复', value: 'none' },
    { label: '每周重复', value: 'weekly' },
    { label: '每月重复', value: 'monthly' },
    { label: '每年重复', value: 'yearly' },
];

const COLOR_OPTIONS = ['#2196F3', '#F44336', '#E91E63', '#FF9800', '#4CAF50', '#9C27B0', '#607D8B'];

// 更新选项和上限
const REMINDER_UNITS = [
    { label: '分钟', value: 'minute' },
    { label: '小时', value: 'hour' },
    { label: '天', value: 'day' },
    { label: '周', value: 'week' },
    { label: '月', value: 'month' },
    { label: '年', value: 'year' },
];

const REMINDER_LIMITS: { [key: string]: number } = {
    minute: 60,
    hour: 24,
    day: 30,
    week: 4,
    month: 24,
    year: 3
};

// 辅助函数
const formatTime = (date: Date) => {
    const h = date.getHours().toString().padStart(2, '0');
    const m = date.getMinutes().toString().padStart(2, '0');
    return `${h}:${m}`;
};

const parseTime = (timeStr: string) => {
    if (!timeStr) return new Date();
    const [h, m] = timeStr.split(':').map(Number);
    const date = new Date();
    date.setHours(h || 0);
    date.setMinutes(m || 0);
    date.setSeconds(0);
    return date;
};

const formatDate = (d: Date) => d.toISOString().split('T')[0];

const calculateNextOccurrence = (todo: any, currentDateStr: string): string => {
    const type = todo.repeatType || (todo.isYearly ? 'yearly' : 'none');
    const current = new Date(currentDateStr);
    const next = new Date(current);
    if (type === 'weekly') next.setDate(next.getDate() + 7);
    else if (type === 'monthly') next.setMonth(next.getMonth() + 1);
    else if (type === 'yearly') next.setFullYear(next.getFullYear() + 1);
    const y = next.getFullYear();
    const m = String(next.getMonth() + 1).padStart(2, '0');
    const d = String(next.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
};

const getReminderMilliseconds = (val: number, unit: string) => {
    const minute = 60 * 1000;
    const hour = 60 * minute;
    const day = 24 * hour;
    switch (unit) {
        case 'minute': return val * minute;
        case 'hour': return val * hour;
        case 'day': return val * day;
        case 'week': return val * 7 * day;
        case 'month': return val * 30 * day;
        case 'year': return val * 365 * day;
        default: return 0;
    }
};

export default function TodoEditPage({ route, navigation }: any) {
    const { theme } = useTheme();
    const { addTodo, updateTodo, deleteTodo } = useSchedule();

    const { todo, initDate } = route.params || {};
    const isEdit = !!todo;

    const [title, setTitle] = useState(todo?.title || '');
    const [description, setDescription] = useState(todo?.description || '');
    const [dateStr, setDateStr] = useState(todo?.date || initDate || formatDate(new Date()));

    const [startTime, setStartTime] = useState(todo?.startTime || '12:00');
    const [endTime, setEndTime] = useState(todo?.endTime || '13:00');

    const [selectedTag, setSelectedTag] = useState(todo?.tagType || 'default');
    const [customTagName, setCustomTagName] = useState(todo?.tag === todo?.tagType ? '' : (todo?.tag || ''));
    const [selectedColor, setSelectedColor] = useState(todo?.color || COLOR_OPTIONS[0]);

    const [repeatType, setRepeatType] = useState<'none' | 'weekly' | 'monthly' | 'yearly'>(
        todo?.repeatType || (todo?.isYearly ? 'yearly' : 'none')
    );

    const [reminderValue, setReminderValue] = useState<string>(todo?.reminder ? String(todo.reminder.value) : '15');
    const [reminderUnit, setReminderUnit] = useState<string>(todo?.reminder ? todo.reminder.unit : 'minute');
    const [hasReminder, setHasReminder] = useState<boolean>(!!todo?.reminder);

    const [tempReminderValue, setTempReminderValue] = useState(reminderValue);
    const [tempReminderUnit, setTempReminderUnit] = useState(reminderUnit);
    const [tempHasReminder, setTempHasReminder] = useState(hasReminder);

    const [repeatModalVisible, setRepeatModalVisible] = useState(false);
    const [reminderModalVisible, setReminderModalVisible] = useState(false);
    const [showDateModal, setShowDateModal] = useState(false);
    const [calendarCursor, setCalendarCursor] = useState(new Date(dateStr));
    const [showTimePicker, setShowTimePicker] = useState(false);
    const [pickerMode, setPickerMode] = useState<'start' | 'end'>('start');
    const [tempDate, setTempDate] = useState(new Date());

    useLayoutEffect(() => {
        navigation.setOptions({
            title: isEdit ? '编辑事项' : '新建事项',
            headerTitleAlign: 'center',
            headerStyle: { backgroundColor: theme.background },
            headerTintColor: theme.text,
            headerShadowVisible: false,
        });
    }, [navigation, theme, isEdit]);

    const openReminderModal = () => {
        setTempReminderValue(reminderValue);
        setTempReminderUnit(reminderUnit);
        setTempHasReminder(hasReminder);
        setReminderModalVisible(true);
    };

    const handleTempReminderUnitChange = (unit: string) => {
        setTempReminderUnit(unit);
        const currentVal = parseInt(tempReminderValue, 10);
        const limit = REMINDER_LIMITS[unit];
        if (!isNaN(currentVal) && currentVal > limit) {
            setTempReminderValue(String(limit));
        }
        if (isNaN(currentVal) || currentVal <= 0) {
            setTempReminderValue('1');
        }
    };

    const handleConfirmReminder = () => {
        if (tempHasReminder && repeatType === 'none' && tempReminderValue) {
            const start = parseTime(startTime);
            const todoDate = new Date(dateStr);
            todoDate.setHours(start.getHours());
            todoDate.setMinutes(start.getMinutes());

            const offsetMs = getReminderMilliseconds(parseInt(tempReminderValue, 10), tempReminderUnit);
            const triggerTime = new Date(todoDate.getTime() - offsetMs);
            const now = new Date();

            if (triggerTime < now) {
                Alert.alert(
                    '提醒时间无效',
                    '您设置的提醒时间早于当前时间，请检查日期、时间或提前量。',
                    [{ text: '知道了', style: 'cancel' }]
                );
                return;
            }
        }

        setReminderValue(tempReminderValue);
        setReminderUnit(tempReminderUnit);
        setHasReminder(tempHasReminder);
        setReminderModalVisible(false);
    };

    const handleTagSelect = (tagValue: string) => {
        setSelectedTag(tagValue);
        const tagConfig = PRESET_TAGS.find(t => t.value === tagValue);
        if (tagConfig?.isSpecial) {
            setRepeatType('yearly');
            setStartTime('10:00');
            if (tagValue === 'birthday') setSelectedColor('#E91E63');
            if (tagValue === 'anniversary') setSelectedColor('#F44336');
        }
    };

    const getDaysInMonth = (y: number, m: number) => new Date(y, m + 1, 0).getDate();
    const getFirstDayOfMonth = (y: number, m: number) => new Date(y, m, 1).getDay();

    const renderCalendarGrid = () => {
        const year = calendarCursor.getFullYear();
        const month = calendarCursor.getMonth();
        const daysCount = getDaysInMonth(year, month);
        const firstDay = getFirstDayOfMonth(year, month);
        const blanks = Array(firstDay).fill(null);
        const days = Array.from({ length: daysCount }, (_, i) => i + 1);
        const grid = [...blanks, ...days];

        return (
            <View style={styles.calGrid}>
                {['日', '一', '二', '三', '四', '五', '六'].map(d => (
                    <Text key={d} style={{ width: '14.2%', textAlign: 'center', color: theme.subText, marginBottom: 5 }}>{d}</Text>
                ))}
                {grid.map((day, i) => {
                    if (!day) return <View key={i} style={{ width: '14.2%', aspectRatio: 1 }} />;
                    const thisDateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                    const isSelected = thisDateStr === dateStr;
                    return (
                        <TouchableOpacity
                            key={i}
                            style={[styles.calCell, isSelected && { backgroundColor: theme.primary }]}
                            onPress={() => { setDateStr(thisDateStr); setShowDateModal(false); }}
                        >
                            <Text style={{ color: isSelected ? '#fff' : theme.text, fontWeight: isSelected ? 'bold' : 'normal' }}>{day}</Text>
                        </TouchableOpacity>
                    )
                })}
            </View>
        );
    };

    const handleTimePress = (type: 'start' | 'end') => {
        setPickerMode(type);
        setTempDate(parseTime(type === 'start' ? startTime : endTime));
        setShowTimePicker(true);
    };

    const onPickerChange = (event: any, selectedDate?: Date) => {
        if (Platform.OS === 'android') setShowTimePicker(false);
        if (!selectedDate) return;
        setTempDate(selectedDate);
        if (Platform.OS === 'android') {
            const timeStr = formatTime(selectedDate);
            if (pickerMode === 'start') setStartTime(timeStr);
            else setEndTime(timeStr);
        }
    };

    const handleSave = () => {
        if (!title.trim()) { Alert.alert('提示', '请输入标题'); return; }

        const start = parseTime(startTime);
        const end = parseTime(endTime);
        if (end < start) { Alert.alert('提示', '结束时间不能早于开始时间'); return; }

        if (hasReminder && repeatType === 'none' && reminderValue) {
            const todoDate = new Date(dateStr);
            todoDate.setHours(start.getHours());
            todoDate.setMinutes(start.getMinutes());

            const offsetMs = getReminderMilliseconds(parseInt(reminderValue, 10), reminderUnit);
            const triggerTime = new Date(todoDate.getTime() - offsetMs);
            const now = new Date();

            if (triggerTime < now) {
                Alert.alert(
                    '提醒时间已过期',
                    '您设置的提醒时间早于当前时间，可能是因为您停留了太久。请重新调整。',
                    [{ text: '去调整', onPress: openReminderModal }, { text: '取消保存', style: 'cancel' }]
                );
                return;
            }
        }

        let finalTagName = '';
        if (selectedTag === 'custom') {
            finalTagName = customTagName || '自定义';
        } else {
            const tagObj = PRESET_TAGS.find(t => t.value === selectedTag);
            finalTagName = tagObj ? tagObj.label : '默认';
        }

        const newTodo: any = {
            id: todo?.id || Date.now().toString(),
            date: dateStr,
            startTime,
            endTime,
            title,
            description,
            completed: todo?.completed || false,
            tag: finalTagName,
            tagType: selectedTag,
            color: selectedColor,
            repeatType: repeatType,
            isYearly: repeatType === 'yearly',
            reminder: hasReminder ? { value: Number(reminderValue) || 0, unit: reminderUnit } : null
        };

        if (isEdit) updateTodo(newTodo);
        else addTodo(newTodo);
        navigation.goBack();
    };

    const handleDelete = () => {
        const isRepeating = (repeatType && repeatType !== 'none') || (todo && todo.isYearly);
        if (isRepeating) {
            Alert.alert('删除重复事项', '检测到这是一个重复事项，您希望如何删除？', [
                { text: '取消', style: 'cancel' },
                { text: '仅删除本次', onPress: () => confirmAction('delete-current') },
                { text: '删除所有将来', style: 'destructive', onPress: () => confirmAction('delete-all') }
            ]);
        } else {
            confirmAction('delete-all');
        }
    };

    const confirmAction = (action: 'delete-all' | 'delete-current') => {
        const msg = action === 'delete-all' ? '确定要永久删除此事项吗？' : '确定要删除本次待办（推迟到下个周期）吗？';
        Alert.alert('确认操作', msg, [
            { text: '取消', style: 'cancel' },
            {
                text: '确定', style: 'destructive',
                onPress: () => {
                    if (action === 'delete-all') deleteTodo(todo.id);
                    else {
                        const nextDate = calculateNextOccurrence({ ...todo, repeatType }, dateStr);
                        const updatedTodo = { ...todo, date: nextDate, repeatType: repeatType };
                        updateTodo(updatedTodo);
                    }
                    navigation.goBack();
                }
            }
        ]);
    };

    const getRepeatLabel = () => {
        const opt = REPEAT_OPTIONS.find(o => o.value === repeatType);
        return opt ? opt.label : '不重复';
    };

    const getReminderLabel = () => {
        if (!hasReminder) return '无提醒';
        const unitLabel = REMINDER_UNITS.find(u => u.value === reminderUnit)?.label;
        return `提前 ${reminderValue} ${unitLabel}`;
    };

    return (
        <ScrollView
            style={[styles.container, { backgroundColor: theme.background }]}
            contentContainerStyle={{ paddingBottom: 80 }}
        >
            <StatusBar barStyle={theme.dark ? 'light-content' : 'dark-content'} backgroundColor={theme.background} translucent={false} />

            <Surface style={[styles.card, { backgroundColor: theme.card }]} elevation={1}>
                <TextInput label="事项标题" value={title} onChangeText={setTitle} mode="outlined" style={styles.input} textColor={theme.text} theme={{ colors: { background: theme.card, primary: theme.primary } }} />
                <TextInput label="备注 / 描述" value={description} onChangeText={setDescription} mode="outlined" multiline numberOfLines={3} style={styles.input} textColor={theme.text} theme={{ colors: { background: theme.card, primary: theme.primary } }} />
            </Surface>

            <Surface style={[styles.card, { backgroundColor: theme.card, marginTop: 20 }]} elevation={1}>
                <Text style={{ color: theme.subText, marginBottom: 10, fontSize: 12 }}>事项类型</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 15 }}>
                    {PRESET_TAGS.map((tag) => {
                        const isSelected = selectedTag === tag.value;
                        return (
                            <Chip key={tag.value} selected={isSelected} showSelectedOverlay icon={tag.icon} onPress={() => handleTagSelect(tag.value)} style={{ backgroundColor: isSelected ? theme.primary + '20' : theme.background, borderColor: isSelected ? theme.primary : theme.border }} mode="outlined" textStyle={{ color: isSelected ? theme.primary : theme.subText }}>{tag.label}</Chip>
                        );
                    })}
                </View>
                {selectedTag === 'custom' && (
                    <TextInput label="输入自定义标签名称" value={customTagName} onChangeText={setCustomTagName} mode="outlined" dense style={styles.input} textColor={theme.text} theme={{ colors: { background: theme.card, primary: theme.primary } }} />
                )}
                <Text style={{ color: theme.subText, marginBottom: 10, fontSize: 12 }}>标记颜色</Text>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    {COLOR_OPTIONS.map((color) => {
                        const isSelected = selectedColor === color;
                        return (
                            <TouchableOpacity key={color} onPress={() => setSelectedColor(color)} style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: color, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: isSelected ? theme.text : 'transparent' }}>{isSelected && <Ionicons name="checkmark" size={20} color="white" />}</TouchableOpacity>
                        );
                    })}
                </View>
            </Surface>

            <Surface style={[styles.card, { backgroundColor: theme.card, marginTop: 20 }]} elevation={1}>
                <TouchableOpacity onPress={() => setShowDateModal(true)} style={styles.rowItem}>
                    <View>
                        <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 4 }}>日期</Text>
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Text style={{ color: theme.text, fontSize: 18, fontWeight: 'bold', marginRight: 5 }}>{dateStr}</Text>
                            <Ionicons name="calendar-outline" size={18} color={theme.primary} />
                        </View>
                    </View>

                    <TouchableOpacity style={{ alignItems: 'center', flexDirection: 'row', padding: 8 }} onPress={() => setRepeatModalVisible(true)}>
                        <Text style={{ color: repeatType !== 'none' ? theme.primary : theme.subText, marginRight: 8, fontSize: 14, fontWeight: 'bold' }}>{getRepeatLabel()}</Text>
                        <Ionicons name="repeat" size={20} color={repeatType !== 'none' ? theme.primary : theme.subText} />
                    </TouchableOpacity>
                </TouchableOpacity>

                <View style={{ height: 1, backgroundColor: theme.border, marginVertical: 15 }} />

                <Text style={{ color: theme.subText, marginBottom: 10, fontSize: 12 }}>时间段</Text>
                <View style={styles.timeRangeContainer}>
                    <TouchableOpacity style={[styles.timeBox, { backgroundColor: theme.background, borderColor: theme.border }]} onPress={() => handleTimePress('start')}>
                        <Text style={{ fontSize: 24, fontWeight: 'bold', color: theme.primary }}>{startTime}</Text>
                        <Text style={{ fontSize: 10, color: theme.subText }}>开始时间</Text>
                    </TouchableOpacity>
                    <Ionicons name="arrow-forward" size={20} color={theme.subText} />
                    <TouchableOpacity style={[styles.timeBox, { backgroundColor: theme.background, borderColor: theme.border }]} onPress={() => handleTimePress('end')}>
                        <Text style={{ fontSize: 24, fontWeight: 'bold', color: theme.text }}>{endTime}</Text>
                        <Text style={{ fontSize: 10, color: theme.subText }}>结束时间</Text>
                    </TouchableOpacity>
                </View>

                {/* 提醒时间设置行 */}
                <View style={{ height: 1, backgroundColor: theme.border, marginVertical: 15 }} />
                <TouchableOpacity onPress={openReminderModal} style={styles.rowItem}>
                    <View>
                        <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 4 }}>提醒</Text>
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Text style={{ color: hasReminder ? theme.primary : theme.text, fontSize: 16, fontWeight: 'bold', marginRight: 5 }}>
                                {getReminderLabel()}
                            </Text>
                        </View>
                    </View>
                    <Ionicons name={hasReminder ? "notifications" : "notifications-off-outline"} size={20} color={hasReminder ? theme.primary : theme.subText} />
                </TouchableOpacity>
            </Surface>

            <Button mode="contained" onPress={handleSave} style={{ marginTop: 30, marginBottom: 10 }} buttonColor={theme.primary} contentStyle={{ height: 50 }}>
                {isEdit ? '保存修改' : '创建事项'}
            </Button>

            {isEdit && (
                <Button mode="outlined" onPress={handleDelete} style={{ marginBottom: 30, borderColor: '#FF6B6B' }} textColor="#FF6B6B">删除事项</Button>
            )}

            {showTimePicker && (Platform.OS === 'android' ? (<DateTimePicker value={tempDate} mode="time" is24Hour={true} display="default" onChange={onPickerChange} />) : null)}

            <Portal>
                <Modal visible={showDateModal} onDismiss={() => setShowDateModal(false)} contentContainerStyle={[styles.modalBox, { backgroundColor: theme.card }]}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}>
                        <IconButton icon="chevron-left" onPress={() => { const d = new Date(calendarCursor); d.setMonth(d.getMonth() - 1); setCalendarCursor(d); }} />
                        <Text style={{ color: theme.text, fontSize: 16, fontWeight: 'bold' }}>{calendarCursor.getFullYear()}年 {calendarCursor.getMonth() + 1}月</Text>
                        <IconButton icon="chevron-right" onPress={() => { const d = new Date(calendarCursor); d.setMonth(d.getMonth() + 1); setCalendarCursor(d); }} />
                    </View>
                    {renderCalendarGrid()}
                </Modal>
            </Portal>

            {/* 重复规则弹窗 */}
            <Portal>
                <Modal visible={repeatModalVisible} onDismiss={() => setRepeatModalVisible(false)} contentContainerStyle={{ justifyContent: 'flex-end', flex: 1 }}>
                    <TouchableOpacity style={{ flex: 1 }} onPress={() => setRepeatModalVisible(false)} />
                    <Surface style={{ borderTopLeftRadius: 16, borderTopRightRadius: 16, backgroundColor: theme.card }}>
                        <View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'center' }}>
                            <Text style={{ fontSize: 16, fontWeight: 'bold', color: theme.text }}>重复规则</Text>
                        </View>
                        <View style={{ padding: 10 }}>
                            {REPEAT_OPTIONS.map((opt) => (
                                <TouchableOpacity key={opt.value} onPress={() => { setRepeatType(opt.value as any); setRepeatModalVisible(false); }} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 15, borderBottomWidth: 1, borderBottomColor: theme.border + '40' }}>
                                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                                        <Ionicons name={opt.value === 'none' ? 'remove-circle-outline' : 'repeat'} size={20} color={theme.subText} style={{ marginRight: 10 }} />
                                        <Text style={{ fontSize: 16, color: theme.text }}>{opt.label}</Text>
                                    </View>
                                    {repeatType === opt.value && <Ionicons name="checkmark" size={24} color={theme.primary} />}
                                </TouchableOpacity>
                            ))}
                        </View>
                        <Button onPress={() => setRepeatModalVisible(false)} style={{ margin: 10 }}>取消</Button>
                    </Surface>
                </Modal>
            </Portal>

            {/* 🔥 提醒设置弹窗：修复了布局塌陷和按钮跑偏的问题 */}
            <Portal>
                <Modal visible={reminderModalVisible} onDismiss={() => setReminderModalVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ borderRadius: 16, backgroundColor: theme.card, width: '85%', maxWidth: 350, overflow: 'hidden' }}>
                        <View style={{ padding: 20 }}>
                            <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>设置提醒</Text>

                            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
                                <Text style={{ color: theme.text }}>开启提醒</Text>
                                <Button mode={tempHasReminder ? 'contained' : 'outlined'} onPress={() => setTempHasReminder(!tempHasReminder)} buttonColor={tempHasReminder ? theme.primary : undefined} compact>{tempHasReminder ? '已开启' : '已关闭'}</Button>
                            </View>

                            {tempHasReminder && (
                                <View style={{ width: '100%' }}>
                                    {/* 1. 先选单位 */}
                                    <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 8 }}>选择单位</Text>
                                    <View style={{ gap: 10, marginBottom: 20 }}>
                                        <View style={{ flexDirection: 'row', gap: 8 }}>
                                            {REMINDER_UNITS.slice(0, 3).map(unit => (
                                                <Chip
                                                    key={unit.value}
                                                    selected={tempReminderUnit === unit.value}
                                                    onPress={() => handleTempReminderUnitChange(unit.value)}
                                                    style={{ backgroundColor: tempReminderUnit === unit.value ? theme.primary + '20' : theme.background, flex: 1 }}
                                                    textStyle={{ color: tempReminderUnit === unit.value ? theme.primary : theme.text, textAlign: 'center' }}
                                                    showSelectedOverlay={true}
                                                >
                                                    {unit.label}
                                                </Chip>
                                            ))}
                                        </View>
                                        <View style={{ flexDirection: 'row', gap: 8 }}>
                                            {REMINDER_UNITS.slice(3).map(unit => (
                                                <Chip
                                                    key={unit.value}
                                                    selected={tempReminderUnit === unit.value}
                                                    onPress={() => handleTempReminderUnitChange(unit.value)}
                                                    style={{ backgroundColor: tempReminderUnit === unit.value ? theme.primary + '20' : theme.background, flex: 1 }}
                                                    textStyle={{ color: tempReminderUnit === unit.value ? theme.primary : theme.text, textAlign: 'center' }}
                                                    showSelectedOverlay={true}
                                                >
                                                    {unit.label}
                                                </Chip>
                                            ))}
                                            {REMINDER_UNITS.slice(3).length < 3 && <View style={{ flex: 1 }} />}
                                        </View>
                                    </View>

                                    {/* 2. 再选数值 (网格) */}
                                    <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 10 }}>
                                        选择提前量 (最大{REMINDER_LIMITS[tempReminderUnit]})
                                    </Text>

                                    {/* 🔥 修复：增加 minHeight 和 padding 防止塌陷；内容少时也能撑开 */}
                                    <ScrollView style={{ maxHeight: 200, flexGrow: 0 }} contentContainerStyle={{ paddingBottom: 5 }}>
                                        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'flex-start' }}>
                                            {Array.from({ length: REMINDER_LIMITS[tempReminderUnit] }, (_, i) => i + 1).map((num) => {
                                                const isSelected = parseInt(tempReminderValue) === num;
                                                return (
                                                    <TouchableOpacity
                                                        key={num}
                                                        onPress={() => setTempReminderValue(String(num))}
                                                        style={{
                                                            width: 45, height: 45, borderRadius: 8,
                                                            // 🔥 修复：未选中时使用 theme.background，确保可视
                                                            backgroundColor: isSelected ? theme.primary : theme.background,
                                                            borderWidth: 1,
                                                            borderColor: isSelected ? theme.primary : theme.border,
                                                            justifyContent: 'center', alignItems: 'center'
                                                        }}
                                                    >
                                                        <Text style={{ color: isSelected ? '#fff' : theme.text, fontWeight: 'bold' }}>{num}</Text>
                                                    </TouchableOpacity>
                                                );
                                            })}
                                        </View>
                                    </ScrollView>
                                </View>
                            )}

                            <Button mode="contained" onPress={handleConfirmReminder} style={{ marginTop: 20 }} buttonColor={theme.primary}>确定</Button>
                        </View>
                    </Surface>
                </Modal>
            </Portal>

        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20 },
    card: { padding: 20, borderRadius: 16 },
    input: { marginBottom: 15, backgroundColor: 'transparent' },
    rowItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    timeRangeContainer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    timeBox: { flex: 1, alignItems: 'center', padding: 15, borderRadius: 12, borderWidth: 1, marginHorizontal: 5 },
    pickerModal: { backgroundColor: 'white', padding: 20, margin: 30, borderRadius: 12, alignSelf: 'center', minWidth: 300 },
    modalBox: { margin: 30, padding: 20, borderRadius: 12 },
    calGrid: { flexDirection: 'row', flexWrap: 'wrap' },
    calCell: { width: '14.2%', aspectRatio: 1, justifyContent: 'center', alignItems: 'center', borderRadius: 20 }
});