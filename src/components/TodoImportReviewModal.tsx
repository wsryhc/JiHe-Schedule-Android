// src/components/TodoImportReviewModal.tsx
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, StatusBar, Platform } from 'react-native';
import { Portal, Modal, Surface, Button, IconButton, TextInput, Chip } from 'react-native-paper';
import DateTimePicker from '@react-native-community/datetimepicker';
import { useTheme } from '../context/ThemeContext';
import { Todo } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';

interface TodoImportReviewModalProps {
    visible: boolean;
    onDismiss: () => void;
    onConfirm: (todos: Todo[]) => void;
    initialTodos: any[];
}

const PRESET_TAGS = [
    { label: '默认', value: 'default', icon: 'checkbox-blank-circle-outline' },
    { label: '生日', value: 'birthday', icon: 'cake-variant' },
    { label: '纪念日', value: 'anniversary', icon: 'heart' },
    { label: '考试', value: 'exam', icon: 'school' },
    { label: '会议', value: 'meeting', icon: 'briefcase' },
];

const COLOR_OPTIONS = ['#2196F3', '#F44336', '#E91E63', '#FF9800', '#4CAF50', '#9C27B0', '#607D8B'];

export function TodoImportReviewModal({ visible, onDismiss, onConfirm, initialTodos }: TodoImportReviewModalProps) {
    const { theme } = useTheme();
    const [todos, setTodos] = useState<any[]>([]);

    // 冲突ID集合（这里指代不符合规范的ID）
    const [invalidIds, setInvalidIds] = useState<Set<string>>(new Set());

    // 时间选择器状态
    const [showDatePicker, setShowDatePicker] = useState(false);
    const [showTimePicker, setShowTimePicker] = useState(false);
    const [pickerMode, setPickerMode] = useState<'date' | 'start' | 'end'>('date');
    const [editingTodoId, setEditingTodoId] = useState<string | null>(null);
    const [tempDate, setTempDate] = useState(new Date());

    useEffect(() => {
        if (visible) {
            // 初始化清洗：强制移除重复和提醒
            const safeTodos = (initialTodos || []).map((t, index) => ({
                ...t,
                id: t.id || `import-todo-${index}-${Date.now()}`,
                title: t.title || '',
                description: t.description || '',
                date: t.date || new Date().toISOString().split('T')[0],
                startTime: t.startTime || '12:00',
                endTime: t.endTime || '13:00',
                color: t.color || COLOR_OPTIONS[0],
                tagType: t.tagType || 'default',
                tag: t.tag || '默认',
                completed: false, // 导入的默认未完成
                // 🔥 强制清洗
                repeatType: 'none',
                isYearly: false,
                reminder: null
            }));
            setTodos(safeTodos);
            validateTodos(safeTodos);
        }
    }, [visible, initialTodos]);

    const validateTodos = (currentTodos: any[]) => {
        const invalids = new Set<string>();
        currentTodos.forEach(t => {
            // 校验1: 标题必填
            if (!t.title.trim()) invalids.add(t.id);
            // 校验2: 结束时间必须大于开始时间
            const [sh, sm] = t.startTime.split(':').map(Number);
            const [eh, em] = t.endTime.split(':').map(Number);
            if ((eh * 60 + em) < (sh * 60 + sm)) invalids.add(t.id);
        });
        setInvalidIds(invalids);
    };

    const handleUpdateTodo = (id: string, field: string, value: any) => {
        const newTodos = todos.map(t => {
            if (t.id === id) {
                const updated = { ...t, [field]: value };
                // 特殊处理：如果是改 tagType，同步 tag 名称
                if (field === 'tagType') {
                    const tagObj = PRESET_TAGS.find(pt => pt.value === value);
                    updated.tag = tagObj ? tagObj.label : '默认';
                }
                return updated;
            }
            return t;
        });
        setTodos(newTodos);
        validateTodos(newTodos);
    };

    const handleRemoveTodo = (id: string) => {
        const newTodos = todos.filter(t => t.id !== id);
        setTodos(newTodos);
        validateTodos(newTodos);
    };

    const handleConfirmImport = () => {
        if (invalidIds.size > 0) {
            Alert.alert('无法导入', '列表中仍有标黄的错误事项（标题为空或时间设置错误），请修改或删除后再导入。');
            return;
        }
        onConfirm(todos);
    };

    // --- 日期时间选择逻辑 ---
    const openPicker = (id: string, mode: 'date' | 'start' | 'end') => {
        setEditingTodoId(id);
        setPickerMode(mode);
        const targetTodo = todos.find(t => t.id === id);
        if (targetTodo) {
            if (mode === 'date') {
                setTempDate(new Date(targetTodo.date));
            } else {
                const timeStr = mode === 'start' ? targetTodo.startTime : targetTodo.endTime;
                const [h, m] = timeStr.split(':').map(Number);
                const d = new Date(); d.setHours(h); d.setMinutes(m);
                setTempDate(d);
            }
            if (mode === 'date') setShowDatePicker(true);
            else setShowTimePicker(true);
        }
    };

    const onPickerChange = (event: any, selectedDate?: Date) => {
        if (Platform.OS === 'android') {
            setShowDatePicker(false);
            setShowTimePicker(false);
        }
        if (selectedDate && editingTodoId) {
            if (pickerMode === 'date') {
                const dateStr = selectedDate.toISOString().split('T')[0];
                handleUpdateTodo(editingTodoId, 'date', dateStr);
            } else {
                const h = String(selectedDate.getHours()).padStart(2, '0');
                const m = String(selectedDate.getMinutes()).padStart(2, '0');
                const timeStr = `${h}:${m}`;
                if (pickerMode === 'start') handleUpdateTodo(editingTodoId, 'startTime', timeStr);
                else handleUpdateTodo(editingTodoId, 'endTime', timeStr);
            }
        }
    };

    const inputTheme = { colors: { primary: theme.primary, background: theme.card, onSurface: theme.text, onSurfaceVariant: theme.subText } };

    return (
        <Portal>
            <Modal visible={visible} onDismiss={onDismiss} contentContainerStyle={{ flex: 1, backgroundColor: theme.background }}>
                <StatusBar barStyle={theme.dark ? 'light-content' : 'dark-content'} backgroundColor={theme.card} translucent={false} />
                <View style={styles.container}>
                    {/* Header */}
                    <Surface style={[styles.header, { backgroundColor: theme.card }]} elevation={2}>
                        <View>
                            <Text style={[styles.title, { color: theme.text }]}>导入事项校对</Text>
                            <Text style={{ color: theme.subText, fontSize: 12 }}>
                                请核对以下事项。有问题的事项已标黄（如标题为空或时间错误）。{'\n'}
                                <Text style={{ color: '#FF9800' }}>注：导入事项已自动重置为不重复、无提醒。</Text>
                            </Text>
                        </View>
                    </Surface>

                    <ScrollView contentContainerStyle={{ padding: 15, paddingBottom: 100 }} keyboardShouldPersistTaps="handled">
                        {todos.map((item, index) => {
                            const isInvalid = invalidIds.has(item.id);
                            return (
                                <Surface
                                    key={item.id}
                                    style={[
                                        styles.card,
                                        {
                                            backgroundColor: isInvalid ? '#FFF9C4' : theme.card,
                                            borderColor: isInvalid ? '#FFC107' : theme.border,
                                            borderWidth: isInvalid ? 2 : 1
                                        }
                                    ]}
                                    elevation={1}
                                >
                                    <View style={styles.cardHeader}>
                                        <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 4 }}>名称 *</Text>
                                        <View style={{ flexDirection: 'row', alignItems: 'flex-start' }}>
                                            <TextInput
                                                mode="outlined"
                                                value={item.title}
                                                onChangeText={(text) => handleUpdateTodo(item.id, 'title', text)}
                                                style={[styles.mainInput, { flex: 1 }]}
                                                dense
                                                textColor={theme.text}
                                                theme={inputTheme}
                                            />
                                            <IconButton
                                                icon="trash-can-outline"
                                                size={20}
                                                iconColor={theme.subText}
                                                onPress={() => handleRemoveTodo(item.id)}
                                                style={{ marginTop: 6 }}
                                            />
                                        </View>
                                        <TextInput
                                            mode="outlined"
                                            value={item.description}
                                            onChangeText={(text) => handleUpdateTodo(item.id, 'description', text)}
                                            style={[styles.subInput, { marginBottom: 10 }]}
                                            placeholder="备注"
                                            dense
                                            textColor={theme.text}
                                            theme={inputTheme}
                                        />
                                    </View>

                                    {/* 类型选择 */}
                                    <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 5 }}>类型 (若需自定义请导入后编辑)</Text>
                                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 }}>
                                        {PRESET_TAGS.map(tag => (
                                            <Chip
                                                key={tag.value}
                                                selected={item.tagType === tag.value}
                                                showSelectedOverlay
                                                onPress={() => handleUpdateTodo(item.id, 'tagType', tag.value)}
                                                style={{ backgroundColor: item.tagType === tag.value ? theme.primary + '20' : theme.background }}
                                                textStyle={{ color: item.tagType === tag.value ? theme.primary : theme.text, fontSize: 11 }}
                                                compact
                                            >
                                                {tag.label}
                                            </Chip>
                                        ))}
                                    </View>

                                    {/* 颜色选择 */}
                                    <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 5 }}>颜色</Text>
                                    <View style={{ flexDirection: 'row', gap: 10, marginBottom: 15 }}>
                                        {COLOR_OPTIONS.map(c => (
                                            <TouchableOpacity
                                                key={c}
                                                onPress={() => handleUpdateTodo(item.id, 'color', c)}
                                                style={{
                                                    width: 24, height: 24, borderRadius: 12, backgroundColor: c,
                                                    borderWidth: item.color === c ? 2 : 0, borderColor: theme.text
                                                }}
                                            />
                                        ))}
                                    </View>

                                    {/* 时间日期 */}
                                    <View style={{ flexDirection: 'row', gap: 10 }}>
                                        <TouchableOpacity style={[styles.actionBtn, { borderColor: theme.border, flex: 1.2 }]} onPress={() => openPicker(item.id, 'date')}>
                                            <Ionicons name="calendar-outline" size={16} color={theme.primary} style={{ marginRight: 6 }} />
                                            <Text style={{ color: theme.text, fontSize: 13 }}>{item.date}</Text>
                                        </TouchableOpacity>
                                        <TouchableOpacity style={[styles.actionBtn, { borderColor: theme.border, flex: 1 }]} onPress={() => openPicker(item.id, 'start')}>
                                            <Text style={{ color: theme.text, fontSize: 13 }}>{item.startTime}</Text>
                                        </TouchableOpacity>
                                        <View style={{ justifyContent: 'center' }}><Text>-</Text></View>
                                        <TouchableOpacity style={[styles.actionBtn, { borderColor: theme.border, flex: 1 }]} onPress={() => openPicker(item.id, 'end')}>
                                            <Text style={{ color: theme.text, fontSize: 13 }}>{item.endTime}</Text>
                                        </TouchableOpacity>
                                    </View>

                                    <Text style={{ color: theme.subText, fontSize: 10, marginTop: 10, textAlign: 'center' }}>
                                        如果需要修改提醒与重复，请先导入后再去编辑
                                    </Text>
                                </Surface>
                            );
                        })}
                    </ScrollView>

                    <Surface style={[styles.footer, { backgroundColor: theme.card }]} elevation={4}>
                        <Button mode="outlined" onPress={onDismiss} style={{ flex: 1, marginRight: 10 }} textColor={theme.subText}>取消</Button>
                        <Button mode="contained" onPress={handleConfirmImport} style={{ flex: 2 }} buttonColor={theme.primary}>
                            确认导入 ({todos.length})
                        </Button>
                    </Surface>

                    {showDatePicker && (
                        <DateTimePicker value={tempDate} mode="date" display="default" onChange={onPickerChange} />
                    )}
                    {showTimePicker && (
                        <DateTimePicker value={tempDate} mode="time" is24Hour={true} display="default" onChange={onPickerChange} />
                    )}
                </View>
            </Modal>
        </Portal>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    header: { padding: 20, paddingTop: 20, borderBottomLeftRadius: 16, borderBottomRightRadius: 16 },
    title: { fontSize: 20, fontWeight: 'bold', marginBottom: 4 },
    footer: { padding: 20, flexDirection: 'row', borderTopLeftRadius: 16, borderTopRightRadius: 16 },
    card: { borderRadius: 12, marginBottom: 15, padding: 15 },
    cardHeader: { marginBottom: 10 },
    mainInput: { height: 45, fontSize: 16, backgroundColor: 'transparent' },
    subInput: { height: 40, backgroundColor: 'transparent', fontSize: 14 },
    actionBtn: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
        paddingVertical: 8, paddingHorizontal: 5, borderRadius: 8,
        borderWidth: 1, backgroundColor: 'rgba(0,0,0,0.02)'
    },
});