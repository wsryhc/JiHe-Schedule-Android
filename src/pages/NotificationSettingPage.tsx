// src/pages/NotificationSettingPage.tsx
import React, { useState, useLayoutEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, StatusBar, Alert } from 'react-native';
import { List, Switch, Surface, Divider, Button, Portal, Modal, RadioButton } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule } from '../context/ScheduleContext';
import { useNavigation } from '@react-navigation/native';

export default function NotificationSettingPage() {
    const { theme, themeMode } = useTheme();
    const navigation = useNavigation<any>();

    // 🔥 从 Context 引入 sendTestNotification
    const { notificationConfig, updateNotificationConfig, sendTestNotification } = useSchedule();

    const [showTimeModal, setShowTimeModal] = useState(false);

    useLayoutEffect(() => {
        navigation.setOptions({
            title: '消息提醒',
            headerStyle: { backgroundColor: theme.card },
            headerTintColor: theme.text,
            headerShadowVisible: false,
        });
    }, [navigation, theme]);

    const handleToggleEnable = async (val: boolean) => {
        updateNotificationConfig({ enabled: val });

        // 🔥 如果是开启操作，立刻发送测试通知并弹窗引导
        if (val) {
            await sendTestNotification(); // 发送立即通知

            Alert.alert(
                "设置指引",
                "请前往应用设置界面，找到通知管理（设置），打开你想要的悬浮，锁屏通知，然后在里面找到Miscellaneous的选项，在里面打开他的通知，按需选择打开悬浮通知，锁屏通知，还有声音。",
                [{ text: "我知道了" }]
            );
        }
    };

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
                            onValueChange={handleToggleEnable} // 🔥 绑定新逻辑
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

                    {/* 🔥 更新后的引导文字 */}
                    <View style={{ padding: 10, marginTop: 20 }}>
                        <Text style={{ color: theme.subText, fontSize: 12, lineHeight: 20 }}>
                            请前往应用设置界面，找到通知管理（设置），打开你想要的悬浮，锁屏通知，然后在里面找到Miscellaneous的选项，在里面打开他的通知，按需选择打开悬浮通知，锁屏通知，还有声音。
                        </Text>
                        <Text style={{ color: theme.subText, fontSize: 12, lineHeight: 20, marginTop: 10 }}>
                            同时请务必在“省电策略”中将本应用设置为“无限制”，并允许“自启动”，否则消息可能无法发出。
                        </Text>
                    </View>
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