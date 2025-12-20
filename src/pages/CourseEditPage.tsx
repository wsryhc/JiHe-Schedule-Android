// src/pages/CourseEditPage.tsx
import React, { useState, useLayoutEffect, useRef, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, StatusBar, PanResponder, TextInput as NativeTextInput, Keyboard } from 'react-native';
import { TextInput, Button, Portal, Modal, Surface, RadioButton } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule, Course } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';
// 尝试引入渐变库，如果没有安装则降级处理
let LinearGradient: any;
try { LinearGradient = require('expo-linear-gradient').LinearGradient; } catch (e) { }

// 1. 基础推荐色
const BASIC_COLORS = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#96CEB4', '#FFEEAD'];
const DEFAULT_CUSTOM_COLOR = '#9B59B6';
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日'];

// --- 颜色算法 ---
const hsvToRgb = (h: number, s: number, v: number) => {
    let r = 0, g = 0, b = 0;
    const i = Math.floor(h * 6);
    const f = h * 6 - i;
    const p = v * (1 - s);
    const q = v * (1 - f * s);
    const t = v * (1 - (1 - f) * s);
    switch (i % 6) {
        case 0: r = v; g = t; b = p; break;
        case 1: r = q; g = v; b = p; break;
        case 2: r = p; g = v; b = t; break;
        case 3: r = p; g = q; b = v; break;
        case 4: r = t; g = p; b = v; break;
        case 5: r = v; g = p; b = q; break;
    }
    return { r: Math.round(r * 255), g: Math.round(g * 255), b: Math.round(b * 255) };
};

// 辅助：生成 rgb 字符串
const hsvToRgbString = (h: number, s: number, v: number) => {
    const { r, g, b } = hsvToRgb(h, s, v);
    return `rgb(${r},${g},${b})`;
};

const rgbToHex = (r: number, g: number, b: number) => {
    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1).toUpperCase();
};

const hexToHsv = (hex: string) => {
    let r = 0, g = 0, b = 0;
    if (hex.startsWith('#')) hex = hex.slice(1);
    if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
    if (hex.length !== 6) return { h: 0, s: 1, v: 1 };

    r = parseInt(hex.substring(0, 2), 16) / 255;
    g = parseInt(hex.substring(2, 4), 16) / 255;
    b = parseInt(hex.substring(4, 6), 16) / 255;

    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    let h = 0, s = 0, v = max;
    const d = max - min;
    s = max === 0 ? 0 : d / max;
    if (max === min) h = 0;
    else {
        switch (max) {
            case r: h = (g - b) / d + (g < b ? 6 : 0); break;
            case g: h = (b - r) / d + 2; break;
            case b: h = (r - g) / d + 4; break;
        }
        h /= 6;
    }
    return { h, s, v };
};

