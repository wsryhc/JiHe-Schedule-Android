// src/pages/ScheduleCreatePage.tsx
import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Platform, ActivityIndicator, Alert, KeyboardAvoidingView, TouchableOpacity, StatusBar } from 'react-native';
import { Surface, TextInput, Button, SegmentedButtons, Divider, IconButton, Portal, Modal } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';
import * as FileSystem from 'expo-file-system/legacy';
import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import TextRecognition, { TextRecognitionScript } from '@react-native-ml-kit/text-recognition';
import * as Clipboard from 'expo-clipboard';

import { AI_PROMPT_TEMPLATE, getMonday, formatDate, parseOcrResultToSchedule } from '../utils/scheduleHelpers';
import { ImportReviewModal } from '../components/ImportReviewModal'; // 🔥 引用新组件

export default function ScheduleCreatePage({ navigation }: any) {
    const { theme } = useTheme();
    const { createSchedule, importScheduleData, periodConfig } = useSchedule();

    const [createTab, setCreateTab] = useState('new');
    const [name, setName] = useState('');
    const [startDate, setStartDate] = useState(getMonday(new Date()));
    const [totalWeeks, setTotalWeeks] = useState(25);
    const [showWeekSelector, setShowWeekSelector] = useState(false);

    const [importJson, setImportJson] = useState('');
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [isGlobalLoading, setIsGlobalLoading] = useState(false);

    // 🔥 新增：审核 Modal 状态
    const [reviewVisible, setReviewVisible] = useState(false);
    const [reviewCourses, setReviewCourses] = useState<any[]>([]);
    const [reviewTotalWeeks, setReviewTotalWeeks] = useState(25);
    const [reviewMode, setReviewMode] = useState<'json' | 'ocr'>('json');
    const [pendingImportInfo, setPendingImportInfo] = useState<any>(null); // 暂存 JSON 的 info 信息

    const inputTheme = { colors: { primary: theme.primary, background: theme.card, onSurface: theme.text, onSurfaceVariant: theme.subText } };

    const handleSuccess = () => {
        navigation.goBack();
    };

    const adjustStartDate = (weeks: number) => {
        const newDate = new Date(startDate);
        newDate.setDate(newDate.getDate() + (weeks * 7));
        setStartDate(newDate);
    };

    const handleCreateNew = () => {
        if (!name.trim()) { Alert.alert('提示', '请输入课表名称'); return; }
        createSchedule(name, formatDate(startDate), totalWeeks);
        handleSuccess();
    };

    // 🔥 辅助：简单冲突检测 (用于决定 JSON 是否直接导入)
    const hasConflicts = (courses: any[]) => {
        for (let i = 0; i < courses.length; i++) {
            for (let j = i + 1; j < courses.length; j++) {
                const a = courses[i];
                const b = courses[j];
                if (a.day === b.day &&
                    (a.startPeriod <= b.endPeriod) && (b.startPeriod <= a.endPeriod)) {
                    // 检查周数
                    if (a.weeks.some((w: number) => b.weeks.includes(w))) return true;
                }
            }
        }
        return false;
    };

    const processJsonImport = (data: any) => {
        if (!data.info) data.info = { name: '导入课表' };
        const rawDate = data.info.termStartDate ? new Date(data.info.termStartDate) : new Date();
        data.info.termStartDate = formatDate(getMonday(rawDate));

        const courses = data.courses || [];
        const tWeeks = data.info.totalWeeks || 25;

        // 🔥 1. 预检测冲突
        if (hasConflicts(courses)) {
            // 有冲突 -> 打开审核页面
            setReviewCourses(courses);
            setReviewTotalWeeks(tWeeks);
            setPendingImportInfo(data.info);
            setReviewMode('json');
            setReviewVisible(true);
        } else {
            // 无冲突 -> 直接导入
            if (importScheduleData(JSON.stringify(data))) {
                Alert.alert('成功', '导入成功', [{ text: '确定', onPress: handleSuccess }]);
            } else {
                Alert.alert('失败', '格式错误');
            }
        }
    };

    const handleImportText = async () => {
        if (!importJson) return;
        setIsGlobalLoading(true);
        try {
            await new Promise(r => setTimeout(r, 100));
            const cleanJsonStr = importJson.replace(/```json/g, '').replace(/```/g, '').trim();
            const data = JSON.parse(cleanJsonStr);
            processJsonImport(data);
        } catch (e) { Alert.alert('失败', 'JSON 解析错误'); }
        finally { setIsGlobalLoading(false); }
    };

    const handleImportFile = async () => {
        try {
            const res = await DocumentPicker.getDocumentAsync({ type: ['application/json', 'text/plain'] });
            if (!res.canceled && res.assets && res.assets.length > 0) {
                setIsGlobalLoading(true);
                const content = await FileSystem.readAsStringAsync(res.assets[0].uri);
                const data = JSON.parse(content);
                processJsonImport(data);
            }
        } catch (e) { Alert.alert('错误', '读取文件出错'); }
        finally { setIsGlobalLoading(false); }
    };

    const handleImportFromImage = async () => {
        try {
            const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ImagePicker.MediaTypeOptions.Images, allowsEditing: true, quality: 1 });
            if (result.canceled || !result.assets) return;
            setIsAnalyzing(true);
            setIsGlobalLoading(true);
            try {
                const ocrResult = await TextRecognition.recognize(result.assets[0].uri, TextRecognitionScript.CHINESE);
                const scheduleData = parseOcrResultToSchedule(ocrResult.blocks);

                if (scheduleData && scheduleData.courses.length > 0) {
                    // 🔥 OCR 模式 -> 始终进入审核页面
                    setReviewCourses(scheduleData.courses);
                    setReviewTotalWeeks(25); // OCR 默认 25 周，可在页面改
                    setPendingImportInfo({ name: 'OCR 导入', termStartDate: formatDate(getMonday(new Date())) });
                    setReviewMode('ocr');
                    setReviewVisible(true);
                } else {
                    Alert.alert('未识别到课程');
                }
            } catch (e) { throw e; }
        } catch (e: any) { Alert.alert('错误', e.message); }
        finally { setIsAnalyzing(false); setIsGlobalLoading(false); }
    };

    // 🔥 最终确认导入 (来自 Modal)
    const handleConfirmReviewImport = (finalCourses: any[], finalTotalWeeks: number) => {
        const finalData = {
            info: {
                ...pendingImportInfo,
                totalWeeks: finalTotalWeeks
            },
            courses: finalCourses
        };

        if (importScheduleData(JSON.stringify(finalData))) {
            setReviewVisible(false);
            Alert.alert('成功', '导入成功', [{ text: '确定', onPress: handleSuccess }]);
        } else {
            Alert.alert('保存失败');
        }
    };

    const copyAiPrompt = async () => {
        await Clipboard.setStringAsync(AI_PROMPT_TEMPLATE);
        Alert.alert('成功', '提示词已复制');
    };

    const renderWeekSelectorButton = () => (
        <View style={{ marginBottom: 20 }}>
            <Text style={{ color: theme.subText, marginBottom: 5 }}>学期总周数</Text>
            <TouchableOpacity onPress={() => setShowWeekSelector(true)} activeOpacity={0.7}>
                <View style={{
                    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                    borderWidth: 1, borderColor: theme.border, borderRadius: 8, padding: 12, backgroundColor: theme.background
                }}>
                    <Text style={{ fontSize: 16, color: theme.text }}>共 <Text style={{ color: theme.primary, fontWeight: 'bold', fontSize: 18 }}>{totalWeeks}</Text> 周</Text>
                    <Ionicons name="chevron-down" size={20} color={theme.subText} />
                </View>
            </TouchableOpacity>
        </View>
    );

    return (
        <View style={{ flex: 1, backgroundColor: theme.background }}>
            <StatusBar barStyle={theme.dark ? 'light-content' : 'dark-content'} backgroundColor={theme.background} translucent={false} />

            <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} style={{ flex: 1 }}>
                <ScrollView contentContainerStyle={{ padding: 20 }}>
                    <Surface style={[styles.card, { backgroundColor: theme.card }]} elevation={2}>
                        <Text style={[styles.title, { color: theme.text }]}>添加课表</Text>

                        <SegmentedButtons
                            value={createTab}
                            onValueChange={setCreateTab}
                            buttons={[{ value: 'new', label: '创建空白' }, { value: 'import', label: '导入数据' }]}
                            style={{ marginBottom: 20 }}
                            theme={{ colors: { secondaryContainer: theme.primary, onSecondaryContainer: '#fff', onSurface: theme.text, outline: theme.border } }}
                        />

                        {createTab === 'new' ? (
                            <>
                                <TextInput label="课表名称" value={name} onChangeText={setName} mode="outlined" style={styles.input} textColor={theme.text} theme={inputTheme} />
                                <View style={{ marginBottom: 15 }}>
                                    <Text style={{ color: theme.subText, marginBottom: 5 }}>开学日期 (本周一)</Text>
                                    <View style={[styles.dateRow, { borderColor: theme.border }]}>
                                        <IconButton icon="chevron-left" onPress={() => adjustStartDate(-1)} iconColor={theme.text} />
                                        <Text style={{ color: theme.primary, fontWeight: 'bold', fontSize: 16 }}>{formatDate(startDate)}</Text>
                                        <IconButton icon="chevron-right" onPress={() => adjustStartDate(1)} iconColor={theme.text} />
                                    </View>
                                </View>
                                {renderWeekSelectorButton()}
                                <Button mode="contained" onPress={handleCreateNew} buttonColor={theme.primary} style={{ marginTop: 10, elevation: 0 }}>立即创建</Button>
                            </>
                        ) : (
                            <>
                                <Button mode="outlined" icon="file-document" onPress={handleImportFile} style={{ marginBottom: 15 }} textColor={theme.text}>
                                    从 JSON 文件导入
                                </Button>
                                <Divider style={{ marginBottom: 15 }} />
                                <Text style={{ color: theme.subText, marginBottom: 5 }}>或者粘贴 JSON 数据：</Text>
                                <TextInput
                                    label="JSON 内容"
                                    value={importJson}
                                    onChangeText={setImportJson}
                                    mode="outlined"
                                    multiline
                                    numberOfLines={10}
                                    style={[styles.input, { minHeight: 200, textAlignVertical: 'top' }]}
                                    textColor={theme.text}
                                    theme={inputTheme}
                                    placeholder='请在此处粘贴 JSON...'
                                />
                                <Button mode="contained" onPress={handleImportText} buttonColor={theme.primary} style={{ marginBottom: 20, elevation: 0 }}>
                                    确认导入文本
                                </Button>
                                <Divider style={{ marginBottom: 15 }} />
                                <TouchableOpacity onPress={copyAiPrompt} activeOpacity={0.8} style={{ marginBottom: 15 }}>
                                    <Surface style={[styles.aiButton, { backgroundColor: theme.primary + '15', borderColor: theme.primary }]}>
                                        <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
                                            <Ionicons name="sparkles" size={24} color={theme.primary} style={{ marginRight: 8 }} />
                                            <Text style={{ color: theme.primary, fontWeight: 'bold', fontSize: 16 }}>AI 智能导入 (推荐)</Text>
                                        </View>
                                        <Text style={{ color: theme.text, fontSize: 13, lineHeight: 22 }}>
                                            1. 点击复制专用提示词{'\n'}2. 打开 AI (如 ChatGPT)，发送课表截图{'\n'}3. 复制 AI 返回的 JSON 代码并粘贴到上方
                                        </Text>
                                    </Surface>
                                </TouchableOpacity>
                                <Button mode="outlined" icon="camera" onPress={handleImportFromImage} loading={isAnalyzing} textColor={theme.text}>
                                    本地 OCR 识别 (精度较低)
                                </Button>
                            </>
                        )}
                    </Surface>
                </ScrollView>
            </KeyboardAvoidingView>

            <Portal>
                <Modal visible={showWeekSelector} onDismiss={() => setShowWeekSelector(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 12, backgroundColor: theme.card, width: '85%', maxWidth: 350, maxHeight: '80%' }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>选择总周数</Text>
                        <ScrollView style={{ maxHeight: 300 }} showsVerticalScrollIndicator={false}>
                            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, justifyContent: 'center' }}>
                                {Array.from({ length: 48 }, (_, i) => i + 1).map((w) => (
                                    <TouchableOpacity
                                        key={w}
                                        onPress={() => { setTotalWeeks(w); setShowWeekSelector(false); }}
                                        style={{
                                            width: 45, height: 45, borderRadius: 8,
                                            backgroundColor: totalWeeks === w ? theme.primary : theme.background,
                                            borderWidth: 1, borderColor: totalWeeks === w ? theme.primary : theme.border,
                                            justifyContent: 'center', alignItems: 'center'
                                        }}
                                    >
                                        <Text style={{ color: totalWeeks === w ? '#fff' : theme.text, fontWeight: 'bold' }}>{w}</Text>
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

            {/* 🔥 替换为新的 Review 组件 */}
            <ImportReviewModal
                visible={reviewVisible}
                onDismiss={() => setReviewVisible(false)}
                onConfirm={handleConfirmReviewImport}
                initialCourses={reviewCourses}
                initialTotalWeeks={reviewTotalWeeks}
                mode={reviewMode}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    card: { padding: 20, borderRadius: 12 },
    title: { fontSize: 20, fontWeight: 'bold', marginBottom: 20, textAlign: 'center' },
    input: { marginBottom: 15, backgroundColor: 'transparent' },
    dateRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderWidth: 1, borderRadius: 8, paddingHorizontal: 5, marginBottom: 10 },
    aiButton: { padding: 20, borderRadius: 12, borderWidth: 1, width: '100%' },
    loadingOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0, 0, 0, 0.4)', justifyContent: 'center', alignItems: 'center', zIndex: 9999 },
    loadingContainer: { padding: 25, borderRadius: 16, alignItems: 'center', width: 160 },
});