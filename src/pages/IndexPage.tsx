import React, { useLayoutEffect, useState, useMemo, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, KeyboardAvoidingView, Platform, ImageBackground, StatusBar } from 'react-native';
import { Portal, Modal, Surface, Button, IconButton, TouchableRipple } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext';
import { useSchedule, Course } from '../context/ScheduleContext';
import { Ionicons } from '@expo/vector-icons';
import { useIsFocused } from '@react-navigation/native';

import ScheduleHeader from '../components/ScheduleHeader';
import ScheduleList from '../components/ScheduleList';

const getMonday = (d: Date) => {
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(d.setDate(diff));
};
const getChineseDate = (date: Date) => `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;

export default function IndexPage({ navigation }: any) {
    const { theme, customSettings } = useTheme();
    const { 
        courseList, currentSchedule, switchSchedule, 
        currentWeek, setCurrentWeek, scheduleList 
    } = useSchedule();
    const isFocused = useIsFocused();

    const [modalVisible, setModalVisible] = useState(false);
    const [selectedSlot, setSelectedSlot] = useState<{ dayIndex: number, periodId: number } | null>(null);
    const [weekModalVisible, setWeekModalVisible] = useState(false); 

    useEffect(() => {
        if (isFocused && !currentSchedule && scheduleList && scheduleList.length > 0) {
            switchSchedule(scheduleList[0].id);
        }
    }, [isFocused, currentSchedule, scheduleList]);

    // 1. 计算真实的当前周（不做限制，可能是负数或超大数，用于判断假期）
    const realCurrentWeek = useMemo(() => {
        if (!currentSchedule) return 1;
        const start = new Date(currentSchedule.termStartDate);
        const now = new Date();
        const diffTime = now.getTime() - start.getTime();
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
        const w = Math.floor(diffDays / 7) + 1;
        return w;
    }, [currentSchedule]);

    const maxWeeks = currentSchedule?.totalWeeks || 25;
    const weekData = useMemo(() => Array.from({ length: maxWeeks }, (_, i) => i + 1), [maxWeeks]);

    // 🔥🔥 新增逻辑：自动校正显示的周次
    // 每次打开页面(isFocused)或课表切换时，检查当前时间是否超出范围
    useEffect(() => {
        if (isFocused && currentSchedule) {
            // 如果还没开学 (realCurrentWeek < 1)，强制跳转到第 1 周
            if (realCurrentWeek < 1) {
                if (currentWeek !== 1) setCurrentWeek(1);
            } 
            // 如果已经结课 (realCurrentWeek > maxWeeks)，强制跳转到最后一周
            else if (realCurrentWeek > maxWeeks) {
                if (currentWeek !== maxWeeks) setCurrentWeek(maxWeeks);
            } 
            // 如果在学期内，自动跳转到当前周
            else {
                if (currentWeek !== realCurrentWeek) setCurrentWeek(realCurrentWeek);
            }
        }
    }, [isFocused, currentSchedule, realCurrentWeek, maxWeeks]);

    // 2. 计算左上角显示的文字
    const subtitleText = useMemo(() => {
        const isVacation = realCurrentWeek < 1 || realCurrentWeek > maxWeeks;
        if (isVacation) {
            // 假期时，currentWeek 已经被上面的 effect 强制设为 1 或 maxWeeks
            // 所以这里直接显示 "假期中(非本周)" 即可 (因为 realCurrentWeek 肯定不等于 currentWeek)
            return `假期中(非本周)`;
        }
        return null; // 学期内，交给 Header 默认显示
    }, [realCurrentWeek, maxWeeks]);

    const getCardColor = () => (customSettings.backgroundImage && currentSchedule) ? 'transparent' : theme.card;

    const shouldForceWhite = customSettings.backgroundImage && customSettings.forceWhiteContent && currentSchedule;
    const dynamicTheme = useMemo(() => {
        if (shouldForceWhite) {
            return { ...theme, text: '#FFFFFF', subText: 'rgba(255, 255, 255, 0.85)' };
        }
        return theme;
    }, [theme, shouldForceWhite]);

    useLayoutEffect(() => {
        if (currentSchedule) {
            const isHeaderTransparent = customSettings.backgroundImage && customSettings.transparentHeader;
            navigation.setOptions({
                headerShown: true,
                title: currentSchedule.name,
                headerTitleAlign: 'center',
                headerStyle: {
                    backgroundColor: isHeaderTransparent ? 'transparent' : getCardColor(),
                    shadowColor: 'transparent',
                    elevation: 0,
                },
                headerTintColor: shouldForceWhite ? '#FFFFFF' : theme.text,
                headerShadowVisible: false,
                headerTransparent: isHeaderTransparent,
            });
        } else {
            navigation.setOptions({ headerShown: false });
        }
    }, [navigation, theme, currentSchedule, customSettings, shouldForceWhite]);

    const handleHeaderArrow = (direction: number) => {
        const target = currentWeek + direction;
        if (target < 1 || target > maxWeeks) return;
        setCurrentWeek(target);
    };

    const handleJumpToWeek = (week: number) => {
        setCurrentWeek(week);
        setWeekModalVisible(false);
    };

    const handleCellPress = useCallback((dayIndex: number, periodId: number) => {
        setSelectedSlot({ dayIndex, periodId });
        setModalVisible(true);
    }, []);

    const RootContainer = ({ children }: any) => {
        const topPadding = (customSettings.backgroundImage && customSettings.transparentHeader && currentSchedule) 
            ? (Platform.OS === 'android' ? StatusBar.currentHeight || 24 : 44) + 50 
            : 0;

        let statusBarStyle: 'light-content' | 'dark-content' = theme.dark ? 'light-content' : 'dark-content';
        if (shouldForceWhite) {
            statusBarStyle = 'light-content';
        } else if (customSettings.backgroundImage && customSettings.transparentHeader && currentSchedule) {
            statusBarStyle = 'light-content';
        }

        if (customSettings.backgroundImage && currentSchedule) {
            return (
                <ImageBackground
                    source={{ uri: customSettings.backgroundImage }}
                    style={{ flex: 1 }}
                    imageStyle={{ opacity: customSettings.bgImageOpacity ?? 1 }}
                    resizeMode="cover"
                >
                    {isFocused && <StatusBar barStyle={statusBarStyle} backgroundColor="transparent" translucent={true} />}
                    <View style={{ flex: 1, paddingTop: topPadding }}>{children}</View>
                </ImageBackground>
            );
        }
        return (
            <View style={{ flex: 1, backgroundColor: theme.background }}>
                {isFocused && <StatusBar barStyle={statusBarStyle} backgroundColor={theme.background} />}
                {children}
            </View>
        );
    };

    if (!currentSchedule) {
        const handleGoToCreate = () => { navigation.navigate('ScheduleCreate'); };

        return (
            <RootContainer>
                <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} style={{ flex: 1 }}>
                    <ScrollView contentContainerStyle={styles.createContainer}>
                        <Surface style={[styles.createCard, { backgroundColor: theme.card }]} elevation={2}>
                            <Ionicons name="school-outline" size={48} color={theme.primary} style={{ marginBottom: 10 }} />
                            <Text style={[styles.createTitle, { color: theme.text }]}>欢迎使用</Text>
                            <Text style={{ color: theme.subText, marginBottom: 30, textAlign: 'center' }}>
                                您还没有创建任何课表，{"\n"}请点击下方按钮开始。
                            </Text>
                            
                            <Button 
                                mode="contained" 
                                onPress={handleGoToCreate} 
                                buttonColor={theme.primary} 
                                style={{ width: '100%', paddingVertical: 5, elevation: 0 }}
                                labelStyle={{ fontSize: 16 }}
                            >
                                创建或导入课表
                            </Button>
                        </Surface>
                    </ScrollView>
                </KeyboardAvoidingView>
            </RootContainer>
        );
    }

    const handleEditCourse = (course?: Course) => { setModalVisible(false); navigation.navigate('CourseEdit', { course, initDay: selectedSlot?.dayIndex, initPeriod: selectedSlot?.periodId, scheduleId: currentSchedule.id }); };

    const renderModalContent = () => {
        if (!selectedSlot) return null;
        const { dayIndex, periodId } = selectedSlot;
        const { displayDays } = { displayDays: ['周一','周二','周三','周四','周五','周六','周日'] }; 
        const allCoursesHere = courseList.filter(c => c.scheduleId === currentSchedule.id && c.day === dayIndex && periodId >= c.startPeriod && periodId <= c.endPeriod);
        return (
            <Surface style={[styles.modalContent, { backgroundColor: theme.card }]}>
                <View style={styles.modalHeader}><Text style={{ color: theme.subText }}>{displayDays[dayIndex]} - 第{periodId}节</Text><TouchableOpacity onPress={() => setModalVisible(false)}><Ionicons name="close" size={24} color={theme.subText} /></TouchableOpacity></View>
                {allCoursesHere.length > 0 ? (allCoursesHere.map(c => (<TouchableOpacity key={c.id} style={[styles.courseItem, { borderLeftColor: c.color, backgroundColor: theme.background }, !c.weeks.includes(currentWeek) && { opacity: 0.6, borderLeftColor: '#ccc' }]} onPress={() => handleEditCourse(c)}><View><Text style={[styles.itemTitle, { color: theme.text }]}>{c.name}</Text><Text style={{ color: theme.subText, fontSize: 12 }}>{c.weeks.includes(currentWeek) ? `[本周] ${c.classroom}` : '[非本周]'} | {c.teacher}</Text></View><Ionicons name="chevron-forward" size={20} color={theme.subText} /></TouchableOpacity>))) : <Text style={{ textAlign: 'center', color: theme.subText, marginVertical: 20 }}>此处没有课程</Text>}
                <TouchableOpacity style={[styles.addButton, { backgroundColor: theme.primary + '20' }]} onPress={() => handleEditCourse(undefined)}><Ionicons name="add-circle" size={24} color={theme.primary} /><Text style={{ color: theme.primary, fontWeight: 'bold', marginLeft: 8 }}>添加课程</Text></TouchableOpacity>
            </Surface>
        );
    };

    return (
        <RootContainer>
            <View style={{ flex: 1 }}>
                <ScheduleHeader 
                    displayWeek={currentWeek} 
                    realCurrentWeek={realCurrentWeek}
                    maxWeeks={maxWeeks} 
                    chineseDate={getChineseDate(new Date())} 
                    customSubtitle={subtitleText}
                    theme={dynamicTheme}
                    getCardColor={getCardColor}
                    onPrevWeek={() => handleHeaderArrow(-1)}
                    onNextWeek={() => handleHeaderArrow(1)}
                    onTitlePress={() => setWeekModalVisible(true)}
                />
                <ScheduleList 
                    weekIndex={currentWeek} 
                    onCellPress={handleCellPress} 
                    theme={dynamicTheme}
                />
                <Portal><Modal visible={modalVisible} onDismiss={() => setModalVisible(false)} contentContainerStyle={{ padding: 20, alignItems: 'center', justifyContent: 'center' }}>{renderModalContent()}</Modal></Portal>
                <Portal><Modal visible={weekModalVisible} onDismiss={() => setWeekModalVisible(false)} contentContainerStyle={{ justifyContent: 'flex-end', flex: 1 }}><TouchableOpacity style={{ flex: 1 }} onPress={() => setWeekModalVisible(false)} /><Surface style={{ borderTopLeftRadius: 16, borderTopRightRadius: 16, backgroundColor: theme.card, maxHeight: '50%' }}><View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'center' }}><Text style={{ fontSize: 16, fontWeight: 'bold', color: theme.text }}>跳转到指定周</Text></View><ScrollView>{weekData.map((w) => (<TouchableRipple key={w} onPress={() => { handleJumpToWeek(w); }}><View style={{ padding: 15, alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' }}><Text style={{ fontSize: 16, color: w === currentWeek ? theme.primary : theme.text, fontWeight: w === currentWeek ? 'bold' : 'normal' }}>第 {w} 周</Text>{w === realCurrentWeek && <Text style={{ fontSize: 12, color: theme.subText }}>(本周)</Text>}</View></TouchableRipple>))}</ScrollView></Surface></Modal></Portal>
            </View>
        </RootContainer>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    createContainer: { flexGrow: 1, justifyContent: 'center', padding: 20 },
    createCard: { padding: 30, borderRadius: 16, alignItems: 'center', width: '100%', maxWidth: 350, alignSelf: 'center' },
    createTitle: { fontSize: 24, fontWeight: 'bold', marginBottom: 5 },
    createInput: { marginBottom: 20, width: '100%', backgroundColor: 'transparent' },
    modalContent: { width: '85%', borderRadius: 12, padding: 15, elevation: 5 },
    modalHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 15 },
    courseItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 12, borderRadius: 8, marginBottom: 10, borderLeftWidth: 4 },
    itemTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 4 },
    addButton: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', padding: 12, borderRadius: 8, marginTop: 5, borderStyle: 'dashed', borderWidth: 1, borderColor: '#ccc' },
});