export default function CourseEditPage({ route, navigation }: any) {
    const { theme } = useTheme();
    // 🔥 修改：解构出 courseList 用于冲突检测
    const { addCourse, updateCourse, deleteCourse, periodConfig, currentSchedule, courseList } = useSchedule();

    const totalPeriods = (periodConfig.morning || 0) + (periodConfig.afternoon || 0) + (periodConfig.evening || 0);
    const maxWeeks = currentSchedule?.totalWeeks || 25;

    const { course, initDay, initPeriod, scheduleId } = route.params || {};
    const isEdit = !!course;

    // 表单状态
    const [name, setName] = useState(course?.name || '');
    const [classroom, setClassroom] = useState(course?.classroom || '');
    const [teacher, setTeacher] = useState(course?.teacher || '');
    const [day, setDay] = useState<number>(course?.day !== undefined ? course.day : (initDay || 0));
    const [startPeriod, setStartPeriod] = useState<string>(String(course?.startPeriod || initPeriod || 1));
    const [endPeriod, setEndPeriod] = useState<string>(String(course?.endPeriod || initPeriod || 1));
    const [selectedWeeks, setSelectedWeeks] = useState<number[]>(course?.weeks || []);

    // 颜色逻辑
    const initialColor = course?.color || BASIC_COLORS[0];
    const isBasic = BASIC_COLORS.includes(initialColor);
    const [colorMode, setColorMode] = useState<'basic' | 'custom'>(isBasic ? 'basic' : 'custom');
    const [selectedBasicColor, setSelectedBasicColor] = useState(isBasic ? initialColor : BASIC_COLORS[0]);
    const [customColor, setCustomColor] = useState(isBasic ? DEFAULT_CUSTOM_COLOR : initialColor);

    // HSV 状态
    const [hue, setHue] = useState(0);
    const [sat, setSat] = useState(1);
    const [val, setVal] = useState(1);

    // 交互状态
    const [showColorPicker, setShowColorPicker] = useState(false);
    const [showPeriodModal, setShowPeriodModal] = useState(false);
    const [selectingType, setSelectingType] = useState<'start' | 'end'>('start');
    const [scrollEnabled, setScrollEnabled] = useState(true);

    // 布局常量
    const PICKER_SIZE = 240;
    const SLIDER_HEIGHT = 30;

    // 手势状态记录 (避免闭包陷阱)
    const gestureRef = useRef({ startX: 0, startY: 0 });

    useLayoutEffect(() => {
        navigation.setOptions({
            title: isEdit ? '编辑课程' : '添加课程',
            headerStyle: { backgroundColor: theme.background },
            headerTintColor: theme.text,
            headerShadowVisible: false,
        });
    }, [navigation, theme, isEdit]);

    const openColorPicker = () => {
        const { h, s, v } = hexToHsv(customColor);
        setHue(h); setSat(s); setVal(v);
        setColorMode('custom');
        setShowColorPicker(true);
    };

    const updateColorFromHsv = (h: number, s: number, v: number) => {
        const { r, g, b } = hsvToRgb(h, s, v);
        setCustomColor(rgbToHex(r, g, b));
    };

    // --- 1. 饱和度/亮度 触摸板手势 ---
    const hsvRef = useRef({ hue, sat, val });
    hsvRef.current = { hue, sat, val };

    const handleSVUpdateWithRef = (x: number, y: number) => {
        const s = Math.max(0, Math.min(1, x / PICKER_SIZE));
        const v = Math.max(0, Math.min(1, 1 - (y / PICKER_SIZE)));
        setSat(s); setVal(v);
        updateColorFromHsv(hsvRef.current.hue, s, v);
    };

    const svResponderReal = useRef(
        PanResponder.create({
            onStartShouldSetPanResponder: () => true,
            onMoveShouldSetPanResponder: () => true,
            onPanResponderGrant: (evt) => {
                setScrollEnabled(false);
                gestureRef.current.startX = evt.nativeEvent.locationX;
                gestureRef.current.startY = evt.nativeEvent.locationY;
                handleSVUpdateWithRef(gestureRef.current.startX, gestureRef.current.startY);
            },
            onPanResponderMove: (evt, gestureState) => {
                const newX = gestureRef.current.startX + gestureState.dx;
                const newY = gestureRef.current.startY + gestureState.dy;
                handleSVUpdateWithRef(newX, newY);
            },
            onPanResponderRelease: () => setScrollEnabled(true),
            onPanResponderTerminate: () => setScrollEnabled(true),
        })
    ).current;


    // --- 2. 色相条 手势 ---
    const hResponderReal = useRef(
        PanResponder.create({
            onStartShouldSetPanResponder: () => true,
            onMoveShouldSetPanResponder: () => true,
            onPanResponderGrant: (evt) => {
                setScrollEnabled(false);
                gestureRef.current.startX = evt.nativeEvent.locationX;
                handleHueUpdateWithRef(gestureRef.current.startX);
            },
            onPanResponderMove: (evt, gestureState) => {
                const newX = gestureRef.current.startX + gestureState.dx;
                handleHueUpdateWithRef(newX);
            },
            onPanResponderRelease: () => setScrollEnabled(true),
            onPanResponderTerminate: () => setScrollEnabled(true),
        })
    ).current;

    const handleHueUpdateWithRef = (x: number) => {
        const h = Math.max(0, Math.min(1, x / PICKER_SIZE));
        setHue(h);
        updateColorFromHsv(h, hsvRef.current.sat, hsvRef.current.val);
    };

    const handlePreset = (type: 'all' | 'odd' | 'even') => {
        let newWeeks: number[] = [];
        if (type === 'all') newWeeks = Array.from({ length: maxWeeks }, (_, i) => i + 1);
        if (type === 'odd') newWeeks = Array.from({ length: maxWeeks }, (_, i) => i + 1).filter(w => w % 2 !== 0);
        if (type === 'even') newWeeks = Array.from({ length: maxWeeks }, (_, i) => i + 1).filter(w => w % 2 === 0);
        setSelectedWeeks(newWeeks);
    };

    const toggleWeek = (week: number) => {
        if (selectedWeeks.includes(week)) setSelectedWeeks(selectedWeeks.filter(w => w !== week));
        else setSelectedWeeks([...selectedWeeks, week].sort((a, b) => a - b));
    };

    const handleSave = () => {
        if (!name.trim()) { Alert.alert('提示', '请输入课程名称'); return; }
        if (selectedWeeks.length === 0) { Alert.alert('提示', '请至少选择一个上课周'); return; }
        const sP = parseInt(startPeriod) || 1;
        const eP = parseInt(endPeriod) || 1;

        if (sP > eP) {
            Alert.alert('提示', '结束节次不能小于开始节次');
            return;
        }

        // 🔥🔥 核心：冲突检测逻辑
        const targetScheduleId = course?.scheduleId || scheduleId || currentSchedule?.id;

        if (targetScheduleId) {
            // 找出同一张课表里，除了自己以外的课程
            const conflicts = courseList.filter(c => {
                if (c.scheduleId !== targetScheduleId) return false;
                if (isEdit && c.id === course.id) return false; // 排除自己

                // 1. 检查星期是否相同
                if (c.day !== day) return false;

                // 2. 检查节次是否有交集 (start1 <= end2 && start2 <= end1)
                const isPeriodOverlap = (sP <= c.endPeriod) && (c.startPeriod <= eP);
                if (!isPeriodOverlap) return false;

                // 3. 检查周数是否有交集
                const isWeeksOverlap = c.weeks.some(w => selectedWeeks.includes(w));
                if (!isWeeksOverlap) return false;

                return true; // 命中冲突
            });

            if (conflicts.length > 0) {
                const conflictNames = conflicts.map(c =>
                    `• ${c.name} (第${c.startPeriod}-${c.endPeriod}节)`
                ).join('\n');

                Alert.alert(
                    '课程时间冲突',
                    `检测到该时间段与以下课程冲突：\n\n${conflictNames}\n\n请修改时间或周数后再保存。`,
                    [{ text: '去调整', style: 'cancel' }]
                );
                return; // ⛔ 阻止保存
            }
        }

        const finalColor = colorMode === 'basic' ? selectedBasicColor : customColor;
        const newData: Course = {
            id: course?.id || Date.now().toString(),
            scheduleId: course?.scheduleId || scheduleId,
            name, classroom, teacher, day,
            startPeriod: sP, endPeriod: eP,
            weeks: selectedWeeks, color: finalColor,
        };
        if (isEdit) updateCourse(newData);
        else addCourse(newData);
        navigation.goBack();
    };

    const handleDelete = () => {
        Alert.alert('确认删除', '确定要删除这门课程吗？', [
            { text: '取消', style: 'cancel' },
            { text: '删除', style: 'destructive', onPress: () => { deleteCourse(course.id); navigation.goBack(); } }
        ]);
    };

    const paperInputTheme = { colors: { primary: theme.primary, background: theme.background, onSurfaceVariant: theme.subText } };

    return (
        <View style={{ flex: 1 }}>
            <ScrollView
                style={[styles.container, { backgroundColor: theme.background }]}
                scrollEnabled={scrollEnabled}
            >
                <StatusBar barStyle={theme.dark ? 'light-content' : 'dark-content'} backgroundColor={theme.background} translucent={false} />

                {/* 1. 基本信息 */}
                <View style={styles.section}>
                    <TextInput label="课程名称 *" value={name} onChangeText={setName} mode="outlined" style={styles.input} textColor={theme.text} theme={paperInputTheme} />
                    <TextInput label="教室" value={classroom} onChangeText={setClassroom} mode="outlined" style={styles.input} textColor={theme.text} theme={paperInputTheme} />
                    <TextInput label="老师" value={teacher} onChangeText={setTeacher} mode="outlined" style={styles.input} textColor={theme.text} theme={paperInputTheme} />
                </View>

                {/* 2. 上课时间 (星期) */}
                <View style={styles.section}>
                    <Text style={[styles.label, { color: theme.subText }]}>上课时间</Text>
                    <View style={styles.weekDayContainer}>
                        {WEEKDAYS.map((d, index) => {
                            const isSelected = day === index;
                            return (
                                <TouchableOpacity
                                    key={index}
                                    activeOpacity={0.7}
                                    onPress={() => setDay(index)}
                                    style={[
                                        styles.weekDayCell,
                                        {
                                            backgroundColor: isSelected ? theme.primary : theme.card,
                                            borderColor: isSelected ? theme.primary : theme.border,
                                            borderWidth: 1
                                        }
                                    ]}
                                >
                                    <Text style={{ color: isSelected ? '#fff' : theme.text, fontWeight: isSelected ? 'bold' : 'normal', fontSize: 13 }}>{d}</Text>
                                </TouchableOpacity>
                            );
                        })}
                    </View>
                </View>

                {/* 3. 上课节次 */}
                <View style={styles.section}>
                    <Text style={[styles.label, { color: theme.subText }]}>上课节次</Text>
                    <View style={styles.row}>
                        <Text style={{ color: theme.text, marginRight: 10 }}>第</Text>
                        <TouchableOpacity onPress={() => { setSelectingType('start'); setShowPeriodModal(true); }}>
                            <TextInput mode="outlined" value={startPeriod} editable={false} style={[styles.smallInput, { textAlign: 'center' }]} textColor={theme.text} theme={paperInputTheme} />
                        </TouchableOpacity>
                        <Text style={{ color: theme.text, marginHorizontal: 10 }}>至</Text>
                        <TouchableOpacity onPress={() => { setSelectingType('end'); setShowPeriodModal(true); }}>
                            <TextInput mode="outlined" value={endPeriod} editable={false} style={[styles.smallInput, { textAlign: 'center' }]} textColor={theme.text} theme={paperInputTheme} />
                        </TouchableOpacity>
                        <Text style={{ color: theme.text, marginLeft: 10 }}>节</Text>
                    </View>
                </View>

                {/* 4. 上课周数 (垂直居中修复版) */}
                <View style={styles.section}>
                    <View style={styles.rowBetween}>
                        <Text style={[styles.label, { color: theme.subText }]}>上课周数 (共{maxWeeks}周)</Text>
                        <View style={{ flexDirection: 'row', gap: 10 }}>
                            <TouchableOpacity onPress={() => handlePreset('odd')}><Text style={{ color: theme.primary, fontSize: 13 }}>单周</Text></TouchableOpacity>
                            <TouchableOpacity onPress={() => handlePreset('even')}><Text style={{ color: theme.primary, fontSize: 13 }}>双周</Text></TouchableOpacity>
                            <TouchableOpacity onPress={() => handlePreset('all')}><Text style={{ color: theme.primary, fontSize: 13 }}>全选</Text></TouchableOpacity>
                        </View>
                    </View>

                    <View style={styles.weekGrid}>
                        {Array.from({ length: maxWeeks }, (_, i) => i + 1).map((week) => {
                            const isSelected = selectedWeeks.includes(week);
                            return (
                                <TouchableOpacity
                                    key={week}
                                    activeOpacity={0.7}
                                    onPress={() => toggleWeek(week)}
                                    style={[
                                        styles.weekItem,
                                        {
                                            backgroundColor: isSelected ? theme.primary : theme.card,
                                            borderColor: isSelected ? theme.primary : theme.border
                                        }
                                    ]}
                                >
                                    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
                                        <Text style={[
                                            {
                                                color: isSelected ? '#fff' : theme.text,
                                                fontSize: 13,
                                                textAlign: 'center',
                                                textAlignVertical: 'center', // Android 关键
                                                includeFontPadding: false,   // Android 关键：去除默认上下边距
                                                lineHeight: 18               // 锁定行高
                                            }
                                        ]}>
                                            {week}
                                        </Text>
                                    </View>
                                </TouchableOpacity>
                            );
                        })}
                    </View>
                </View>

                {/* 5. 课程颜色 */}
                <View style={styles.section}>
                    <Text style={[styles.label, { color: theme.subText }]}>课程颜色</Text>

                    <View style={[styles.colorRow, { marginBottom: 15 }]}>
                        {BASIC_COLORS.map(c => (
                            <TouchableOpacity
                                key={c}
                                onPress={() => { setColorMode('basic'); setSelectedBasicColor(c); }}
                                style={[
                                    styles.colorCircle,
                                    { backgroundColor: c },
                                    (colorMode === 'basic' && selectedBasicColor === c) && { borderWidth: 3, borderColor: theme.text, transform: [{ scale: 1.1 }] }
                                ]}
                            />
                        ))}
                    </View>

                    <TouchableOpacity
                        style={[styles.customColorRow, { backgroundColor: theme.card, borderColor: colorMode === 'custom' ? theme.primary : theme.border }]}
                        onPress={openColorPicker}
                        activeOpacity={0.8}
                    >
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <RadioButton.Android
                                value="custom"
                                status={colorMode === 'custom' ? 'checked' : 'unchecked'}
                                onPress={openColorPicker}
                                color={theme.primary}
                            />
                            <Text style={{ color: theme.text, marginLeft: 8 }}>自定义颜色</Text>
                        </View>
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Text style={{ color: theme.subText, fontSize: 12, marginRight: 8 }}>{customColor}</Text>
                            <View style={[styles.previewColorBox, { backgroundColor: customColor }]}>
                                <Ionicons name="color-palette" size={16} color="#fff" />
                            </View>
                        </View>
                    </TouchableOpacity>
                </View>

                <View style={{ marginTop: 10, marginBottom: 50 }}>
                    <Button mode="contained" onPress={handleSave} buttonColor={theme.primary} style={styles.btn}>保存</Button>
                    {isEdit && (
                        <Button mode="outlined" onPress={handleDelete} textColor="red" style={[styles.btn, { marginTop: 15, borderColor: 'red' }]}>删除课程</Button>
                    )}
                </View>
            </ScrollView>

            {/* 节次选择 */}
            <Portal>
                <Modal visible={showPeriodModal} onDismiss={() => setShowPeriodModal(false)} contentContainerStyle={[styles.modalContent, { backgroundColor: theme.card }]}>
                    <Text style={{ fontSize: 18, fontWeight: 'bold', marginBottom: 20, color: theme.text }}>选择节次</Text>
                    <View style={styles.modalGrid}>
                        {Array.from({ length: totalPeriods }, (_, i) => i + 1).map(num => (
                            <TouchableOpacity
                                key={num}
                                style={[
                                    styles.modalCell,
                                    {
                                        backgroundColor: theme.background, borderColor: theme.border,
                                        ...((selectingType === 'start' && parseInt(startPeriod) === num) || (selectingType === 'end' && parseInt(endPeriod) === num) ? { backgroundColor: theme.primary, borderColor: theme.primary } : {})
                                    }
                                ]}
                                onPress={() => {
                                    if (selectingType === 'start') {
                                        setStartPeriod(String(num));
                                        if (num > parseInt(endPeriod)) setEndPeriod(String(num));
                                    } else {
                                        setEndPeriod(String(num));
                                        if (num < parseInt(startPeriod)) setStartPeriod(String(num));
                                    }
                                    setShowPeriodModal(false);
                                }}
                            >
                                <Text style={{ color: ((selectingType === 'start' && parseInt(startPeriod) === num) || (selectingType === 'end' && parseInt(endPeriod) === num)) ? '#fff' : theme.text, fontWeight: 'bold' }}>{num}</Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </Modal>
            </Portal>

            {/* 🔥🔥 全新调色盘弹窗 */}
            <Portal>
                <Modal visible={showColorPicker} onDismiss={() => setShowColorPicker(false)} contentContainerStyle={{ padding: 20, alignItems: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 16, backgroundColor: theme.card, width: 280, alignItems: 'center' }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: theme.text }}>选取颜色</Text>

                        {/* 1. 饱和度/亮度 面板 */}
                        <View
                            style={{ width: PICKER_SIZE, height: PICKER_SIZE, borderRadius: 8, overflow: 'hidden', marginBottom: 15, borderColor: theme.border, borderWidth: 1 }}
                            {...svResponderReal.panHandlers}
                        >
                            {/* 🔥 修复：颜色字符串化 */}
                            <View style={{ ...StyleSheet.absoluteFillObject, backgroundColor: hsvToRgbString(hue, 1, 1) }} />

                            {LinearGradient ? (
                                <>
                                    <LinearGradient
                                        colors={['#FFF', 'transparent']}
                                        start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }}
                                        style={StyleSheet.absoluteFill}
                                    />
                                    <LinearGradient
                                        colors={['transparent', '#000']}
                                        start={{ x: 0, y: 0 }} end={{ x: 0, y: 1 }}
                                        style={StyleSheet.absoluteFill}
                                    />
                                </>
                            ) : (
                                <View style={{ flex: 1, backgroundColor: 'rgba(255,255,255,0.2)', justifyContent: 'center', alignItems: 'center' }}>
                                    <Text style={{ color: '#000', opacity: 0.5 }}>请安装渐变库</Text>
                                </View>
                            )}

                            {/* 选色圆圈 */}
                            <View style={{
                                position: 'absolute',
                                left: sat * PICKER_SIZE - 10,
                                top: (1 - val) * PICKER_SIZE - 10,
                                width: 20, height: 20, borderRadius: 10,
                                borderWidth: 2, borderColor: '#fff',
                                backgroundColor: customColor, // 这里的 customColor 已经是 HEX 字符串
                                shadowColor: '#000', shadowOpacity: 0.3, shadowRadius: 2, elevation: 5
                            }} pointerEvents="none" />
                        </View>

                        {/* 2. 色相条 */}
                        <View
                            style={{ width: PICKER_SIZE, height: SLIDER_HEIGHT, borderRadius: 10, overflow: 'hidden', marginBottom: 20, borderColor: theme.border, borderWidth: 1 }}
                            {...hResponderReal.panHandlers}
                        >
                            {LinearGradient ? (
                                <LinearGradient
                                    colors={['#F00', '#FF0', '#0F0', '#0FF', '#00F', '#F0F', '#F00']}
                                    start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }}
                                    style={{ flex: 1 }}
                                />
                            ) : (
                                <View style={{ flex: 1, backgroundColor: '#ccc' }} />
                            )}
                            {/* 色相滑块 */}
                            <View style={{
                                position: 'absolute', left: hue * PICKER_SIZE - 5, top: 0, bottom: 0,
                                width: 10, backgroundColor: '#fff', borderWidth: 1, borderColor: '#ccc', borderRadius: 5,
                                shadowColor: '#000', shadowOpacity: 0.3, elevation: 3
                            }} pointerEvents="none" />
                        </View>

                        {/* 3. 预览 & 确认 */}
                        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', width: '100%', marginBottom: 10 }}>
                            <View style={{ width: 40, height: 40, borderRadius: 8, backgroundColor: customColor, borderWidth: 1, borderColor: theme.border, marginRight: 10 }} />
                            <NativeTextInput
                                value={customColor}
                                onChangeText={(text) => {
                                    setCustomColor(text);
                                    const { h, s, v } = hexToHsv(text);
                                    setHue(h); setSat(s); setVal(v);
                                }}
                                style={{ flex: 1, height: 40, borderColor: theme.border, borderWidth: 1, borderRadius: 8, paddingHorizontal: 10, color: theme.text }}
                            />
                        </View>

                        <View style={{ flexDirection: 'row', gap: 10, width: '100%' }}>
                            <Button mode="outlined" onPress={() => setShowColorPicker(false)} style={{ flex: 1 }} textColor={theme.subText}>取消</Button>
                            <Button mode="contained" onPress={() => setShowColorPicker(false)} style={{ flex: 1 }} buttonColor={theme.primary}>确定</Button>
                        </View>
                    </Surface>
                </Modal>
            </Portal>

        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20 },
    section: { marginBottom: 20 },
    input: { marginBottom: 12, backgroundColor: 'transparent' },
    label: { fontSize: 14, marginBottom: 8, fontWeight: 'bold' },
    row: { flexDirection: 'row', alignItems: 'center' },
    rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 },
    smallInput: { width: 70, height: 40, backgroundColor: 'transparent' },

    weekDayContainer: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
    weekDayCell: { flex: 1, height: 36, justifyContent: 'center', alignItems: 'center', borderRadius: 8 },

    // 🔥 修复周数布局
    weekGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    weekItem: {
        width: '18%', // 一行5个左右
        aspectRatio: 1.2,
        justifyContent: 'center',
        alignItems: 'center',
        borderRadius: 8,
        borderWidth: 1,
    },

    colorRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 15 },
    colorCircle: { width: 35, height: 35, borderRadius: 17.5 },
    customColorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 8, borderRadius: 8, borderWidth: 1 },
    previewColorBox: { width: 40, height: 26, borderRadius: 4, justifyContent: 'center', alignItems: 'center' },

    btn: { paddingVertical: 4 },
    modalContent: { margin: 20, borderRadius: 16, alignItems: 'center', maxWidth: 340, alignSelf: 'center', width: '90%', padding: 20 },
    modalGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 10 },
    modalCell: { width: 45, height: 45, borderRadius: 22.5, justifyContent: 'center', alignItems: 'center', borderWidth: 1 },
});