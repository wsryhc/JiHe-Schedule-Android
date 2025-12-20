// src/pages/ScheduleManagementPage.tsx
import React, { useState, useRef, useLayoutEffect, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, Alert, ScrollView, Platform, ActivityIndicator, StatusBar } from 'react-native';
import { Surface, IconButton, Modal, Portal, TextInput, Button, Divider } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule, ScheduleInfo } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
const { StorageAccessFramework } = FileSystem;

import ViewShot from 'react-native-view-shot';
import { getMonday, formatDate } from '../utils/scheduleHelpers';
import ScheduleList from '../components/ScheduleList';

export default function ScheduleManagementPage({ navigation }: any) {
    const { theme } = useTheme();
    const {
        scheduleList, currentSchedule, switchSchedule, updateScheduleInfo,
        deleteSchedule, exportScheduleData, currentWeek,
        // 🔥 获取清空方法
        clearAllSchedules
    } = useSchedule();

    const [modalVisible, setModalVisible] = useState(false);
    const [showExportImageModal, setShowExportImageModal] = useState(false);
    const [showExportJsonModal, setShowExportJsonModal] = useState(false);

    // 🔥 批量删除菜单状态
    const [deleteMenuVisible, setDeleteMenuVisible] = useState(false);

    const [editId, setEditId] = useState('');
    const [editName, setEditName] = useState('');
    const [editStartDate, setEditStartDate] = useState(getMonday(new Date()));
    const [editTotalWeeks, setEditTotalWeeks] = useState(25);
    const [showWeekSelector, setShowWeekSelector] = useState(false);

    const [jsonContent, setJsonContent] = useState('');
    const [exportWeek, setExportWeek] = useState(1);
    const [isGlobalLoading, setIsGlobalLoading] = useState(false);

    const viewShotRef = useRef<any>(null);
    const [exportTargetSchedule, setExportTargetSchedule] = useState<ScheduleInfo | null>(null);

    useLayoutEffect(() => {
        navigation.setOptions({
            title: '课表管理',
            headerStyle: { backgroundColor: theme.background },
            headerTintColor: theme.text,
        });
    }, [navigation, theme]);

    useEffect(() => {
        if (scheduleList && scheduleList.length > 0 && !currentSchedule) {
            switchSchedule(scheduleList[0].id);
        }
    }, [scheduleList, currentSchedule]);

    // 🔥 处理全部删除
    const handleDeleteAll = () => {
        setDeleteMenuVisible(false);
        Alert.alert(
            '删除所有课表',
            '确定要清空所有课表数据吗？此操作不可恢复。',
            [
                { text: '取消', style: 'cancel' },
                {
                    text: '确定清空',
                    style: 'destructive',
                    onPress: () => {
                        clearAllSchedules();
                        Alert.alert('已清空', '所有课表数据已删除');
                    }
                }
            ]
        );
    };

    const openEdit = (schedule: ScheduleInfo) => {
        setEditId(schedule.id);
        setEditName(schedule.name);
        setEditStartDate(new Date(schedule.termStartDate));
        setEditTotalWeeks(schedule.totalWeeks || 25);
        setExportWeek(currentWeek > 0 ? currentWeek : 1);
        setExportTargetSchedule(schedule);
        setModalVisible(true);
    };

    const handleSaveInfo = () => {
        if (!editName.trim()) { Alert.alert('请输入名称'); return; }
        updateScheduleInfo(editId, editName, formatDate(editStartDate), editTotalWeeks);
        setModalVisible(false);
    };

    const handleDelete = () => {
        Alert.alert('确认删除', '确定要删除该课表吗？', [
            { text: '取消', style: 'cancel' },
            { text: '删除', style: 'destructive', onPress: () => { deleteSchedule(editId); setModalVisible(false); } }
        ]);
    };

    // Export Logic
    const handleOpenJsonExport = () => {
        const json = exportScheduleData(editId);
        setJsonContent(json);
        setShowExportJsonModal(true);
    };

    const copyJson = async () => { await Clipboard.setStringAsync(jsonContent); Alert.alert('成功', '已复制'); };

    const saveJsonFile = async () => {
        const fileName = `schedule_${editId}_${new Date().getTime()}.json`;
        await saveFileGeneric(fileName, 'application/json', jsonContent, false);
    };

    const shareJsonFile = async () => {
        const fileName = `schedule_share_${new Date().getTime()}.json`;
        await shareFileGeneric(fileName, 'application/json', jsonContent, false);
    };

    const handleOpenImageExport = () => {
        setExportWeek(currentWeek > 0 ? currentWeek : 1);
        setShowExportImageModal(true);
    };

    const saveImageFile = async () => {
        await processImageExport(async (uri) => {
            const fileName = `schedule_img_${new Date().getTime()}.png`;
            if (Platform.OS === 'android') {
                const base64 = await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.Base64 });
                await saveFileGeneric(fileName, 'image/png', base64, true);
            } else {
                await Sharing.shareAsync(uri);
            }
        });
    };

    const shareImageFile = async () => {
        await processImageExport(async (uri) => {
            await Sharing.shareAsync(uri, { mimeType: 'image/png', dialogTitle: '分享课表图片' });
        });
    };

    const processImageExport = async (callback: (uri: string) => Promise<void>) => {
        setIsGlobalLoading(true);
        setTimeout(async () => {
            try {
                if (viewShotRef.current) {
                    const uri = await viewShotRef.current.capture();
                    await callback(uri);
                } else { Alert.alert('错误', '无法获取视图'); }
            } catch (e) { Alert.alert('失败', '图片生成失败'); }
            finally {
                setIsGlobalLoading(false);
                setShowExportImageModal(false);
            }
        }, 800);
    };

    const saveFileGeneric = async (fileName: string, mimeType: string, content: string, isBase64: boolean) => {
        if (Platform.OS === 'android') {
            try {
                const permissions = await StorageAccessFramework.requestDirectoryPermissionsAsync();
                if (permissions.granted) {
                    setIsGlobalLoading(true);
                    const uri = await StorageAccessFramework.createFileAsync(permissions.directoryUri, fileName, mimeType);
                    await FileSystem.writeAsStringAsync(uri, content, { encoding: isBase64 ? FileSystem.EncodingType.Base64 : FileSystem.EncodingType.UTF8 });
                    Alert.alert('成功', '文件已保存');
                }
            } catch (e) { Alert.alert('失败', '保存文件失败'); }
            finally {
                setIsGlobalLoading(false);
                if (mimeType === 'application/json') setShowExportJsonModal(false);
            }
        } else {
            shareFileGeneric(fileName, mimeType, content, isBase64);
        }
    };

    const shareFileGeneric = async (fileName: string, mimeType: string, content: string, isBase64: boolean) => {
        const fileUri = FileSystem.cacheDirectory + fileName;
        try {
            setIsGlobalLoading(true);
            await FileSystem.writeAsStringAsync(fileUri, content, { encoding: isBase64 ? FileSystem.EncodingType.Base64 : FileSystem.EncodingType.UTF8 });
            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(fileUri, { mimeType: mimeType, dialogTitle: '分享文件', UTI: mimeType === 'application/json' ? 'public.json' : 'public.png' });
            }
        } catch (e) { Alert.alert('失败', '分享失败'); }
        finally {
            setIsGlobalLoading(false);
            if (mimeType === 'application/json') setShowExportJsonModal(false);
            try { await FileSystem.deleteAsync(fileUri, { idempotent: true }); } catch (ignore) { }
        }
    };

    const adjustEditStartDate = (weeks: number) => {
        const newDate = new Date(editStartDate);
        newDate.setDate(newDate.getDate() + (weeks * 7));
        setEditStartDate(newDate);
    };

    const renderWeekSelectorButton = () => (
        <View style={{ marginBottom: 20 }}>
            <Text style={{ color: theme.subText, marginBottom: 5 }}>学期总周数</Text>
            <TouchableOpacity onPress={() => setShowWeekSelector(true)} activeOpacity={0.7}>
                <View style={{
                    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                    borderWidth: 1, borderColor: theme.border, borderRadius: 8, padding: 12,
                    backgroundColor: theme.background
                }}>
                    <Text style={{ fontSize: 16, color: theme.text }}>
                        共 <Text style={{ color: theme.primary, fontWeight: 'bold', fontSize: 18 }}>{editTotalWeeks}</Text> 周
                    </Text>
                    <Ionicons name="chevron-down" size={20} color={theme.subText} />
                </View>
            </TouchableOpacity>
        </View>
    );

    const renderItem = ({ item }: { item: ScheduleInfo }) => {
        const isActive = currentSchedule?.id === item.id;
        return (
            <TouchableOpacity onPress={() => switchSchedule(item.id)} activeOpacity={0.8}>
                <Surface style={[styles.itemCard, { backgroundColor: theme.card, borderColor: isActive ? theme.primary : 'transparent', borderWidth: 2 }]} elevation={1}>
                    <View style={{ flex: 1 }}>
                        <Text style={[styles.itemTitle, { color: theme.text }]}>{item.name}</Text>
                        <Text style={{ color: theme.subText }}>开学: {item.termStartDate}</Text>
                    </View>
                    {isActive && <Text style={{ color: theme.primary, fontWeight: 'bold', marginRight: 10 }}>使用中</Text>}
                    <IconButton icon="pencil" iconColor={theme.subText} size={20} onPress={() => openEdit(item)} />
                </Surface>
            </TouchableOpacity>
        );
    };

    const inputTheme = { colors: { primary: theme.primary, background: theme.card, onSurface: theme.text, onSurfaceVariant: theme.subText } };

    return (
        <View style={[styles.container, { backgroundColor: theme.background }]}>
            <StatusBar
                barStyle={theme.dark ? 'light-content' : 'dark-content'}
                backgroundColor={theme.background}
                translucent={false}
            />

            <FlatList data={scheduleList} keyExtractor={item => item.id} renderItem={renderItem} contentContainerStyle={{ padding: 15, paddingBottom: 100 }} />

            {/* 🔥 新增：删除 FAB (红色 X) */}
            <TouchableOpacity
                style={[styles.fab, { backgroundColor: '#FF5252', bottom: 100 }]} // 位置在添加按钮上方
                onPress={() => setDeleteMenuVisible(true)}
            >
                <Ionicons name="close" size={30} color="#fff" />
            </TouchableOpacity>

            <TouchableOpacity style={[styles.fab, { backgroundColor: theme.primary }]} onPress={() => navigation.navigate('ScheduleCreate')}>
                <Ionicons name="add" size={30} color="#fff" />
            </TouchableOpacity>

            {/* 🔥🔥 批量删除菜单 Modal */}
            <Portal>
                <Modal visible={deleteMenuVisible} onDismiss={() => setDeleteMenuVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '80%', maxWidth: 300 }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 20, textAlign: 'center' }}>清空所有课表</Text>

                        <Button mode="contained" buttonColor="#FF5252" onPress={handleDeleteAll}>
                            删除所有课表
                        </Button>

                        <Button mode="text" textColor={theme.subText} style={{ marginTop: 10 }} onPress={() => setDeleteMenuVisible(false)}>
                            取消
                        </Button>
                    </Surface>
                </Modal>
            </Portal>

            {/* 1. 主编辑弹窗 */}
            <Portal>
                <Modal visible={modalVisible} onDismiss={() => setModalVisible(false)} contentContainerStyle={{ padding: 20 }}>
                    <Surface style={[styles.modalCard, { backgroundColor: theme.card }]}>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}>
                            <Text style={[styles.modalTitle, { color: theme.text, marginBottom: 0 }]}>编辑课表</Text>
                            <IconButton icon="close" size={24} onPress={() => setModalVisible(false)} style={{ margin: 0, marginRight: -10 }} />
                        </View>

                        <ScrollView>
                            <TextInput label="名称" value={editName} onChangeText={setEditName} mode="outlined" style={styles.input} textColor={theme.text} theme={inputTheme} />
                            <View style={{ marginBottom: 15 }}>
                                <Text style={{ color: theme.subText, marginBottom: 5 }}>开学日期</Text>
                                <View style={[styles.dateRow, { borderColor: theme.border }]}>
                                    <IconButton icon="chevron-left" onPress={() => adjustEditStartDate(-1)} iconColor={theme.text} />
                                    <Text style={{ color: theme.primary, fontWeight: 'bold' }}>{formatDate(editStartDate)}</Text>
                                    <IconButton icon="chevron-right" onPress={() => adjustEditStartDate(1)} iconColor={theme.text} />
                                </View>
                            </View>

                            {renderWeekSelectorButton()}

                            <Button mode="contained" onPress={handleSaveInfo} buttonColor={theme.primary} style={{ marginBottom: 10 }}>保存修改</Button>

                            <Divider style={{ marginVertical: 15 }} />
                            <Text style={{ color: theme.subText, marginBottom: 10 }}>数据导出</Text>
                            <View style={{ flexDirection: 'row', gap: 10, marginBottom: 10 }}>
                                <Button mode="outlined" icon="image" onPress={handleOpenImageExport} style={{ flex: 1 }} textColor={theme.primary}>导出图片</Button>
                                <Button mode="outlined" icon="code-json" onPress={handleOpenJsonExport} style={{ flex: 1 }} textColor={theme.primary}>导出JSON</Button>
                            </View>

                            <Button mode="contained" buttonColor="#FF6B6B" onPress={handleDelete} style={{ marginTop: 5 }}>删除课表</Button>
                        </ScrollView>
                    </Surface>
                </Modal>
            </Portal>

            {/* 2. 图片导出确认弹窗 */}
            <Portal>
                <Modal visible={showExportImageModal} onDismiss={() => setShowExportImageModal(false)} contentContainerStyle={{ padding: 20, alignItems: 'center' }}>
                    <Surface style={[styles.miniModalCard, { backgroundColor: theme.card }]}>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}>
                            <Text style={[styles.modalTitle, { color: theme.text, marginBottom: 0 }]}>导出图片</Text>
                            <IconButton icon="close" size={20} onPress={() => setShowExportImageModal(false)} style={{ margin: 0, marginRight: -10 }} />
                        </View>

                        <Text style={{ color: theme.subText, marginBottom: 10, textAlign: 'center' }}>请选择要导出的周次</Text>

                        <View style={[styles.dateRow, { borderColor: theme.border, marginBottom: 25, paddingHorizontal: 10 }]}>
                            <IconButton icon="minus" onPress={() => setExportWeek(p => Math.max(1, p - 1))} iconColor={theme.text} />
                            <Text style={{ color: theme.primary, fontSize: 18, fontWeight: 'bold' }}>第 {exportWeek} 周</Text>
                            <IconButton icon="plus" onPress={() => setExportWeek(p => p + 1)} iconColor={theme.text} />
                        </View>

                        <View style={{ flexDirection: 'row', gap: 10 }}>
                            <Button mode="contained" icon="content-save" onPress={saveImageFile} buttonColor={theme.primary} style={{ flex: 1, elevation: 0 }}>另存为</Button>
                            <Button mode="outlined" icon="share-variant" onPress={shareImageFile} textColor={theme.primary} style={{ flex: 1 }}>分享</Button>
                        </View>
                    </Surface>
                </Modal>
            </Portal>

            {/* 3. JSON 导出弹窗 */}
            <Portal>
                <Modal visible={showExportJsonModal} onDismiss={() => setShowExportJsonModal(false)} contentContainerStyle={{ padding: 20, alignItems: 'center' }}>
                    <Surface style={[styles.miniModalCard, { backgroundColor: theme.card }]}>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                            <Text style={[styles.modalTitle, { color: theme.text, marginBottom: 0 }]}>导出 JSON</Text>
                            <IconButton icon="close" size={20} onPress={() => setShowExportJsonModal(false)} style={{ margin: 0, marginRight: -10 }} />
                        </View>

                        <Text style={{ color: theme.subText, marginBottom: 20, textAlign: 'center' }}>
                            数据已准备就绪，请选择操作
                        </Text>

                        <View style={{ gap: 10, width: '100%' }}>
                            <View style={{ flexDirection: 'row', gap: 10 }}>
                                <Button mode="contained" icon="content-copy" onPress={copyJson} buttonColor={theme.primary} style={{ flex: 1, elevation: 0 }}>复制</Button>
                                <Button mode="contained" icon="content-save" onPress={saveJsonFile} buttonColor={theme.primary} style={{ flex: 1, elevation: 0 }}>另存为</Button>
                            </View>
                            <Button mode="outlined" icon="share-variant" onPress={shareJsonFile} textColor={theme.primary} style={{ width: '100%' }}>
                                分享文件
                            </Button>
                        </View>
                    </Surface>
                </Modal>
            </Portal>

            {/* 周数选择弹窗 */}
            <Portal>
                <Modal visible={showWeekSelector} onDismiss={() => setShowWeekSelector(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '85%', maxWidth: 350, maxHeight: '80%' }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>选择总周数</Text>
                        <ScrollView style={{ maxHeight: 300 }} showsVerticalScrollIndicator={false}>
                            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'center' }}>
                                {Array.from({ length: 48 }, (_, i) => i + 1).map((w) => (
                                    <TouchableOpacity
                                        key={w}
                                        onPress={() => { setEditTotalWeeks(w); setShowWeekSelector(false); }}
                                        style={{
                                            width: 45, height: 45, borderRadius: 8,
                                            backgroundColor: editTotalWeeks === w ? theme.primary : theme.background,
                                            borderWidth: 1, borderColor: editTotalWeeks === w ? theme.primary : theme.border,
                                            justifyContent: 'center', alignItems: 'center'
                                        }}
                                    >
                                        <Text style={{ color: editTotalWeeks === w ? '#fff' : theme.text, fontWeight: 'bold' }}>{w}</Text>
                                    </TouchableOpacity>
                                ))}
                            </View>
                        </ScrollView>
                        <Button mode="contained" onPress={() => setShowWeekSelector(false)} style={{ marginTop: 20, elevation: 0 }} buttonColor={theme.primary}>取消</Button>
                    </Surface>
                </Modal>
            </Portal>

            {isGlobalLoading && (
                <Portal>
                    <View style={styles.loadingOverlay}>
                        <Surface style={[styles.loadingContainer, { backgroundColor: theme.card }]} elevation={4}>
                            <ActivityIndicator size="large" color={theme.primary} />
                            <Text style={{ marginTop: 15, color: theme.text, fontWeight: 'bold' }}>处理中...</Text>
                        </Surface>
                    </View>
                </Portal>
            )}

            <View style={{ position: 'absolute', left: -9999, top: 0, width: 400, height: 800, backgroundColor: theme.background }} pointerEvents="none">
                <ViewShot ref={viewShotRef} options={{ format: 'png', quality: 0.9 }}>
                    <View style={{ backgroundColor: theme.background, padding: 20 }}>
                        <Text style={{ fontSize: 20, fontWeight: 'bold', color: theme.text, textAlign: 'center', marginBottom: 10 }}>
                            {exportTargetSchedule?.name} - 第 {exportWeek} 周
                        </Text>
                        <ScheduleList weekIndex={exportWeek} onCellPress={undefined} targetSchedule={exportTargetSchedule} />
                    </View>
                </ViewShot>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    itemCard: { flexDirection: 'row', alignItems: 'center', padding: 15, borderRadius: 12, marginBottom: 12 },
    itemTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 4 },
    fab: { position: 'absolute', right: 20, bottom: 30, width: 56, height: 56, borderRadius: 28, justifyContent: 'center', alignItems: 'center', elevation: 6 },
    modalCard: { padding: 20, borderRadius: 12, width: '90%', maxHeight: '80%', alignSelf: 'center' },
    miniModalCard: { padding: 20, paddingBottom: 25, borderRadius: 12, width: '80%', maxWidth: 320, alignSelf: 'center' },
    modalTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 20, textAlign: 'center' },
    dateRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderWidth: 1, borderRadius: 4 },
    input: { marginBottom: 15, backgroundColor: 'transparent' },
    loadingOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0, 0, 0, 0.4)', justifyContent: 'center', alignItems: 'center', zIndex: 9999 },
    loadingContainer: { padding: 25, borderRadius: 16, alignItems: 'center', width: 160 },
});