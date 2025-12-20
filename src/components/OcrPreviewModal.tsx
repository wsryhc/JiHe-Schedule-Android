// src/components/OcrPreviewModal.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { Portal, Modal, Surface, Button, TextInput, TouchableRipple } from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';

// 内部使用的简单选择器
const SelectionModal = ({ visible, onDismiss, title, options, onSelect }: any) => {
    const { theme } = useTheme();
    return (
        <Portal>
            <Modal visible={visible} onDismiss={onDismiss} contentContainerStyle={{ justifyContent: 'flex-end', flex: 1 }}>
                <TouchableOpacity style={{ flex: 1 }} onPress={onDismiss} />
                <Surface style={{ borderTopLeftRadius: 16, borderTopRightRadius: 16, backgroundColor: theme.card, maxHeight: '50%' }}>
                    <View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'center' }}>
                        <Text style={{ fontSize: 16, fontWeight: 'bold', color: theme.text }}>{title}</Text>
                    </View>
                    <ScrollView>
                        {options.map((opt: any) => (
                            <TouchableRipple key={opt.value} onPress={() => { onSelect(opt.value); onDismiss(); }}>
                                <View style={{ padding: 15, alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: theme.border }}>
                                    <Text style={{ fontSize: 16, color: theme.text }}>{opt.label}</Text>
                                </View>
                            </TouchableRipple>
                        ))}
                    </ScrollView>
                    <Button onPress={onDismiss} style={{ margin: 10 }}>取消</Button>
                </Surface>
            </Modal>
        </Portal>
    );
};

