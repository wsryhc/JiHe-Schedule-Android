// src/pages/TodoManagementPage.tsx
import React, { useState, useLayoutEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, Alert, ActivityIndicator, StatusBar, KeyboardAvoidingView, Platform } from 'react-native';
import { Surface, Button, TextInput, Divider, Portal, Modal, SegmentedButtons } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule } from '../context/ScheduleContext';
import * as Clipboard from 'expo-clipboard';
import * as FileSystem from 'expo-file-system/legacy';
import * as DocumentPicker from 'expo-document-picker';
import * as Sharing from 'expo-sharing';
import { TodoImportReviewModal } from '../components/TodoImportReviewModal';

const { StorageAccessFramework } = FileSystem;

export default function TodoManagementPage({ navigation }: any) {
    const { theme } = useTheme();
    // 🔥 需要用到 setTodoList 来清空
    const { todoList, exportTodoData, batchAddTodos, setTodoList } = useSchedule();

    const [importMode, setImportMode] = useState('text');
    const [jsonContent, setJsonContent] = useState('');
    const [importText, setImportText] = useState('');
    const [isGlobalLoading, setIsGlobalLoading] = useState(false);
    const [showExportModal, setShowExportModal] = useState(false);

    // 校对 Modal 状态
    const [reviewVisible, setReviewVisible] = useState(false);
    const [pendingTodos, setPendingTodos] = useState<any[]>([]);

    useLayoutEffect(() => {
        navigation.setOptions({
            title: '待办管理',
            headerStyle: { backgroundColor: theme.background },
            headerTintColor: theme.text,
            headerShadowVisible: false,
        });
    }, [navigation, theme]);

    const todoCount = todoList.length;

    // --- 1. 删除全部待办逻辑 ---
    const handleDeleteAll = () => {
        if (todoCount === 0) {
            Alert.alert('提示', '当前没有待办事项');
            return;
        }
        Alert.alert(
            '清空所有待办',
            '确定要删除所有待办事项吗？此操作不可恢复。',
            [
                { text: '取消', style: 'cancel' },
                {
                    text: '确定清空',
                    style: 'destructive',
                    onPress: () => {
                        setTodoList([]);
                        Alert.alert('成功', '已清空所有待办事项');
                    }
                }
            ]
        );
    };

    // --- 导出逻辑 ---
    const handleExport = () => {
        const json = exportTodoData();
        setJsonContent(json);
        setShowExportModal(true);
    };

    const copyJson = async () => { await Clipboard.setStringAsync(jsonContent); Alert.alert('成功', '已复制'); };

    // 🔥 通用保存文件逻辑 (移植自 ScheduleManagementPage)
    const saveFileGeneric = async (fileName: string, content: string) => {
        if (Platform.OS === 'android') {
            try {
                const permissions = await StorageAccessFramework.requestDirectoryPermissionsAsync();
                if (permissions.granted) {
                    setIsGlobalLoading(true);
                    const uri = await StorageAccessFramework.createFileAsync(permissions.directoryUri, fileName, 'application/json');
                    await FileSystem.writeAsStringAsync(uri, content, { encoding: FileSystem.EncodingType.UTF8 });
                    Alert.alert('成功', '文件已保存');
                }
            } catch (e) {
                Alert.alert('失败', '保存文件失败');
            } finally {
                setIsGlobalLoading(false);
                setShowExportModal(false);
            }
        } else {
            // iOS Fallback
            shareJsonFile();
        }
    };

    // 🔥 2. 保存 JSON 文件
    const saveJsonFile = async () => {
        const fileName = `todo_backup_${Date.now()}.json`;
        await saveFileGeneric(fileName, jsonContent);
    };

    const shareJsonFile = async () => {
        const fileUri = FileSystem.cacheDirectory + `todo_backup_${Date.now()}.json`;
        try {
            await FileSystem.writeAsStringAsync(fileUri, jsonContent);
            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(fileUri, { mimeType: 'application/json', dialogTitle: '分享待办备份' });
            }
        } catch (e) { Alert.alert('失败', '分享失败'); }
    };

    // --- 导入逻辑 ---
    const processImport = (data: any) => {
        if (!data.todos || !Array.isArray(data.todos)) {
            Alert.alert('格式错误', 'JSON 文件中未找到有效的 todos 数组');
            return;
        }
        setPendingTodos(data.todos);
        setReviewVisible(true);
    };

    const handleImportText = async () => {
        if (!importText.trim()) return;
        setIsGlobalLoading(true);
        try {
            await new Promise(r => setTimeout(r, 100));
            const cleanStr = importText.replace(/```json/g, '').replace(/```/g, '').trim();
            const data = JSON.parse(cleanStr);
            processImport(data);
        } catch (e) { Alert.alert('解析失败', '请检查 JSON 格式是否正确'); }
        finally { setIsGlobalLoading(false); }
    };

    const handleImportFile = async () => {
        try {
            const res = await DocumentPicker.getDocumentAsync({ type: ['application/json', 'text/plain'] });
            if (!res.canceled && res.assets && res.assets.length > 0) {
                setIsGlobalLoading(true);
                const content = await FileSystem.readAsStringAsync(res.assets[0].uri);
                const data = JSON.parse(content);
                processImport(data);
            }
        } catch (e) { Alert.alert('读取失败', '文件读取出错'); }
        finally { setIsGlobalLoading(false); }
    };

    const handleConfirmImport = (finalTodos: any[]) => {
        batchAddTodos(finalTodos);
        setReviewVisible(false);
        setImportText('');
        Alert.alert('导入成功', `成功导入 ${finalTodos.length} 个事项`);
    };

    return (
        <View style={[styles.container, { backgroundColor: theme.background }]}>
            <StatusBar barStyle={theme.dark ? 'light-content' : 'dark-content'} backgroundColor={theme.background} />

            <ScrollView contentContainerStyle={{ padding: 20 }}>
                {/* 统计卡片 */}
                <Surface style={[styles.statCard, { backgroundColor: theme.card }]} elevation={2}>
                    <Text style={{ color: theme.subText, fontSize: 14 }}>当前一共添加事项</Text>
                    <Text style={{ color: theme.primary, fontSize: 48, fontWeight: 'bold' }}>{todoCount}</Text>
                    <Text style={{ color: theme.subText, fontSize: 12 }}>(不含课程)</Text>
                </Surface>

                {/* 🔥 新增：删除全部按钮 */}
                <Button
                    mode="contained"
                    icon="delete-outline"
                    onPress={handleDeleteAll}
                    buttonColor="#FF5252"
                    style={{ marginBottom: 15 }}
                >
                    清空所有待办
                </Button>

                <Button mode="contained" icon="export" onPress={handleExport} buttonColor={theme.primary} style={{ marginBottom: 20 }}>
                    导出备份 (JSON)
                </Button>

                <Surface style={[styles.card, { backgroundColor: theme.card }]} elevation={1}>
                    <Text style={[styles.cardTitle, { color: theme.text }]}>导入数据</Text>
                    <SegmentedButtons
                        value={importMode}
                        onValueChange={setImportMode}
                        buttons={[
                            { value: 'text', label: '文本输入' },
                            { value: 'file', label: '文件导入' },
                        ]}
                        style={{ marginBottom: 15 }}
                        theme={{ colors: { secondaryContainer: theme.primary, onSecondaryContainer: '#fff', onSurface: theme.text, outline: theme.border } }}
                    />

                    {importMode === 'text' ? (
                        <>
                            <TextInput
                                label="粘贴 JSON 内容"
                                value={importText}
                                onChangeText={setImportText}
                                mode="outlined"
                                multiline
                                numberOfLines={6}
                                style={{ backgroundColor: theme.background, marginBottom: 15, maxHeight: 200 }}
                                textColor={theme.text}
                                theme={{ colors: { primary: theme.primary, onSurfaceVariant: theme.subText } }}
                            />
                            <Button mode="contained" onPress={handleImportText} buttonColor={theme.primary}>解析并预览</Button>
                        </>
                    ) : (
                        <View style={{ alignItems: 'center', padding: 20 }}>
                            <Button mode="outlined" icon="file-document-outline" onPress={handleImportFile} textColor={theme.text} style={{ width: '100%' }}>
                                选择 JSON 文件
                            </Button>
                        </View>
                    )}
                </Surface>
            </ScrollView>

            {/* 导出弹窗 */}
            <Portal>
                <Modal visible={showExportModal} onDismiss={() => setShowExportModal(false)} contentContainerStyle={{ padding: 20, alignItems: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '85%' }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>导出成功</Text>
                        <Text style={{ color: theme.subText, marginBottom: 20, textAlign: 'center' }}>您可以复制内容或直接分享文件。</Text>
                        <View style={{ gap: 10 }}>
                            <Button mode="contained" icon="content-copy" onPress={copyJson} buttonColor={theme.primary}>复制 JSON</Button>

                            {/* 🔥 新增：另存为按钮 */}
                            <Button mode="contained" icon="content-save" onPress={saveJsonFile} buttonColor={theme.primary}>另存为...</Button>

                            <Button mode="outlined" icon="share-variant" onPress={shareJsonFile} textColor={theme.text}>分享文件</Button>
                        </View>
                    </Surface>
                </Modal>
            </Portal>

            {isGlobalLoading && (
                <Portal>
                    <View style={styles.loadingOverlay}>
                        <Surface style={[styles.loadingContainer, { backgroundColor: theme.card }]}>
                            <ActivityIndicator size="large" color={theme.primary} />
                            <Text style={{ marginTop: 10, color: theme.text }}>处理中...</Text>
                        </Surface>
                    </View>
                </Portal>
            )}

            {/* 导入校对 Modal */}
            <TodoImportReviewModal
                visible={reviewVisible}
                onDismiss={() => setReviewVisible(false)}
                onConfirm={handleConfirmImport}
                initialTodos={pendingTodos}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    statCard: { padding: 30, borderRadius: 16, alignItems: 'center', marginBottom: 20 },
    card: { padding: 20, borderRadius: 12 },
    cardTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 15 },
    loadingOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.3)', justifyContent: 'center', alignItems: 'center' },
    loadingContainer: { padding: 20, borderRadius: 12, alignItems: 'center' }
});