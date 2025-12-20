// src/components/ImportReviewModal.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, Platform, StatusBar } from 'react-native';
import { Portal, Modal, Surface, Button, IconButton, TextInput, Chip } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { Course } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';

interface ImportReviewModalProps {
    visible: boolean;
    onDismiss: () => void;
    onConfirm: (courses: Course[], totalWeeks: number) => void;
    initialCourses: any[];
    initialTotalWeeks: number;
    mode: 'json' | 'ocr';
}

const WEEK_DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

// 冲突检测算法
const detectConflicts = (courses: any[]) => {
    const conflictIds = new Set<string>();

    for (let i = 0; i < courses.length; i++) {
        for (let j = i + 1; j < courses.length; j++) {
            const a = courses[i];
            const b = courses[j];

            // 1. 星期相同
            if (a.day !== b.day) continue;

            // 2. 节次交叉 (StartA <= EndB && StartB <= EndA)
            const isPeriodOverlap = (a.startPeriod <= b.endPeriod) && (b.startPeriod <= a.endPeriod);
            if (!isPeriodOverlap) continue;

            // 3. 周次交叉
            const isWeekOverlap = a.weeks.some((w: number) => b.weeks.includes(w));

            if (isWeekOverlap) {
                conflictIds.add(a.id);
                conflictIds.add(b.id);
            }
        }
    }
    return conflictIds;
};

