// src/pages/TimeSettingPage.tsx
import React, { useState, useLayoutEffect, createElement } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Platform, Alert, StatusBar } from 'react-native';
import { Button, Portal, Modal, FAB, TouchableRipple, Surface } from 'react-native-paper';
import DateTimePicker from '@react-native-community/datetimepicker'; 
import { useTheme } from '../context/ThemeContext';
import { useSchedule, TimeSlot } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';

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

const addMinutes = (timeStr: string, minutes: number) => {
    const date = parseTime(timeStr);
    date.setMinutes(date.getMinutes() + minutes);
    return formatTime(date);
};

export default function TimeSettingPage({ navigation }: any) {
    const { theme } = useTheme();
    const { timeLayout, updateTimeLayout, periodConfig } = useSchedule();

    // 默认配置 (存储字符串)
    const [duration, setDuration] = useState('45'); 
    const [breakTime, setBreakTime] = useState('10'); 

    // 通用选择器状态
    const [pickerModalVisible, setPickerModalVisible] = useState(false);
    const [pickerTitle, setPickerTitle] = useState('');
    const [pickerOptions, setPickerOptions] = useState<number[]>([]);
    const [onPickerSelect, setOnPickerSelect] = useState<(val: string) => void>(() => {});

    // 时间选择器状态
    const [showTimePicker, setShowTimePicker] = useState(false);
    const [pickerMode, setPickerMode] = useState<'start' | 'end'>('start'); 
    const [currentEditId, setCurrentEditId] = useState<number | null>(null); 
    const [tempDate, setTempDate] = useState(new Date()); 

    useLayoutEffect(() => {
        navigation.setOptions({
            title: '作息时间设置',
            headerStyle: { backgroundColor: theme.background },
            headerTintColor: theme.text,
        });
    }, [navigation, theme]);

    // 🔥 改进：滚动选择器 (1-120)
    const openNumberPicker = (title: string, currentVal: string, onConfirm: (val: string) => void) => {
        setPickerTitle(title);
        setPickerOptions(Array.from({ length: 120 }, (_, i) => i + 1));
        setOnPickerSelect(() => onConfirm);
        setPickerModalVisible(true);
    };

    const updateTimeChain = (startId: number, newStartTime: string) => {
        const defaultBrk = parseInt(breakTime) || 10;
        let newLayout = [...timeLayout];
        const startIndex = newLayout.findIndex(t => t.id === startId);
        if (startIndex === -1) return;

        let limitIndex = -1;
        if (startId <= periodConfig.morning) limitIndex = periodConfig.morning;
        else if (startId <= periodConfig.morning + periodConfig.afternoon) limitIndex = periodConfig.morning + periodConfig.afternoon;
        else limitIndex = 999;

        let currentStartStr = newStartTime;

        for (let i = startIndex; i < newLayout.length; i++) {
            const slot = newLayout[i];
            if (slot.id > limitIndex) break;

            const oldStartObj = parseTime(slot.startTime);
            const oldEndObj = parseTime(slot.endTime);
            let slotDurationMinutes = (oldEndObj.getTime() - oldStartObj.getTime()) / 1000 / 60;

            if (slotDurationMinutes <= 0 || isNaN(slotDurationMinutes)) {
                slotDurationMinutes = parseInt(duration) || 45;
            }

            const newEndStr = addMinutes(currentStartStr, slotDurationMinutes);
            newLayout[i] = { ...slot, startTime: currentStartStr, endTime: newEndStr };
            currentStartStr = addMinutes(newEndStr, defaultBrk);
        }
        updateTimeLayout(newLayout);
    };

    const handleTimePress = (id: number, type: 'start' | 'end', timeStr: string) => {
        setCurrentEditId(id);
        setPickerMode(type);
        setTempDate(parseTime(timeStr));
        setShowTimePicker(true);
    };

    const onTimePickerChange = (event: any, selectedDate?: Date) => {
        if (Platform.OS === 'android') setShowTimePicker(false);
        if (!selectedDate || !currentEditId) return;
        setTempDate(selectedDate);
        const timeStr = formatTime(selectedDate);
        if (Platform.OS === 'android') {
            if (pickerMode === 'start') updateTimeChain(currentEditId, timeStr);
            else {
                const newLayout = timeLayout.map(t => t.id === currentEditId ? { ...t, endTime: timeStr } : t);
                updateTimeLayout(newLayout);
            }
        }
    };

    const confirmTimePicker = () => {
        const timeStr = formatTime(tempDate);
        if (pickerMode === 'start' && currentEditId) updateTimeChain(currentEditId, timeStr);
        else if (currentEditId) {
            const newLayout = timeLayout.map(t => t.id === currentEditId ? { ...t, endTime: timeStr } : t);
            updateTimeLayout(newLayout);
        }
        setShowTimePicker(false);
    };

    const handleApplyAll = () => {
        Alert.alert('应用设置', '将使用当前设置的「时长」和「休息」重新计算所有课程时间，确定吗？', [
            { text: '取消', style: 'cancel' },
            {
                text: '确定', onPress: () => {
                    const dur = parseInt(duration) || 45;
                    const brk = parseInt(breakTime) || 10;
                    
                    let tempLayout = [...timeLayout];
                    
                    if (periodConfig.morning > 0) {
                        let startT = tempLayout.find(t=>t.id===1)?.startTime || '08:00';
                        for (let i = 1; i <= periodConfig.morning; i++) {
                            const endT = addMinutes(startT, dur);
                            const exist = tempLayout.find(t=>t.id===i);
                            if(exist) Object.assign(exist, { startTime: startT, endTime: endT });
                            else tempLayout.push({ id: i, startTime: startT, endTime: endT });
                            startT = addMinutes(endT, brk);
                        }
                    }
                    
                    const pmStartId = periodConfig.morning + 1;
                    if (periodConfig.afternoon > 0) {
                        let startT = tempLayout.find(t=>t.id===pmStartId)?.startTime || '14:00';
                        for (let i = pmStartId; i < pmStartId + periodConfig.afternoon; i++) {
                            const endT = addMinutes(startT, dur);
                            const exist = tempLayout.find(t=>t.id===i);
                            if(exist) Object.assign(exist, { startTime: startT, endTime: endT });
                            else tempLayout.push({ id: i, startTime: startT, endTime: endT });
                            startT = addMinutes(endT, brk);
                        }
                    }

                    const eveStartId = periodConfig.morning + periodConfig.afternoon + 1;
                    if (periodConfig.evening > 0) {
                        let startT = tempLayout.find(t=>t.id===eveStartId)?.startTime || '19:00';
                        for (let i = eveStartId; i < eveStartId + periodConfig.evening; i++) {
                            const endT = addMinutes(startT, dur);
                            const exist = tempLayout.find(t=>t.id===i);
                            if(exist) Object.assign(exist, { startTime: startT, endTime: endT });
                            else tempLayout.push({ id: i, startTime: startT, endTime: endT });
                            startT = addMinutes(endT, brk);
                        }
                    }
                    tempLayout.sort((a,b) => a.id - b.id);
                    updateTimeLayout([...tempLayout]);
                    Alert.alert('完成', '时间表已更新');
                }
            }
        ]);
    };

    const renderRow = (item: TimeSlot) => (
        <View key={item.id} style={[styles.row, { borderBottomColor: theme.border }]}>
            <View style={[styles.periodBadge, { backgroundColor: theme.card }]}>
                <Text style={{ fontWeight: 'bold', color: theme.text }}>{item.id}</Text>
            </View>
            <TouchableOpacity style={[styles.timeBox, { backgroundColor: theme.card }]} onPress={() => handleTimePress(item.id, 'start', item.startTime)}>
                <Text style={{ fontSize: 18, color: theme.primary, fontWeight: 'bold' }}>{item.startTime}</Text>
            </TouchableOpacity>
            <Text style={{ color: theme.subText }}>~</Text>
            <TouchableOpacity style={[styles.timeBox, { backgroundColor: theme.card }]} onPress={() => handleTimePress(item.id, 'end', item.endTime)}>
                <Text style={{ fontSize: 18, color: theme.text }}>{item.endTime}</Text>
            </TouchableOpacity>
        </View>
    );

    const morningData = timeLayout.filter((t: TimeSlot) => t.id <= periodConfig.morning);
    const afternoonData = timeLayout.filter((t: TimeSlot) => t.id > periodConfig.morning && t.id <= periodConfig.morning + periodConfig.afternoon);
    const eveningData = timeLayout.filter((t: TimeSlot) => t.id > periodConfig.morning + periodConfig.afternoon);

    return (
        <View style={[styles.container, { backgroundColor: theme.background }]}>
            
            <StatusBar 
                barStyle={theme.dark ? 'light-content' : 'dark-content'} 
                backgroundColor={theme.background} 
                translucent={false}
            />

            <View style={[styles.configCard, { backgroundColor: theme.card }]}> 
                <View style={styles.configItem}>
                    <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 5 }}>单节时长</Text>
                    <TouchableOpacity 
                        style={[styles.pickerBtn, { backgroundColor: theme.background, borderColor: theme.border }]}
                        onPress={() => openNumberPicker('选择单节时长', duration, setDuration)}
                    >
                        <Text style={{color: theme.text, fontWeight: 'bold'}}>{duration} 分钟</Text>
                        <Ionicons name="chevron-down" size={16} color={theme.subText} />
                    </TouchableOpacity>
                </View>
                
                <View style={styles.configItem}>
                    <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 5 }}>课间休息</Text>
                    <TouchableOpacity 
                        style={[styles.pickerBtn, { backgroundColor: theme.background, borderColor: theme.border }]}
                        onPress={() => openNumberPicker('选择课间休息', breakTime, setBreakTime)}
                    >
                        <Text style={{color: theme.text, fontWeight: 'bold'}}>{breakTime} 分钟</Text>
                        <Ionicons name="chevron-down" size={16} color={theme.subText} />
                    </TouchableOpacity>
                </View>
                
                <View style={{ justifyContent: 'center', flex: 1, paddingLeft: 10 }}>
                    <Button mode="contained" onPress={handleApplyAll} buttonColor={theme.primary} labelStyle={{fontSize: 12}}>一键应用</Button>
                </View>
            </View>

            <ScrollView contentContainerStyle={{ paddingBottom: 100 }}>
                {morningData.length > 0 && (
                    <View style={styles.section}>
                        <Text style={[styles.sectionHeader, { color: theme.subText }]}>☀️ 上午</Text>
                        {morningData.map(renderRow)}
                    </View>
                )}
                {afternoonData.length > 0 && (
                    <View style={styles.section}>
                        <Text style={[styles.sectionHeader, { color: theme.subText }]}>🌤️ 下午</Text>
                        {afternoonData.map(renderRow)}
                    </View>
                )}
                {eveningData.length > 0 && (
                    <View style={styles.section}>
                        <Text style={[styles.sectionHeader, { color: theme.subText }]}>🌙 晚上</Text>
                        {eveningData.map(renderRow)}
                    </View>
                )}
            </ScrollView>

            {/* 🔥 新增：右下角确认退出 FAB */}
            <FAB
                icon="check"
                label="完成"
                style={[styles.fab, { backgroundColor: theme.primary }]}
                color="white"
                onPress={() => navigation.goBack()}
            />

            <Portal>
                <Modal visible={pickerModalVisible} onDismiss={() => setPickerModalVisible(false)} contentContainerStyle={{ justifyContent: 'flex-end', flex: 1 }}>
                    <TouchableOpacity style={{ flex: 1 }} onPress={() => setPickerModalVisible(false)} />
                    <Surface style={{ borderTopLeftRadius: 16, borderTopRightRadius: 16, backgroundColor: theme.card, maxHeight: '50%' }}>
                        <View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'center' }}>
                            <Text style={{ fontSize: 16, fontWeight: 'bold', color: theme.text }}>{pickerTitle}</Text>
                        </View>
                        <ScrollView>
                            {pickerOptions.map((opt) => (
                                <TouchableRipple key={opt} onPress={() => { onPickerSelect(String(opt)); setPickerModalVisible(false); }}>
                                    <View style={{ padding: 15, alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: theme.border }}>
                                        <Text style={{ fontSize: 16, color: theme.text }}>{opt} 分钟</Text>
                                    </View>
                                </TouchableRipple>
                            ))}
                        </ScrollView>
                        <Button onPress={() => setPickerModalVisible(false)} style={{ margin: 10 }}>取消</Button>
                    </Surface>
                </Modal>
            </Portal>

            {showTimePicker && (
                Platform.OS === 'android' ? (
                    <DateTimePicker value={tempDate} mode="time" is24Hour={true} display="default" onChange={onTimePickerChange} />
                ) : (
                    <Portal>
                        <Modal visible={true} onDismiss={() => setShowTimePicker(false)} contentContainerStyle={styles.pickerModal}>
                            <Text style={{ fontSize: 18, fontWeight: 'bold', marginBottom: 20, textAlign: 'center', color: '#333' }}>
                                {Platform.OS === 'web' ? '请输入时间' : '选择时间'}
                            </Text>
                            <View style={styles.webPickerContainer}>
                                {Platform.OS === 'web' ? (
                                    createElement('input', {
                                        type: 'time',
                                        value: formatTime(tempDate),
                                        style: { fontSize: '24px', padding: '10px', width: '100%', borderRadius: '8px', border: '1px solid #ccc', backgroundColor: '#f9f9f9', color: '#333' },
                                        onChange: (e: any) => {
                                            const val = e.target.value;
                                            if (val) {
                                                const [h, m] = val.split(':').map(Number);
                                                const d = new Date(tempDate); d.setHours(h); d.setMinutes(m);
                                                setTempDate(d);
                                            }
                                        }
                                    })
                                ) : (
                                    <DateTimePicker value={tempDate} mode="time" is24Hour={true} display="spinner" onChange={onTimePickerChange} style={{ height: 150, width: '100%' }} textColor="black" />
                                )}
                            </View>
                            <Button mode="contained" onPress={confirmTimePicker} style={{ marginTop: 20 }} buttonColor={theme.primary}>确定</Button>
                        </Modal>
                    </Portal>
                )
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 15 },
    configCard: { flexDirection: 'row', padding: 15, borderRadius: 12, marginBottom: 20, elevation: 2, alignItems: 'center' },
    configItem: { width: 90, marginRight: 10 },
    pickerBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 10, paddingVertical: 8, borderRadius: 8, borderWidth: 1 },
    section: { marginBottom: 25 },
    sectionHeader: { fontSize: 14, fontWeight: 'bold', marginBottom: 10, marginLeft: 5 },
    row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1 },
    periodBadge: { width: 30, height: 30, borderRadius: 15, justifyContent: 'center', alignItems: 'center', marginRight: 10 },
    timeBox: { paddingVertical: 8, paddingHorizontal: 15, borderRadius: 8, minWidth: 80, alignItems: 'center' }, 
    pickerModal: { backgroundColor: 'white', padding: 20, margin: 20, borderRadius: 10, alignSelf: 'center', minWidth: 300 },
    webPickerContainer: { alignItems: 'center', justifyContent: 'center', paddingVertical: 10, width: '100%' },
    fab: { position: 'absolute', margin: 16, right: 0, bottom: 0 },
});