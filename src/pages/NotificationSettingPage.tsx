// src/pages/NotificationSettingPage.tsx
import React, { useState, useLayoutEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, StatusBar } from 'react-native';
import { List, Switch, Surface, Divider, Button, Portal, Modal, RadioButton } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule } from '../context/ScheduleContext'; // 🔥 引入 Context
import { useNavigation } from '@react-navigation/native';

export default function NotificationSettingPage() {
    const { theme, themeMode } = useTheme();
    const navigation = useNavigation<any>();

    // 🔥 从 Context 获取真实配置
    const { notificationConfig, updateNotificationConfig } = useSchedule();

    const [showTimeModal, setShowTimeModal] = useState(false);

    useLayoutEffect(() => {
        navigation.setOptions({
            title: '消息提醒',
            headerStyle: { backgroundColor: theme.card },
            headerTintColor: theme.text,
            headerShadowVisible: false,
        });
    }, [navigation, theme]);

    const timeOptions = [
        { label: '上课时准点', value: 0 },
        { label: '提前 5 分钟', value: 5 },
        { label: '提前 10 分钟', value: 10 },
        { label: '提前 20 分钟', value: 20 },
        { label: '提前 30 分钟', value: 30 },
    ];

    return (
        <ScrollView style={[styles.container, { backgroundColor: theme.background }]}>
            <StatusBar
                key={themeMode}
                barStyle={theme.dark ? 'light-content' : 'dark-content'}
                backgroundColor={theme.background}
                translucent={false}
            />

            {/* 1. 总开关 */}
            <View style={[styles.card, { backgroundColor: theme.card, marginTop: 10 }]}>
                <List.Item
                    title="允许发送通知"
                    description="关闭后将不会收到任何课程或待办提醒"
                    titleStyle={{ color: theme.text, fontWeight: 'bold' }}
                    descriptionStyle={{ color: theme.subText, fontSize: 12 }}
                    right={() => (
                        <Switch
                            value={notificationConfig.enabled}
                            onValueChange={(val) => updateNotificationConfig({ enabled: val })}
                            color={theme.primary}
                        />
                    )}
                />
            </View>

            {notificationConfig.enabled && (
                <>
                    <Text style={[styles.sectionTitle, { color: theme.subText }]}>课程设置</Text>
                    <View style={[styles.card, { backgroundColor: theme.card }]}>
                        <List.Item
                            title="开启课程提醒"
                            left={props => <List.Icon {...props} icon="school-outline" color={theme.primary} />}
                            right={() => (
                                <Switch
                                    value={notificationConfig.courseRemind}
                                    onValueChange={(val) => updateNotificationConfig({ courseRemind: val })}
                                    color={theme.primary}
                                />
                            )}
                            titleStyle={{ color: theme.text }}
                        />
                        <Divider style={{ backgroundColor: theme.border }} />
                        <TouchableOpacity onPress={() => setShowTimeModal(true)}>
                            <List.Item
                                title="课程提前提醒时间"
                                description={`当前设置: ${notificationConfig.advanceTime === 0 ? '准点提醒' : `提前 ${notificationConfig.advanceTime} 分钟`}`}
                                left={props => <List.Icon {...props} icon="clock-time-four-outline" color={theme.text} />}
                                right={props => <List.Icon {...props} icon="chevron-right" color={theme.subText} />}
                                titleStyle={{ color: theme.text }}
                                descriptionStyle={{ color: theme.primary }}
                            />
                        </TouchableOpacity>
                    </View>

                    <Text style={[styles.sectionTitle, { color: theme.subText }]}>待办事项</Text>
                    <View style={[styles.card, { backgroundColor: theme.card, padding: 15 }]}>
                        <Text style={{ color: theme.text, fontSize: 14 }}>
                            待办事项的提醒时间现在支持**独立设置**。
                        </Text>
                        <Text style={{ color: theme.subText, fontSize: 12, marginTop: 5 }}>
                            请在创建或编辑待办事项时，点击“提醒”选项进行单独配置（支持按分/时/天/周/月提前通知）。
                        </Text>
                    </View>

                    <Text style={{ textAlign: 'center', color: theme.subText, marginTop: 30, fontSize: 12 }}>
                        注：请确保在系统设置中允许应用发送通知，否则设置将不生效。
                    </Text>
                </>
            )}

            {/* 时间选择弹窗 */}
            <Portal>
                <Modal visible={showTimeModal} onDismiss={() => setShowTimeModal(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>
                    <Surface style={{ padding: 20, borderRadius: 16, backgroundColor: theme.card, width: '85%', maxWidth: 320 }}>
                        <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text, marginBottom: 15, textAlign: 'center' }}>选择课程提醒时间</Text>
                        <RadioButton.Group
                            onValueChange={val => {
                                updateNotificationConfig({ advanceTime: Number(val) });
                                setShowTimeModal(false);
                            }}
                            value={String(notificationConfig.advanceTime)}
                        >
                            {timeOptions.map((opt) => (
                                <View key={opt.value} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 8 }}>
                                    <RadioButton.Android value={String(opt.value)} color={theme.primary} />
                                    <Text style={{ fontSize: 16, color: theme.text, marginLeft: 8 }}>{opt.label}</Text>
                                </View>
                            ))}
                        </RadioButton.Group>
                        <Button mode="text" onPress={() => setShowTimeModal(false)} textColor={theme.subText} style={{ marginTop: 10 }}>取消</Button>
                    </Surface>
                </Modal>
            </Portal>

        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20 },
    card: { borderRadius: 12, overflow: 'hidden', marginBottom: 10 },
    sectionTitle: { fontSize: 14, fontWeight: 'bold', marginBottom: 8, marginLeft: 5, marginTop: 15 },
});