export function ImportReviewModal({ visible, onDismiss, onConfirm, initialCourses, initialTotalWeeks, mode }: ImportReviewModalProps) {
    const { theme, themeMode } = useTheme();

    const [courses, setCourses] = useState<any[]>([]);
    const [totalWeeks, setTotalWeeks] = useState(25);
    const [conflictIds, setConflictIds] = useState<Set<string>>(new Set());

    // --- 状态管理 ---
    // 1. 周数选择器
    const [weekSelectorVisible, setWeekSelectorVisible] = useState(false);
    const [editingWeeksId, setEditingWeeksId] = useState<string | null>(null);
    const [tempWeeks, setTempWeeks] = useState<number[]>([]);

    // 2. 总周数设置
    const [totalWeeksModalVisible, setTotalWeeksModalVisible] = useState(false);

    // 3. 时间编辑 (星期/节次)
    const [timeEditVisible, setTimeEditVisible] = useState(false);
    const [editingTimeId, setEditingTimeId] = useState<string | null>(null);
    const [tempDay, setTempDay] = useState(0);
    const [tempStartPeriod, setTempStartPeriod] = useState('1');
    const [tempEndPeriod, setTempEndPeriod] = useState('2');

    // 4. 🔥 新增：节次选择器状态
    const [periodSelectorVisible, setPeriodSelectorVisible] = useState(false);
    const [periodSelectorType, setPeriodSelectorType] = useState<'start' | 'end'>('start');

    useEffect(() => {
        if (visible) {
            // 初始化数据 & 清洗 OCR 文本
            const safeCourses = (initialCourses || []).map((c, index) => ({
                ...c,
                id: c.id || `temp-${index}-${Date.now()}`,
                name: c.name ? String(c.name).replace(/[\r\n]+/g, '').trim() : '',
                classroom: c.classroom ? String(c.classroom).replace(/[\r\n]+/g, '').trim() : '',
                teacher: c.teacher ? String(c.teacher).replace(/[\r\n]+/g, '').trim() : '',
                color: c.color || theme.primary,
                weeks: c.weeks || Array.from({ length: initialTotalWeeks }, (_, i) => i + 1),
                day: c.day ?? 0,
                startPeriod: c.startPeriod ?? 1,
                endPeriod: c.endPeriod ?? 2
            }));
            setCourses(safeCourses);
            setTotalWeeks(initialTotalWeeks || 25);
            setConflictIds(detectConflicts(safeCourses));
        }
    }, [visible, initialCourses, initialTotalWeeks]);

    const handleRemoveCourse = (id: string) => {
        const newCourses = courses.filter(c => c.id !== id);
        setCourses(newCourses);
        setConflictIds(detectConflicts(newCourses));
    };

    const handleConfirmImport = () => {
        const emptyName = courses.find(c => !c.name.trim());
        if (emptyName) {
            Alert.alert('提示', '所有课程名称不能为空，请检查。');
            return;
        }

        const currentConflicts = detectConflicts(courses);
        setConflictIds(currentConflicts);

        if (currentConflicts.size > 0) {
            Alert.alert(
                '存在时间冲突',
                '列表中仍有黄色的冲突课程。请修改上课时间或周数解决冲突，否则会导致课表显示异常。',
                [{ text: '去修改', style: 'cancel' }]
            );
        } else {
            onConfirm(courses, totalWeeks);
        }
    };

    // --- 逻辑：编辑周数 ---
    const openWeekSelector = (course: any) => {
        setEditingWeeksId(course.id);
        setTempWeeks(course.weeks || []);
        setWeekSelectorVisible(true);
    };

    const toggleWeek = (week: number) => {
        if (tempWeeks.includes(week)) {
            setTempWeeks(tempWeeks.filter(w => w !== week));
        } else {
            setTempWeeks([...tempWeeks, week].sort((a, b) => a - b));
        }
    };

    const handlePresetWeeks = (type: 'all' | 'odd' | 'even') => {
        let newWeeks: number[] = [];
        if (type === 'all') newWeeks = Array.from({ length: totalWeeks }, (_, i) => i + 1);
        if (type === 'odd') newWeeks = Array.from({ length: totalWeeks }, (_, i) => i + 1).filter(w => w % 2 !== 0);
        if (type === 'even') newWeeks = Array.from({ length: totalWeeks }, (_, i) => i + 1).filter(w => w % 2 === 0);
        setTempWeeks(newWeeks);
    };

    const saveWeeks = () => {
        if (tempWeeks.length === 0) {
            Alert.alert('提示', '请至少选择一个周');
            return;
        }
        const newCourses = courses.map(c =>
            c.id === editingWeeksId ? { ...c, weeks: tempWeeks } : c
        );
        setCourses(newCourses);
        setWeekSelectorVisible(false);
        setConflictIds(detectConflicts(newCourses));
    };

    // --- 逻辑：编辑时间 (周几/节次) ---
    const openTimeEditor = (course: any) => {
        setEditingTimeId(course.id);
        setTempDay(course.day);
        setTempStartPeriod(String(course.startPeriod));
        setTempEndPeriod(String(course.endPeriod));
        setTimeEditVisible(true);
    };

    // 🔥 打开节次选择网格
    const openPeriodGrid = (type: 'start' | 'end') => {
        setPeriodSelectorType(type);
        setPeriodSelectorVisible(true);
    };

    // 🔥 处理节次选择 (联动逻辑)
    const handlePeriodSelect = (period: number) => {
        const pStr = String(period);
        if (periodSelectorType === 'start') {
            setTempStartPeriod(pStr);
            // 如果开始时间 > 结束时间，自动把结束时间也设为当前时间
            if (period > parseInt(tempEndPeriod)) {
                setTempEndPeriod(pStr);
            }
        } else {
            setTempEndPeriod(pStr);
            // 如果结束时间 < 开始时间，自动把开始时间也设为当前时间
            if (period < parseInt(tempStartPeriod)) {
                setTempStartPeriod(pStr);
            }
        }
        setPeriodSelectorVisible(false);
    };

    const saveTime = () => {
        const s = parseInt(tempStartPeriod) || 1;
        const e = parseInt(tempEndPeriod) || 1;
        if (s > e) {
            Alert.alert('提示', '结束节次不能小于开始节次');
            return;
        }
        const newCourses = courses.map(c =>
            c.id === editingTimeId ? { ...c, day: tempDay, startPeriod: s, endPeriod: e } : c
        );
        setCourses(newCourses);
        setTimeEditVisible(false);
        setConflictIds(detectConflicts(newCourses));
    };

    const weekString = (weeks: number[]) => {
        if (weeks.length === totalWeeks) return "全周";
        if (weeks.length === 0) return "未设置";
        return weeks.length > 5 ? `${weeks.slice(0, 5).join(',')}...等${weeks.length}周` : weeks.join(',');
    };

    const inputTheme = { colors: { primary: theme.primary, background: theme.card, onSurface: theme.text, onSurfaceVariant: theme.subText } };

    return (
        <Portal>
            <Modal visible={visible} onDismiss={onDismiss} contentContainerStyle={{ flex: 1, backgroundColor: theme.background }}>
                <StatusBar
                    barStyle={theme.dark ? 'light-content' : 'dark-content'}
                    backgroundColor={theme.card}
                    translucent={false}
                />
                <View style={styles.container}>
                    {/* Header */}
                    <Surface style={[styles.header, { backgroundColor: theme.card }]} elevation={2}>
                        <View style={{ flex: 1 }}>
                            <Text style={[styles.title, { color: theme.text }]}>
                                {mode === 'json' ? '导入时发现时间冲突' : `校对识别结果 (${courses.length})`}
                            </Text>
                            <Text style={{ color: mode === 'json' ? '#FF9800' : theme.subText, fontSize: 12 }}>
                                {mode === 'json' ? '冲突课程已用黄色标出' : '请核对并修改识别内容，标黄为冲突课程'}
                            </Text>
                        </View>
                        <TouchableOpacity onPress={() => setTotalWeeksModalVisible(true)}>
                            <View style={{ alignItems: 'flex-end', paddingLeft: 10 }}>
                                <Text style={{ color: theme.subText, fontSize: 12 }}>当前上课周数</Text>
                                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                                    <Text style={{ color: theme.primary, fontSize: 20, fontWeight: 'bold' }}>{totalWeeks}</Text>
                                    <Ionicons name="pencil" size={14} color={theme.primary} style={{ marginLeft: 4 }} />
                                </View>
                            </View>
                        </TouchableOpacity>
                    </Surface>

                    {/* List */}
                    <ScrollView contentContainerStyle={{ padding: 15, paddingBottom: 100 }} keyboardShouldPersistTaps="handled">
                        {courses.map((item, index) => {
                            const isConflict = conflictIds.has(item.id);
                            return (
                                <Surface
                                    key={item.id}
                                    style={[
                                        styles.card,
                                        {
                                            backgroundColor: isConflict ? '#FFF9C4' : theme.card,
                                            borderColor: isConflict ? '#FFC107' : theme.border,
                                            borderWidth: isConflict ? 2 : 1
                                        }
                                    ]}
                                    elevation={1}
                                >
                                    <View style={styles.cardHeader}>
                                        <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 4 }}>课程名称 *</Text>
                                        <View style={{ flexDirection: 'row', alignItems: 'flex-start' }}>
                                            <TextInput
                                                mode="outlined"
                                                value={item.name}
                                                onChangeText={(text) => {
                                                    const newCourses = [...courses];
                                                    newCourses[index].name = text;
                                                    setCourses(newCourses);
                                                }}
                                                style={[styles.mainInput, { flex: 1 }]}
                                                dense
                                                textColor={theme.text}
                                                theme={inputTheme}
                                            />
                                            <IconButton
                                                icon="trash-can-outline"
                                                size={20}
                                                iconColor={theme.subText}
                                                onPress={() => handleRemoveCourse(item.id)}
                                                style={{ marginTop: 6 }}
                                            />
                                        </View>
                                    </View>

                                    <View style={{ flexDirection: 'row', gap: 10, marginBottom: 10 }}>
                                        <View style={{ flex: 1 }}>
                                            <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 4 }}>教室</Text>
                                            <TextInput
                                                mode="outlined"
                                                value={item.classroom}
                                                onChangeText={(text) => {
                                                    const newCourses = [...courses];
                                                    newCourses[index].classroom = text;
                                                    setCourses(newCourses);
                                                }}
                                                style={styles.subInput}
                                                dense
                                                textColor={theme.text}
                                                theme={inputTheme}
                                            />
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 4 }}>老师</Text>
                                            <TextInput
                                                mode="outlined"
                                                value={item.teacher}
                                                onChangeText={(text) => {
                                                    const newCourses = [...courses];
                                                    newCourses[index].teacher = text;
                                                    setCourses(newCourses);
                                                }}
                                                style={styles.subInput}
                                                dense
                                                textColor={theme.text}
                                                theme={inputTheme}
                                            />
                                        </View>
                                    </View>

                                    <View style={{ flexDirection: 'row', gap: 10 }}>
                                        <TouchableOpacity
                                            style={[styles.actionBtn, { borderColor: theme.border, flex: 1 }]}
                                            onPress={() => openTimeEditor(item)}
                                        >
                                            <Ionicons name="time-outline" size={16} color={theme.primary} style={{ marginRight: 6 }} />
                                            <Text style={{ color: theme.text, fontSize: 13 }}>
                                                {WEEK_DAYS[item.day]} {item.startPeriod}-{item.endPeriod}节
                                            </Text>
                                        </TouchableOpacity>

                                        <TouchableOpacity
                                            style={[styles.actionBtn, { borderColor: theme.border, flex: 1 }]}
                                            onPress={() => openWeekSelector(item)}
                                        >
                                            <Ionicons name="calendar-outline" size={16} color={theme.primary} style={{ marginRight: 6 }} />
                                            <Text style={{ color: theme.text, fontSize: 13 }} numberOfLines={1}>
                                                {weekString(item.weeks)}
                                            </Text>
                                        </TouchableOpacity>
                                    </View>
                                </Surface>
                            );
                        })}
                    </ScrollView>

                    {/* Bottom Actions */}
                    <Surface style={[styles.footer, { backgroundColor: theme.card }]} elevation={4}>
                        <Button mode="outlined" onPress={onDismiss} style={{ flex: 1, marginRight: 10 }} textColor={theme.subText}>取消</Button>
                        <Button mode="contained" onPress={handleConfirmImport} style={{ flex: 2 }} buttonColor={theme.primary}>
                            确认导入 ({courses.length})
                        </Button>
                    </Surface>

                    {/* 1. 总周数 Modal */}
                    <Portal>
                        <Modal visible={totalWeeksModalVisible} onDismiss={() => setTotalWeeksModalVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                            <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '85%', maxWidth: 320, maxHeight: '80%' }}>
                                <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>设置总周数</Text>
                                <ScrollView style={{ maxHeight: 300 }}>
                                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'center' }}>
                                        {Array.from({ length: 30 }, (_, i) => i + 16).map((w) => (
                                            <TouchableOpacity
                                                key={w}
                                                onPress={() => { setTotalWeeks(w); setTotalWeeksModalVisible(false); }}
                                                style={[styles.weekOption, { backgroundColor: totalWeeks === w ? theme.primary : theme.background, borderColor: totalWeeks === w ? theme.primary : theme.border }]}
                                            >
                                                <Text style={[styles.weekOptionText, { color: totalWeeks === w ? '#fff' : theme.text }]}>{w}</Text>
                                            </TouchableOpacity>
                                        ))}
                                    </View>
                                </ScrollView>
                                <Button onPress={() => setTotalWeeksModalVisible(false)} style={{ marginTop: 10 }}>取消</Button>
                            </Surface>
                        </Modal>
                    </Portal>

                    {/* 2. 单课周数选择 Modal */}
                    <Portal>
                        <Modal visible={weekSelectorVisible} onDismiss={() => setWeekSelectorVisible(false)} contentContainerStyle={{ justifyContent: 'flex-end', flex: 1 }}>
                            <TouchableOpacity style={{ flex: 1 }} onPress={() => setWeekSelectorVisible(false)} />
                            <Surface style={{ borderTopLeftRadius: 16, borderTopRightRadius: 16, backgroundColor: theme.card, padding: 20 }}>
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}>
                                    <Text style={{ fontSize: 16, fontWeight: 'bold', color: theme.text }}>选择上课周数</Text>
                                    <View style={{ flexDirection: 'row', gap: 10 }}>
                                        <TouchableOpacity onPress={() => handlePresetWeeks('odd')}><Text style={{ color: theme.primary }}>单周</Text></TouchableOpacity>
                                        <TouchableOpacity onPress={() => handlePresetWeeks('even')}><Text style={{ color: theme.primary }}>双周</Text></TouchableOpacity>
                                        <TouchableOpacity onPress={() => handlePresetWeeks('all')}><Text style={{ color: theme.primary }}>全选</Text></TouchableOpacity>
                                    </View>
                                </View>

                                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'center', marginBottom: 20 }}>
                                    {Array.from({ length: totalWeeks }, (_, i) => i + 1).map((w) => {
                                        const isSelected = tempWeeks.includes(w);
                                        return (
                                            <TouchableOpacity
                                                key={w}
                                                onPress={() => toggleWeek(w)}
                                                style={[styles.weekOption, { width: '13%', backgroundColor: isSelected ? theme.primary : theme.background, borderColor: isSelected ? theme.primary : theme.border }]}
                                            >
                                                <Text style={[styles.weekOptionText, { color: isSelected ? '#fff' : theme.text, fontSize: 12 }]}>{w}</Text>
                                            </TouchableOpacity>
                                        )
                                    })}
                                </View>
                                <Button mode="contained" onPress={saveWeeks} buttonColor={theme.primary}>确定</Button>
                            </Surface>
                        </Modal>
                    </Portal>

                    {/* 3. 时间编辑 Modal (星期/节次) */}
                    <Portal>
                        <Modal visible={timeEditVisible} onDismiss={() => setTimeEditVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                            <Surface style={{ padding: 20, borderRadius: 16, backgroundColor: theme.card, width: '90%', maxWidth: 350 }}>
                                <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 20, textAlign: 'center' }}>修改上课时间</Text>

                                <Text style={{ color: theme.subText, marginBottom: 8 }}>星期</Text>
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 20 }}>
                                    {WEEK_DAYS.map((d, i) => (
                                        <TouchableOpacity
                                            key={i}
                                            onPress={() => setTempDay(i)}
                                            style={[
                                                styles.weekOption,
                                                { width: 36, height: 36, borderRadius: 18, backgroundColor: tempDay === i ? theme.primary : theme.background, borderColor: tempDay === i ? theme.primary : theme.border }
                                            ]}
                                        >
                                            <Text style={[styles.weekOptionText, { color: tempDay === i ? '#fff' : theme.text, fontSize: 12 }]}>{d.replace('周', '')}</Text>
                                        </TouchableOpacity>
                                    ))}
                                </View>

                                <Text style={{ color: theme.subText, marginBottom: 8 }}>节次范围</Text>
                                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginBottom: 25 }}>
                                    <Text style={{ fontSize: 16, color: theme.text, marginRight: 10 }}>第</Text>
                                    {/* 🔥 修改：改为点击选择模式 */}
                                    <TouchableOpacity onPress={() => openPeriodGrid('start')}>
                                        <View pointerEvents="none">
                                            <TextInput
                                                mode="outlined"
                                                value={tempStartPeriod}
                                                style={{ width: 60, height: 40, backgroundColor: theme.background, textAlign: 'center' }}
                                                textColor={theme.text}
                                                theme={inputTheme}
                                            />
                                        </View>
                                    </TouchableOpacity>

                                    <Text style={{ fontSize: 16, color: theme.text, marginHorizontal: 10 }}>至</Text>

                                    <TouchableOpacity onPress={() => openPeriodGrid('end')}>
                                        <View pointerEvents="none">
                                            <TextInput
                                                mode="outlined"
                                                value={tempEndPeriod}
                                                style={{ width: 60, height: 40, backgroundColor: theme.background, textAlign: 'center' }}
                                                textColor={theme.text}
                                                theme={inputTheme}
                                            />
                                        </View>
                                    </TouchableOpacity>
                                    <Text style={{ fontSize: 16, color: theme.text, marginLeft: 10 }}>节</Text>
                                </View>

                                <View style={{ flexDirection: 'row', gap: 10 }}>
                                    <Button mode="outlined" onPress={() => setTimeEditVisible(false)} style={{ flex: 1 }} textColor={theme.subText}>取消</Button>
                                    <Button mode="contained" onPress={saveTime} style={{ flex: 1 }} buttonColor={theme.primary}>确定</Button>
                                </View>
                            </Surface>
                        </Modal>
                    </Portal>

                    {/* 🔥 4. 新增：节次选择器 Modal (1-12) */}
                    <Portal>
                        <Modal visible={periodSelectorVisible} onDismiss={() => setPeriodSelectorVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                            <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '85%', maxWidth: 320 }}>
                                <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>
                                    选择{periodSelectorType === 'start' ? '开始' : '结束'}节次
                                </Text>
                                <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 10 }}>
                                    {/* 🔥 修改：从18改为12 */}
                                    {Array.from({ length: 12 }, (_, i) => i + 1).map(num => (
                                        <TouchableOpacity
                                            key={num}
                                            style={{
                                                width: 45, height: 45, borderRadius: 22.5,
                                                backgroundColor: ((periodSelectorType === 'start' && num === parseInt(tempStartPeriod)) || (periodSelectorType === 'end' && num === parseInt(tempEndPeriod))) ? theme.primary : theme.background,
                                                borderWidth: 1,
                                                borderColor: theme.border,
                                                justifyContent: 'center', alignItems: 'center'
                                            }}
                                            onPress={() => handlePeriodSelect(num)}
                                        >
                                            <Text style={{
                                                color: ((periodSelectorType === 'start' && num === parseInt(tempStartPeriod)) || (periodSelectorType === 'end' && num === parseInt(tempEndPeriod))) ? '#fff' : theme.text,
                                                fontWeight: 'bold'
                                            }}>
                                                {num}
                                            </Text>
                                        </TouchableOpacity>
                                    ))}
                                </View>
                                <Button onPress={() => setPeriodSelectorVisible(false)} style={{ marginTop: 15 }} textColor={theme.subText}>取消</Button>
                            </Surface>
                        </Modal>
                    </Portal>

                </View>
            </Modal>
        </Portal>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    header: { padding: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: 'rgba(0,0,0,0.05)' },
    title: { fontSize: 18, fontWeight: 'bold', marginBottom: 4 },
    footer: { padding: 20, flexDirection: 'row', borderTopWidth: 1, borderTopColor: 'rgba(0,0,0,0.05)' },

    card: { borderRadius: 12, marginBottom: 15, padding: 15 },
    cardHeader: { marginBottom: 10 },
    mainInput: { height: 45, fontSize: 16, backgroundColor: 'transparent' },
    subInput: { height: 40, backgroundColor: 'transparent', fontSize: 14 },

    actionBtn: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
        paddingVertical: 10, paddingHorizontal: 5, borderRadius: 8,
        borderWidth: 1, backgroundColor: 'rgba(0,0,0,0.02)'
    },

    weekOption: {
        width: 45, height: 45, borderRadius: 8, borderWidth: 1,
        justifyContent: 'center', alignItems: 'center'
    },
    weekOptionText: {
        fontWeight: 'bold',
        textAlign: 'center',
        textAlignVertical: 'center',
        includeFontPadding: false
    }
});