export const OcrPreviewModal = ({ visible, onDismiss, courses, onConfirm, periodConfig }: any) => {
    const { theme } = useTheme();
    const [editableCourses, setEditableCourses] = useState<any[]>([]);
    const [pickerVisible, setPickerVisible] = useState(false);
    const [pickerConfig, setPickerConfig] = useState<any>({ title: '', options: [], onSelect: () => {} });

    useEffect(() => {
        if (visible) {
            setEditableCourses(courses.map((c: any, i: number) => ({ ...c, _tempId: i })));
        }
    }, [visible, courses]);

    const updateCourse = (id: number, field: string, value: any) => {
        setEditableCourses(prev => prev.map(c => c._tempId === id ? { ...c, [field]: value } : c));
    };

    const deleteCourse = (id: number) => {
        setEditableCourses(prev => prev.filter(c => c._tempId !== id));
    };

    const handleConfirm = () => {
        onConfirm(editableCourses);
    };

    const openPicker = (title: string, options: any[], onSelect: (val: any) => void) => {
        setPickerConfig({ title, options, onSelect });
        setPickerVisible(true);
    };

    const dayOptions = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'].map((label, idx) => ({ label, value: idx }));
    // 默认给个20节课作为备选
    const totalPeriods = (periodConfig?.morning || 4) + (periodConfig?.afternoon || 4) + (periodConfig?.evening || 4) || 12;
    const periodOptions = Array.from({ length: totalPeriods }, (_, i) => ({ label: `第 ${i + 1} 节`, value: i + 1 }));

    return (
        <Portal>
            <Modal visible={visible} onDismiss={onDismiss} contentContainerStyle={{ backgroundColor: theme.card, margin: 20, borderRadius: 12, maxHeight: '80%' }}>
                <View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: theme.border }}>
                    <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text }}>校对识别结果 ({editableCourses.length})</Text>
                    <Text style={{ fontSize: 12, color: theme.subText, marginTop: 4 }}>点击文本框修改，点击垃圾桶删除</Text>
                </View>
                <ScrollView style={{ padding: 15 }}>
                    {editableCourses.map((item) => (
                        <Surface key={item._tempId} style={{ marginBottom: 12, padding: 10, borderRadius: 8, backgroundColor: theme.background }} elevation={1}>
                            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 }}>
                                <View style={{ flex: 1, marginRight: 8 }}>
                                    <Text style={{ fontSize: 10, color: theme.subText }}>课程名称</Text>
                                    <TextInput
                                        mode="outlined"
                                        value={item.name}
                                        onChangeText={t => updateCourse(item._tempId, 'name', t)}
                                        style={{ height: 40, fontSize: 14, backgroundColor: theme.background }}
                                        dense
                                        textColor={theme.text}
                                        theme={{ colors: { primary: theme.primary, background: theme.background, onSurfaceVariant: theme.subText } }}
                                    />
                                </View>
                                <TouchableOpacity onPress={() => deleteCourse(item._tempId)} style={{ justifyContent: 'center' }}>
                                    <Ionicons name="trash-outline" size={24} color="red" />
                                </TouchableOpacity>
                            </View>

                            <View style={{ flexDirection: 'row', gap: 8 }}>
                                <View style={{ flex: 1 }}>
                                    <Text style={{ fontSize: 10, color: theme.subText }}>星期</Text>
                                    <TouchableOpacity
                                        onPress={() => openPicker('选择星期', dayOptions, (val) => updateCourse(item._tempId, 'day', val))}
                                        style={{ height: 35, borderWidth: 1, borderColor: theme.border, backgroundColor: theme.background, borderRadius: 4, justifyContent: 'center', paddingHorizontal: 8 }}
                                    >
                                        <Text style={{ color: theme.text, fontSize: 12 }}>{dayOptions[item.day]?.label || '未知'}</Text>
                                    </TouchableOpacity>
                                </View>
                                <View style={{ flex: 1 }}>
                                    <Text style={{ fontSize: 10, color: theme.subText }}>开始节</Text>
                                    <TouchableOpacity
                                        onPress={() => openPicker('选择开始节次', periodOptions, (val) => updateCourse(item._tempId, 'startPeriod', val))}
                                        style={{ height: 35, borderWidth: 1, borderColor: theme.border, backgroundColor: theme.background, borderRadius: 4, justifyContent: 'center', paddingHorizontal: 8 }}
                                    >
                                        <Text style={{ color: theme.text, fontSize: 12 }}>第 {item.startPeriod} 节</Text>
                                    </TouchableOpacity>
                                </View>
                                <View style={{ flex: 1 }}>
                                    <Text style={{ fontSize: 10, color: theme.subText }}>结束节</Text>
                                    <TouchableOpacity
                                        onPress={() => openPicker('选择结束节次', periodOptions, (val) => updateCourse(item._tempId, 'endPeriod', val))}
                                        style={{ height: 35, borderWidth: 1, borderColor: theme.border, backgroundColor: theme.background, borderRadius: 4, justifyContent: 'center', paddingHorizontal: 8 }}
                                    >
                                        <Text style={{ color: theme.text, fontSize: 12 }}>第 {item.endPeriod} 节</Text>
                                    </TouchableOpacity>
                                </View>
                            </View>

                            <View style={{ marginTop: 8 }}>
                                <Text style={{ fontSize: 10, color: theme.subText }}>教室</Text>
                                <TextInput
                                    mode="outlined"
                                    value={item.classroom}
                                    onChangeText={t => updateCourse(item._tempId, 'classroom', t)}
                                    style={{ height: 35, fontSize: 12, backgroundColor: theme.background }}
                                    dense
                                    textColor={theme.text}
                                    theme={{ colors: { primary: theme.primary, background: theme.background, onSurfaceVariant: theme.subText } }}
                                />
                            </View>
                        </Surface>
                    ))}
                </ScrollView>
                <View style={{ padding: 15, borderTopWidth: 1, borderTopColor: theme.border, flexDirection: 'row', justifyContent: 'flex-end', gap: 10 }}>
                    <Button mode="text" onPress={onDismiss}>取消</Button>
                    <Button mode="contained" onPress={handleConfirm}>确认导入</Button>
                </View>

                <SelectionModal
                    visible={pickerVisible}
                    onDismiss={() => setPickerVisible(false)}
                    title={pickerConfig.title}
                    options={pickerConfig.options}
                    onSelect={pickerConfig.onSelect}
                />
            </Modal>
        </Portal>
    );
};