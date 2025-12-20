// src/pages/SettingsPage.tsx
import React, { useState, useLayoutEffect } from 'react';
import { View, Text, StyleSheet, Switch, TouchableOpacity, ScrollView, Alert, Image, StatusBar, Linking } from 'react-native';
import { Modal, Portal, Button, List, Checkbox, IconButton, Surface, SegmentedButtons, Divider } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule } from '../context/ScheduleContext';
import { useNavigation } from '@react-navigation/native';
import * as ImagePicker from 'expo-image-picker';
import { Ionicons } from '@expo/vector-icons';

export default function SettingsPage() {
    const {
        themeMode, setThemeMode,
        changePrimaryColor, primaryColor, theme,
        customSettings, updateCustomSettings
    } = useTheme();
    const {
        weekendConfig, updateWeekendConfig,
        periodConfig, updatePeriodConfig,
        displayConfig, updateDisplayConfig, resetToDefault,
        currentSchedule
    } = useSchedule();
    const navigation = useNavigation<any>();

    const [periodModalVisible, setPeriodModalVisible] = useState(false);
    const [customModalVisible, setCustomModalVisible] = useState(false);

    const [opacityModalVisible, setOpacityModalVisible] = useState(false);
    // 🔥 增加 remindItem 和 remindCalendar 目标
    const [opacityTarget, setOpacityTarget] = useState<'bg' | 'border' | 'course' | 'remindBg' | 'remindItem' | 'remindCalendar' | null>(null);
    const [tempOpacity, setTempOpacity] = useState(0);

    useLayoutEffect(() => {
        navigation.setOptions({
            title: '设置',
            headerStyle: { backgroundColor: theme.card },
            headerTintColor: theme.text,
            headerShadowVisible: false,
        });
    }, [navigation, theme]);

    const pickBackgroundImage = async (target: 'index' | 'remind') => {
        try {
            const result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                aspect: [9, 16],
                quality: 1,
            });

            if (!result.canceled && result.assets[0].uri) {
                if (target === 'index') updateCustomSettings({ backgroundImage: result.assets[0].uri });
                else updateCustomSettings({ remindBackgroundImage: result.assets[0].uri });
            }
        } catch (e) {
            Alert.alert("错误", "无法打开图库，请检查权限");
        }
    };

    const openOpacityModal = (target: 'bg' | 'border' | 'course' | 'remindBg' | 'remindItem' | 'remindCalendar') => {
        setOpacityTarget(target);
        let currentVal = 0;
        if (target === 'bg') currentVal = customSettings.bgImageOpacity ?? 1;
        if (target === 'border') currentVal = customSettings.borderOpacity ?? 0.1;
        if (target === 'course') currentVal = customSettings.courseOpacity ?? 0.85;
        if (target === 'remindBg') currentVal = customSettings.remindBgImageOpacity ?? 1;
        // 🔥 新增
        if (target === 'remindItem') currentVal = customSettings.remindItemOpacity ?? 0.85;
        if (target === 'remindCalendar') currentVal = customSettings.remindCalendarCellOpacity ?? 0.1;

        setTempOpacity(Math.round(currentVal * 100));
        setOpacityModalVisible(true);
    };

    const saveOpacity = () => {
        const val = tempOpacity / 100;
        if (opacityTarget === 'bg') updateCustomSettings({ bgImageOpacity: val });
        if (opacityTarget === 'border') updateCustomSettings({ borderOpacity: val });
        if (opacityTarget === 'course') updateCustomSettings({ courseOpacity: val });
        if (opacityTarget === 'remindBg') updateCustomSettings({ remindBgImageOpacity: val });
        // 🔥 新增保存
        if (opacityTarget === 'remindItem') updateCustomSettings({ remindItemOpacity: val });
        if (opacityTarget === 'remindCalendar') updateCustomSettings({ remindCalendarCellOpacity: val });

        setOpacityModalVisible(false);
    };

    const adjustValue = (delta: number) => {
        setTempOpacity(prev => {
            const newVal = prev + delta;
            if (newVal < 0) return 0;
            if (newVal > 100) return 100;
            return newVal;
        });
    };

    const OpacityRow = ({ label, value, target, icon }: any) => {
        const percent = Math.round(value * 100);
        return (
            <TouchableOpacity onPress={() => openOpacityModal(target)} activeOpacity={0.6}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <IconButton icon={icon} size={20} iconColor={theme.subText} style={{ margin: 0, marginRight: 8 }} />
                        <Text style={{ color: theme.text, fontSize: 16 }}>{label}</Text>
                    </View>
                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Text style={{ color: theme.primary, fontSize: 16, fontWeight: 'bold', marginRight: 5 }}>{percent}%</Text>
                        <List.Icon icon="chevron-right" color={theme.subText} />
                    </View>
                </View>
            </TouchableOpacity>
        );
    };

    const PeriodSelector = ({ label, value, onChange }: any) => {
        const options = [0, 1, 2, 3, 4, 5, 6];
        return (
            <View style={{ marginBottom: 25 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <Text style={{ color: theme.text, fontSize: 16, fontWeight: 'bold' }}>{label}</Text>
                    <Text style={{ color: theme.primary, fontSize: 14 }}>{value} 节</Text>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    {options.map((num) => {
                        const isSelected = value === num;
                        return (
                            <TouchableOpacity
                                key={num} onPress={() => onChange(num)}
                                style={[styles.numBtn, { backgroundColor: isSelected ? theme.primary : theme.background, borderColor: isSelected ? theme.primary : theme.border, borderWidth: 1 }]}
                            >
                                <Text style={{ color: isSelected ? '#fff' : theme.text, fontWeight: isSelected ? 'bold' : 'normal' }}>{num}</Text>
                            </TouchableOpacity>
                        );
                    })}
                </View>
            </View>
        );
    };

    const toggleDisplay = (target: 'inApp' | 'widget', type: 'showTodo' | 'showCourse') => {
        updateDisplayConfig({ ...displayConfig, [target]: { ...displayConfig[target], [type]: !displayConfig[target][type] } });
    };

    const handleReset = () => {
        Alert.alert('重置所有数据', '确定要清空所有课表、待办和设置吗？此操作不可恢复。', [
            { text: '取消', style: 'cancel' },
            { text: '确定重置', style: 'destructive', onPress: resetToDefault }
        ]);
    };

    const openRepo = () => {
        Linking.openURL('https://github.com/wsryhc/JiHe-Schedule');
    };

    const getOpacityTitle = () => {
        switch (opacityTarget) {
            case 'bg': return '调节课程表背景不透明度';
            case 'border': return '调节边框不透明度';
            case 'course': return '调节色块不透明度';
            case 'remindBg': return '调节日程页背景不透明度';
            case 'remindItem': return '调节事项区域透明度';
            case 'remindCalendar': return '调节日历背景透明度';
            default: return '调节透明度';
        }
    };

    return (
        <ScrollView style={[styles.container, { backgroundColor: theme.background }]} contentContainerStyle={{ paddingBottom: 50 }}>
            <StatusBar
                key={themeMode}
                barStyle={theme.dark ? 'light-content' : 'dark-content'}
                backgroundColor={theme.background}
                translucent={false}
            />

            {/* 1. 管理 */}
            <Text style={[styles.sectionTitle, { color: theme.subText }]}>管理</Text>
            <View style={[styles.card, { backgroundColor: theme.card, marginBottom: 20 }]}>
                <List.Item
                    title="课表管理"
                    description="切换学期与课表"
                    left={props => <List.Icon {...props} icon="calendar-edit" color={theme.primary} />}
                    right={props => <List.Icon {...props} icon="chevron-right" color={theme.subText} />}
                    onPress={() => navigation.navigate('ScheduleManagement')}
                    titleStyle={{ color: theme.text }} descriptionStyle={{ color: theme.subText }}
                />
                <Divider style={{ backgroundColor: theme.border }} />
                <List.Item
                    title="待办管理"
                    description="导出、导入待办数据"
                    left={props => <List.Icon {...props} icon="checkbox-multiple-marked-outline" color={theme.primary} />}
                    right={props => <List.Icon {...props} icon="chevron-right" color={theme.subText} />}
                    onPress={() => navigation.navigate('TodoManagement')}
                    titleStyle={{ color: theme.text }} descriptionStyle={{ color: theme.subText }}
                />
                <Divider style={{ backgroundColor: theme.border }} />
                <List.Item
                    title="消息提醒"
                    description="设置上课提醒与待办通知"
                    left={props => <List.Icon {...props} icon="bell-outline" color={theme.primary} />}
                    right={props => <List.Icon {...props} icon="chevron-right" color={theme.subText} />}
                    onPress={() => navigation.navigate('NotificationSetting')}
                    titleStyle={{ color: theme.text }} descriptionStyle={{ color: theme.subText }}
                />
            </View>

            {/* 2. 个性化 */}
            {currentSchedule && (
                <>
                    {/* A. 课程表个性化 */}
                    <Text style={[styles.sectionTitle, { color: theme.subText }]}>🎨 课程表个性化</Text>
                    <View style={[styles.card, { backgroundColor: theme.card, padding: 15, marginBottom: 20 }]}>
                        {/* 背景图 */}
                        <View style={{ marginBottom: 5 }}>
                            <Text style={[styles.optionText, { color: theme.text, marginBottom: 10 }]}>🖼️ 课程表背景</Text>
                            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                                {customSettings.backgroundImage ? (
                                    <Image source={{ uri: customSettings.backgroundImage }} style={{ width: 60, height: 60, borderRadius: 8, borderWidth: 1, borderColor: theme.border }} />
                                ) : (
                                    <View style={{ width: 60, height: 60, borderRadius: 8, backgroundColor: theme.background, justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: theme.border }}>
                                        <Text style={{ color: theme.subText, fontSize: 10 }}>无背景</Text>
                                    </View>
                                )}
                                <View style={{ flex: 1, flexDirection: 'row', gap: 10 }}>
                                    <Button mode="contained" onPress={() => pickBackgroundImage('index')} buttonColor={theme.primary} compact>
                                        {customSettings.backgroundImage ? '更换图片' : '选择图片'}
                                    </Button>
                                    {customSettings.backgroundImage && (
                                        <Button mode="outlined" onPress={() => updateCustomSettings({ backgroundImage: null })} textColor={theme.text} compact>
                                            清除
                                        </Button>
                                    )}
                                </View>
                            </View>
                        </View>

                        {customSettings.backgroundImage && (
                            <OpacityRow label="背景图不透明度" icon="image-outline" value={customSettings.bgImageOpacity ?? 1} target="bg" />
                        )}

                        <Divider style={{ marginVertical: 5 }} />
                        <OpacityRow label="表格边框不透明度" icon="grid" value={customSettings.borderOpacity ?? 0.1} target="border" />
                        <Divider style={{ marginVertical: 5 }} />
                        <OpacityRow label="课程色块不透明度" icon="cube-outline" value={customSettings.courseOpacity ?? 0.85} target="course" />

                        {customSettings.backgroundImage && (
                            <>
                                <Divider style={{ marginVertical: 5 }} />
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 5 }}>
                                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                                        <IconButton icon="page-layout-header" size={20} iconColor={theme.subText} style={{ margin: 0, marginRight: 8 }} />
                                        <View>
                                            <Text style={[styles.optionText, { color: theme.text, fontSize: 16 }]}>沉浸式头部导航</Text>
                                            <Text style={{ color: theme.subText, fontSize: 11 }}>主页内容延伸至顶部</Text>
                                        </View>
                                    </View>
                                    <Switch value={customSettings.transparentHeader} onValueChange={(val) => updateCustomSettings({ transparentHeader: val })} trackColor={{ false: "#ccc", true: theme.primary }} />
                                </View>

                                <Divider style={{ marginVertical: 5 }} />
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 5 }}>
                                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                                        <IconButton icon="format-color-text" size={20} iconColor={theme.subText} style={{ margin: 0, marginRight: 8 }} />
                                        <View>
                                            <Text style={[styles.optionText, { color: theme.text, fontSize: 16 }]}>深色背景适配</Text>
                                            <Text style={{ color: theme.subText, fontSize: 11 }}>强制主页文字和状态栏为白色</Text>
                                        </View>
                                    </View>
                                    <Switch
                                        value={customSettings.forceWhiteContent}
                                        onValueChange={(val) => updateCustomSettings({ forceWhiteContent: val })}
                                        trackColor={{ false: "#ccc", true: theme.primary }}
                                    />
                                </View>
                            </>
                        )}
                    </View>

                    {/* B. 日程页个性化 (新) */}
                    <Text style={[styles.sectionTitle, { color: theme.subText }]}>📅 日程页个性化</Text>
                    <View style={[styles.card, { backgroundColor: theme.card, padding: 15, marginBottom: 20 }]}>
                        {/* 背景图 */}
                        <View style={{ marginBottom: 5 }}>
                            <Text style={[styles.optionText, { color: theme.text, marginBottom: 10 }]}>🖼️ 日程页背景</Text>
                            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                                {customSettings.remindBackgroundImage ? (
                                    <Image source={{ uri: customSettings.remindBackgroundImage }} style={{ width: 60, height: 60, borderRadius: 8, borderWidth: 1, borderColor: theme.border }} />
                                ) : (
                                    <View style={{ width: 60, height: 60, borderRadius: 8, backgroundColor: theme.background, justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: theme.border }}>
                                        <Text style={{ color: theme.subText, fontSize: 10 }}>无背景</Text>
                                    </View>
                                )}
                                <View style={{ flex: 1, flexDirection: 'row', gap: 10 }}>
                                    <Button mode="contained" onPress={() => pickBackgroundImage('remind')} buttonColor={theme.primary} compact>
                                        {customSettings.remindBackgroundImage ? '更换图片' : '选择图片'}
                                    </Button>
                                    {customSettings.remindBackgroundImage && (
                                        <Button mode="outlined" onPress={() => updateCustomSettings({ remindBackgroundImage: null })} textColor={theme.text} compact>
                                            清除
                                        </Button>
                                    )}
                                </View>
                            </View>
                        </View>

                        {customSettings.remindBackgroundImage && (
                            <>
                                <OpacityRow label="背景图不透明度" icon="image-outline" value={customSettings.remindBgImageOpacity ?? 1} target="remindBg" />
                                <Divider style={{ marginVertical: 5 }} />

                                {/* 🔥 新增调节选项 */}
                                <OpacityRow label="事项区域透明度" icon="card-text-outline" value={customSettings.remindItemOpacity ?? 0.85} target="remindItem" />
                                <Divider style={{ marginVertical: 5 }} />
                                <OpacityRow label="日历背景透明度" icon="calendar-blank-outline" value={customSettings.remindCalendarCellOpacity ?? 0.1} target="remindCalendar" />

                                <Divider style={{ marginVertical: 5 }} />
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 5 }}>
                                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                                        <IconButton icon="page-layout-header" size={20} iconColor={theme.subText} style={{ margin: 0, marginRight: 8 }} />
                                        <View>
                                            <Text style={[styles.optionText, { color: theme.text, fontSize: 16 }]}>沉浸式头部导航</Text>
                                            <Text style={{ color: theme.subText, fontSize: 11 }}>日程页内容延伸至顶部</Text>
                                        </View>
                                    </View>
                                    <Switch value={customSettings.remindTransparentHeader} onValueChange={(val) => updateCustomSettings({ remindTransparentHeader: val })} trackColor={{ false: "#ccc", true: theme.primary }} />
                                </View>

                                <Divider style={{ marginVertical: 5 }} />
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 5 }}>
                                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                                        <IconButton icon="format-color-text" size={20} iconColor={theme.subText} style={{ margin: 0, marginRight: 8 }} />
                                        <View>
                                            <Text style={[styles.optionText, { color: theme.text, fontSize: 16 }]}>深色背景适配</Text>
                                            <Text style={{ color: theme.subText, fontSize: 11 }}>强制日程页文字和状态栏为白色</Text>
                                        </View>
                                    </View>
                                    <Switch
                                        value={customSettings.remindForceWhiteContent}
                                        onValueChange={(val) => updateCustomSettings({ remindForceWhiteContent: val })}
                                        trackColor={{ false: "#ccc", true: theme.primary }}
                                    />
                                </View>
                            </>
                        )}
                    </View>
                </>
            )}

            {/* 3. 显示设置 */}
            <Text style={[styles.sectionTitle, { color: theme.subText }]}>待办显示内容设置</Text>
            <View style={[styles.card, { backgroundColor: theme.card, marginBottom: 10, paddingVertical: 10 }]}>
                <View style={{ paddingHorizontal: 16, marginBottom: 8 }}>
                    <Text style={{ color: theme.primary, fontWeight: 'bold' }}>📱 应用内</Text>
                </View>
                <View style={styles.checkRow}>
                    <Text style={{ color: theme.text, fontSize: 15 }}>显示课程</Text>
                    <Checkbox.Android status={displayConfig.inApp.showCourse ? 'checked' : 'unchecked'} onPress={() => toggleDisplay('inApp', 'showCourse')} color={theme.primary} />
                </View>
                <View style={styles.checkRow}>
                    <Text style={{ color: theme.text, fontSize: 15 }}>显示待办</Text>
                    <Checkbox.Android status={displayConfig.inApp.showTodo ? 'checked' : 'unchecked'} onPress={() => toggleDisplay('inApp', 'showTodo')} color={theme.primary} />
                </View>
            </View>

            <Text style={{ color: theme.subText, fontSize: 12, marginBottom: 10, paddingHorizontal: 5, lineHeight: 18 }}>
                桌面组件分为4x2,4x3,3x2,部分系统问题,还提供了显示异常使用的4x2和4x3样式。添加后会是透明的，为正常现象，添加或者修改待办即可刷新。
            </Text>

            <View style={[styles.card, { backgroundColor: theme.card, marginBottom: 20, paddingVertical: 10 }]}>
                <View style={{ paddingHorizontal: 16, marginBottom: 8 }}>
                    <Text style={{ color: theme.primary, fontWeight: 'bold' }}>🧩 桌面小组件</Text>
                </View>
                <View style={styles.checkRow}>
                    <Text style={{ color: theme.text, fontSize: 15 }}>显示课程</Text>
                    <Checkbox.Android status={displayConfig.widget.showCourse ? 'checked' : 'unchecked'} onPress={() => toggleDisplay('widget', 'showCourse')} color={theme.primary} />
                </View>
                <View style={styles.checkRow}>
                    <Text style={{ color: theme.text, fontSize: 15 }}>显示待办</Text>
                    <Checkbox.Android status={displayConfig.widget.showTodo ? 'checked' : 'unchecked'} onPress={() => toggleDisplay('widget', 'showTodo')} color={theme.primary} />
                </View>
            </View>

            {/* 4. 课表参数 */}
            <Text style={[styles.sectionTitle, { color: theme.subText }]}>课表参数</Text>
            <View style={[styles.card, { backgroundColor: theme.card }]}>
                <View style={{ padding: 15 }}>
                    <Text style={[styles.optionText, { color: theme.text, marginBottom: 10 }]}>📅 周末显示</Text>
                    <View style={{ flexDirection: 'row', justifyContent: 'flex-start', gap: 20 }}>
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Checkbox.Android status={weekendConfig.saturday ? 'checked' : 'unchecked'} onPress={() => updateWeekendConfig({ saturday: !weekendConfig.saturday })} color={theme.primary} />
                            <Text style={{ color: theme.text, marginLeft: 0 }}>周六</Text>
                        </View>
                        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Checkbox.Android status={weekendConfig.sunday ? 'checked' : 'unchecked'} onPress={() => updateWeekendConfig({ sunday: !weekendConfig.sunday })} color={theme.primary} />
                            <Text style={{ color: theme.text, marginLeft: 0 }}>周日</Text>
                        </View>
                    </View>
                </View>
            </View>

            <TouchableOpacity onPress={() => setPeriodModalVisible(true)} style={{ marginTop: 10 }}>
                <View style={[styles.card, { backgroundColor: theme.card }]}>
                    <View style={styles.row}>
                        <Text style={[styles.optionText, { color: theme.text }]}>🔢 每天节数设置</Text>
                        <Text style={{ color: theme.subText }}>{periodConfig.morning}+{periodConfig.afternoon}+{periodConfig.evening}</Text>
                    </View>
                </View>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => navigation.navigate('TimeSettings')} style={{ marginTop: 10 }}>
                <View style={[styles.card, { backgroundColor: theme.card }]}>
                    <View style={styles.row}>
                        <Text style={[styles.optionText, { color: theme.text }]}>⏰ 每节课时间设置</Text>
                        <List.Icon icon="chevron-right" color={theme.subText} />
                    </View>
                </View>
            </TouchableOpacity>

            <Text style={[styles.sectionTitle, { color: theme.subText, marginTop: 30 }]}>外观</Text>
            <View style={[styles.card, { backgroundColor: theme.card, padding: 15 }]}>
                <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 15 }}>
                    <Text style={[styles.optionText, { color: theme.text, marginRight: 10 }]}>🌙 夜间模式</Text>
                </View>
                <SegmentedButtons
                    value={themeMode}
                    onValueChange={(val) => setThemeMode(val as any)}
                    buttons={[
                        { value: 'light', label: '浅色', icon: 'weather-sunny' },
                        { value: 'auto', label: '自动', icon: 'theme-light-dark' },
                        { value: 'dark', label: '深色', icon: 'weather-night' },
                    ]}
                    style={{ width: '100%' }}
                    theme={{ colors: { secondaryContainer: theme.primary + '30', onSecondaryContainer: theme.primary, onSurface: theme.text, outline: theme.border } }}
                />
            </View>

            <TouchableOpacity style={[styles.card, { backgroundColor: theme.card, marginTop: 10, padding: 15 }]}>
                <Text style={{ color: theme.text, marginBottom: 10 }}>🎨 主题色</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 15 }}>
                    {['#FF3B30', '#FF9500', '#FFCC00', '#34C759', '#007AFF', '#5856D6', '#FF2D55'].map(c => (
                        <TouchableOpacity
                            key={c}
                            onPress={() => changePrimaryColor(c)}
                            style={{
                                width: 30, height: 30, borderRadius: 15, backgroundColor: c,
                                borderWidth: primaryColor === c ? 3 : 0, borderColor: theme.text
                            }}
                        />
                    ))}
                </View>
            </TouchableOpacity>

            <TouchableOpacity style={[styles.card, { backgroundColor: theme.card, marginTop: 10, padding: 15 }]}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                    <View>
                        <Text style={[styles.optionText, { color: theme.text }]}>未完成的项目</Text>
                        <Text style={{ color: theme.subText, fontSize: 12 }}>查看开发计划</Text>
                    </View>
                    <Button mode="text" onPress={() => setCustomModalVisible(true)} textColor={theme.primary}>查看</Button>
                </View>
            </TouchableOpacity>

            <TouchableOpacity
                onPress={openRepo}
                style={[styles.card, { backgroundColor: theme.card, marginTop: 20, padding: 15, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}
            >
                <Text style={{ color: theme.text, fontSize: 16, fontWeight: 'bold' }}>本项目仓库</Text>
                <Ionicons name="logo-github" size={24} color={theme.text} />
            </TouchableOpacity>

            <View style={{ marginTop: 20, marginBottom: 50 }}>
                <Button mode="outlined" onPress={handleReset} textColor="red" style={{ borderColor: 'red' }}>重置应用数据</Button>
            </View>

            <Portal>
                <Modal visible={periodModalVisible} onDismiss={() => setPeriodModalVisible(false)} contentContainerStyle={[styles.modalBox, { backgroundColor: theme.card }]}>
                    <Text style={[styles.modalTitle, { color: theme.text }]}>设置各时段节数</Text>
                    <PeriodSelector label="☀️ 上午" value={periodConfig.morning} onChange={(v: number) => updatePeriodConfig({ ...periodConfig, morning: v })} />
                    <PeriodSelector label="🌤️ 下午" value={periodConfig.afternoon} onChange={(v: number) => updatePeriodConfig({ ...periodConfig, afternoon: v })} />
                    <PeriodSelector label="🌙 晚上" value={periodConfig.evening} onChange={(v: number) => updatePeriodConfig({ ...periodConfig, evening: v })} />
                    <Button mode="contained" onPress={() => setPeriodModalVisible(false)} buttonColor={theme.primary} style={{ marginTop: 10 }}>完成</Button>
                </Modal>
            </Portal>

            <Portal>
                <Modal visible={opacityModalVisible} onDismiss={() => setOpacityModalVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ padding: 25, borderRadius: 16, backgroundColor: theme.card, width: '85%', maxWidth: 320, alignItems: 'center' }} elevation={4}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 20 }}>
                            {getOpacityTitle()}
                        </Text>
                        <Text style={{ fontSize: 48, fontWeight: 'bold', color: theme.primary, marginBottom: 20 }}>{tempOpacity}%</Text>
                        <View style={{ flexDirection: 'row', gap: 10, marginBottom: 10 }}>
                            <Button mode="outlined" compact onPress={() => adjustValue(-10)} textColor={theme.text}>-10</Button>
                            <Button mode="outlined" compact onPress={() => adjustValue(-1)} textColor={theme.text}>-1</Button>
                            <Button mode="outlined" compact onPress={() => adjustValue(1)} textColor={theme.text}>+1</Button>
                            <Button mode="outlined" compact onPress={() => adjustValue(10)} textColor={theme.text}>+10</Button>
                        </View>
                        <View style={{ flexDirection: 'row', width: '100%', marginTop: 15, gap: 10 }}>
                            <Button mode="text" onPress={() => setOpacityModalVisible(false)} style={{ flex: 1 }} textColor={theme.subText}>取消</Button>
                            <Button mode="contained" onPress={saveOpacity} style={{ flex: 1 }} buttonColor={theme.primary}>确定</Button>
                        </View>
                    </Surface>
                </Modal>
            </Portal>

            <Portal>
                <Modal visible={customModalVisible} onDismiss={() => setCustomModalVisible(false)} contentContainerStyle={[styles.modalBox, { backgroundColor: theme.card }]}>
                    <Text style={[styles.modalTitle, { color: theme.text }]}>项目说明</Text>
                    <ScrollView style={{ maxHeight: 300 }}>
                        <Text style={{ color: theme.text, fontSize: 16, lineHeight: 24 }}>欢迎提出意见,当前测试次数少,可能还存在很多bug。安卓端测试完成即可开发ios和Windows端软件。</Text>
                    </ScrollView>
                    <Button mode="contained" onPress={() => setCustomModalVisible(false)} buttonColor={theme.primary} style={{ marginTop: 20 }}>关闭</Button>
                </Modal>
            </Portal>

        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20 },
    sectionTitle: { fontSize: 14, fontWeight: 'bold', marginBottom: 10, marginLeft: 5 },
    card: { borderRadius: 12, overflow: 'hidden' },
    row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 15 },
    optionText: { fontSize: 16, fontWeight: '600' },
    modalBox: { padding: 20, margin: 20, borderRadius: 12 },
    modalTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 20, textAlign: 'center' },
    checkRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 8 },
    numBtn: { width: 36, height: 36, borderRadius: 18, justifyContent: 'center', alignItems: 'center', elevation: 1 },
});