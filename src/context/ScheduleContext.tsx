// src/context/ScheduleContext.tsx
import React, { createContext, useState, useContext, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useTheme } from './ThemeContext';
import { requestWidgetUpdate } from 'react-native-android-widget';
import { TodoWidget } from '../widget/TodoWidget';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

// --- 通知行为配置 ---
Notifications.setNotificationHandler({
    handleNotification: async () => ({
        shouldShowAlert: true,
        shouldPlaySound: true,
        shouldSetBadge: false,
        shouldShowBanner: true,
        shouldShowList: true,
    }),
});

// --- 类型定义 ---
export type TimeSlot = { id: number; startTime: string; endTime: string; };

export interface Course {
    id: string; scheduleId: string; name: string; classroom: string; teacher: string;
    day: number; startPeriod: number; endPeriod: number; weeks: number[]; color: string;
}
export interface ScheduleInfo {
    id: string;
    name: string;
    termStartDate: string;
    totalWeeks: number;
}

export interface Todo {
    id: string; date: string; startTime: string; endTime: string; title: string;
    description?: string; completed: boolean; tag?: string; tagType?: string;
    color?: string; isYearly?: boolean;
    repeatType?: 'none' | 'weekly' | 'monthly' | 'yearly';
    reminder?: {
        value: number;
        unit: 'minute' | 'hour' | 'day' | 'week' | 'month' | 'year';
    } | null;
    notificationId?: string; // 存储通知ID
}

export interface NotificationConfig {
    enabled: boolean;
    courseRemind: boolean;
    todoRemind: boolean;
    advanceTime: number;
}

const DEFAULT_NOTIFICATION_CONFIG: NotificationConfig = {
    enabled: true,
    courseRemind: true,
    todoRemind: true,
    advanceTime: 10,
};

type PeriodConfig = { morning: number; afternoon: number; evening: number; };

export interface WeekendConfig {
    saturday: boolean;
    sunday: boolean;
}

export interface DisplayConfig {
    inApp: { showTodo: boolean; showCourse: boolean };
    widget: { showTodo: boolean; showCourse: boolean };
}

const DEFAULT_DISPLAY_CONFIG: DisplayConfig = {
    inApp: { showTodo: true, showCourse: true },
    widget: { showTodo: true, showCourse: true }
};

const STORAGE_KEY = 'MY_APP_DATA_V1';

export const checkTodoOnDate = (todo: Todo, targetDateStr: string) => {
    if (targetDateStr < todo.date) return false;
    const target = new Date(targetDateStr);
    const start = new Date(todo.date);

    const type = todo.repeatType || (todo.isYearly ? 'yearly' : 'none');

    if (type === 'none') {
        return targetDateStr === todo.date;
    } else if (type === 'weekly') {
        const oneDay = 24 * 60 * 60 * 1000;
        const diffTime = Math.abs(target.getTime() - start.getTime());
        const diffDays = Math.round(diffTime / oneDay);
        return diffDays % 7 === 0;
    } else if (type === 'monthly') {
        const startDay = start.getDate();
        const targetDay = target.getDate();
        if (startDay === targetDay) return true;
        const targetMonthDays = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
        if (startDay > targetMonthDays && targetDay === targetMonthDays) {
            return true;
        }
        return false;
    } else if (type === 'yearly') {
        const startMonth = start.getMonth();
        const startDay = start.getDate();
        const targetMonth = target.getMonth();
        const targetDay = target.getDate();
        if (startMonth === targetMonth && startDay === targetDay) return true;
        const isLeap = (y: number) => (y % 4 === 0 && y % 100 !== 0) || (y % 400 === 0);
        if (startMonth === 1 && startDay === 29) {
            if (!isLeap(target.getFullYear()) && targetMonth === 1 && targetDay === 28) {
                return true;
            }
        }
        return false;
    }
    return false;
};

interface ScheduleContextType {
    weekendConfig: WeekendConfig;
    updateWeekendConfig: (config: Partial<WeekendConfig>) => void;

    periodConfig: PeriodConfig; setPeriodConfig: (v: PeriodConfig) => void;
    updatePeriodConfig: (v: PeriodConfig) => void;
    timeLayout: TimeSlot[]; setTimeLayout: (v: TimeSlot[]) => void;
    updateTimeLayout: (v: TimeSlot[]) => void;
    periodList: number[];

    courseList: Course[]; addCourse: (c: Course) => void; updateCourse: (c: Course) => void; deleteCourse: (id: string) => void;
    scheduleList: ScheduleInfo[]; currentSchedule: ScheduleInfo | null;

    createSchedule: (name: string, startDate: string, totalWeeks?: number) => void;
    switchSchedule: (id: string) => void;
    updateScheduleInfo: (id: string, name: string, startDate: string, totalWeeks: number) => void;

    currentWeek: number; setCurrentWeek: (week: number) => void;

    todoList: Todo[];
    addTodo: (t: Todo) => Promise<void>;
    updateTodo: (t: Todo) => Promise<void>;
    deleteTodo: (id: string) => Promise<void>;

    displayConfig: DisplayConfig; setDisplayConfig: (config: DisplayConfig) => void;
    updateDisplayConfig: (config: DisplayConfig) => void;

    deleteSchedule: (id: string) => void;
    clearAllSchedules: () => void;
    exportScheduleData: (id: string) => string;
    importScheduleData: (jsonStr: string) => boolean;
    exportTodoData: () => string;
    batchAddTodos: (todos: Todo[]) => void;

    resetToDefault: () => void;
    setTodoList: (todos: Todo[]) => void;

    notificationConfig: NotificationConfig;
    updateNotificationConfig: (config: Partial<NotificationConfig>) => void;
}

const ScheduleContext = createContext<ScheduleContextType>({} as ScheduleContextType);

const generateTimeLayout = (config: PeriodConfig) => {
    const totalPeriods = config.morning + config.afternoon + config.evening;
    const newLayout: TimeSlot[] = [];
    const getDefaultTime = (startH: number, offset: number) => {
        const totalM = startH * 60 + offset * 55;
        const h = Math.floor(totalM / 60) % 24;
        const m = totalM % 60;
        const endM = m + 45;
        const endH = h + Math.floor(endM / 60);
        return {
            start: `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`,
            end: `${endH.toString().padStart(2, '0')}:${(endM % 60).toString().padStart(2, '0')}`
        };
    };
    for (let i = 1; i <= totalPeriods; i++) {
        let t;
        if (i <= config.morning) t = getDefaultTime(8, i - 1);
        else if (i <= config.morning + config.afternoon) t = getDefaultTime(14, i - config.morning - 1);
        else t = getDefaultTime(19, i - config.morning - config.afternoon - 1);
        newLayout.push({ id: i, startTime: t.start, endTime: t.end });
    }
    return newLayout;
};

// 初始化通知渠道
async function registerForPushNotificationsAsync() {
    if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('default', {
            name: '默认通知',
            importance: Notifications.AndroidImportance.MAX,
            vibrationPattern: [0, 250, 250, 250],
            lightColor: '#FF231F7C',
        });
    }
    const { status: existingStatus } = await Notifications.getPermissionsAsync();
    let finalStatus = existingStatus;
    if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
    }
    if (finalStatus !== 'granted') {
        console.log('用户拒绝了通知权限');
    }
}

// 辅助函数：计算提醒的毫秒数
const getReminderOffsetMs = (val: number, unit: string) => {
    const minute = 60 * 1000;
    const hour = 60 * minute;
    const day = 24 * hour;
    switch (unit) {
        case 'minute': return val * minute;
        case 'hour': return val * hour;
        case 'day': return val * day;
        case 'week': return val * 7 * day;
        case 'month': return val * 30 * day;
        case 'year': return val * 365 * day;
        default: return 0;
    }
};

export const ScheduleProvider = ({ children }: any) => {
    const { theme, updateCustomSettings } = useTheme();
    const [weekendConfig, setWeekendConfig] = useState<WeekendConfig>({ saturday: true, sunday: true });

    const [periodConfig, setPeriodConfig] = useState<PeriodConfig>({ morning: 4, afternoon: 4, evening: 4 });
    const [timeLayout, setTimeLayout] = useState<TimeSlot[]>([]);
    const [courseList, setCourseList] = useState<Course[]>([]);
    const [scheduleList, setScheduleList] = useState<ScheduleInfo[]>([]);
    const [currentSchedule, setCurrentSchedule] = useState<ScheduleInfo | null>(null);
    const [currentWeek, setCurrentWeek] = useState(1);
    const [todoList, setTodoList] = useState<Todo[]>([]);
    const [isLoaded, setIsLoaded] = useState(false);
    const [displayConfig, setDisplayConfig] = useState<DisplayConfig>(DEFAULT_DISPLAY_CONFIG);
    const [notificationConfig, setNotificationConfig] = useState<NotificationConfig>(DEFAULT_NOTIFICATION_CONFIG);

    useEffect(() => {
        registerForPushNotificationsAsync();
        const loadData = async () => {
            try {
                const jsonValue = await AsyncStorage.getItem(STORAGE_KEY);
                if (jsonValue != null) {
                    const data = JSON.parse(jsonValue);
                    if (data.weekendConfig) setWeekendConfig(data.weekendConfig);
                    else if (data.showWeekend !== undefined) setWeekendConfig({ saturday: data.showWeekend, sunday: data.showWeekend });
                    if (data.periodConfig) setPeriodConfig(data.periodConfig);
                    if (data.timeLayout && data.timeLayout.length > 0) setTimeLayout(data.timeLayout);
                    else setTimeLayout(generateTimeLayout(data.periodConfig || { morning: 4, afternoon: 4, evening: 4 }));
                    if (data.courseList) setCourseList(data.courseList);
                    const loadedSchedules = (data.scheduleList || []).map((s: any) => ({
                        ...s,
                        totalWeeks: s.totalWeeks || 25
                    }));
                    setScheduleList(loadedSchedules);
                    if (data.currentSchedule) {
                        setCurrentSchedule({
                            ...data.currentSchedule,
                            totalWeeks: data.currentSchedule.totalWeeks || 25
                        });
                        const now = new Date();
                        const start = new Date(data.currentSchedule.termStartDate);
                        const diffTime = now.getTime() - start.getTime();
                        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
                        let autoWeek = Math.floor(diffDays / 7) + 1;
                        setCurrentWeek(autoWeek > 0 ? autoWeek : 1);
                    } else {
                        if (data.currentWeek) setCurrentWeek(data.currentWeek);
                    }
                    if (data.todoList) setTodoList(data.todoList);
                    if (data.displayConfig) setDisplayConfig(data.displayConfig);
                    if (data.notificationConfig) setNotificationConfig(data.notificationConfig);
                } else {
                    setTimeLayout(generateTimeLayout({ morning: 4, afternoon: 4, evening: 4 }));
                }
            } catch (e) {
                console.error("加载失败", e);
            } finally {
                setIsLoaded(true);
            }
        };
        loadData();
    }, []);

    useEffect(() => {
        if (!isLoaded) return;
        const newTotal = periodConfig.morning + periodConfig.afternoon + periodConfig.evening;
        setTimeLayout(prev => {
            const currentTotal = prev.length;
            if (currentTotal === newTotal) return prev;
            if (newTotal < currentTotal) {
                return prev.slice(0, newTotal);
            } else {
                const refLayout = generateTimeLayout(periodConfig);
                const addedSlots = refLayout.slice(currentTotal, newTotal);
                return [...prev, ...addedSlots];
            }
        });
    }, [periodConfig, isLoaded]);

    const updateAndroidWidget = async () => {
        try {
            const today = new Date();
            const year = today.getFullYear();
            const month = String(today.getMonth() + 1).padStart(2, '0');
            const day = String(today.getDate()).padStart(2, '0');
            const todayStr = `${year}-${month}-${day}`;
            const dayOfWeek = today.getDay() === 0 ? 6 : today.getDay() - 1;
            const nowMinutes = today.getHours() * 60 + today.getMinutes();

            let currentWeekNum = 1;
            if (currentSchedule) {
                const start = new Date(currentSchedule.termStartDate);
                const diffTime = today.getTime() - start.getTime();
                const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
                if (diffDays >= 0) currentWeekNum = Math.floor(diffDays / 7) + 1;
            }

            let todayCourses: any[] = [];
            if (displayConfig.widget.showCourse && currentSchedule) {
                todayCourses = courseList.filter(c =>
                    c.scheduleId === currentSchedule.id &&
                    c.day === dayOfWeek &&
                    c.weeks.includes(currentWeekNum)
                ).map(c => {
                    const startSlot = timeLayout.find(t => t.id === c.startPeriod);
                    const endSlot = timeLayout.find(t => t.id === c.endPeriod);
                    let isCompleted = false;
                    if (endSlot) {
                        const [endH, endM] = endSlot.endTime.split(':').map(Number);
                        const endMinutes = endH * 60 + endM;
                        if (nowMinutes > endMinutes) isCompleted = true;
                    }
                    return {
                        type: 'course',
                        time: startSlot ? startSlot.startTime : '00:00',
                        endTime: endSlot ? endSlot.endTime : '00:00',
                        title: c.name,
                        subtitle: c.classroom || '未知教室',
                        color: c.color,
                        sortTime: startSlot ? startSlot.startTime : '00:00',
                        completed: isCompleted
                    };
                }).filter(item => !item.completed);
            }

            let todayTodos: any[] = [];
            if (displayConfig.widget.showTodo) {
                todayTodos = todoList
                    .filter(t => checkTodoOnDate(t, todayStr) && !t.completed)
                    .map(t => ({
                        type: 'todo',
                        time: t.startTime,
                        endTime: t.endTime,
                        title: t.title,
                        subtitle: t.description || '待办事项',
                        color: t.color || theme.primary,
                        tag: t.tag,
                        sortTime: t.startTime,
                        completed: false
                    }));
            }

            const fullList = [...todayCourses, ...todayTodos].sort((a, b) => a.sortTime.localeCompare(b.sortTime));
            const totalCount = fullList.length;
            const displayList = fullList.slice(0, 5);

            const widgetTheme = {
                background: theme.background, text: theme.text, card: theme.card,
                subText: theme.subText, border: theme.border, primary: theme.primary
            };

            const startDate = currentSchedule?.termStartDate;
            const weeks = currentSchedule?.totalWeeks;

            ['TodoWidget', 'TodoWidget3x2', 'TodoWidgetLarge'].forEach(name => {
                requestWidgetUpdate({
                    widgetName: name as any,
                    renderWidget: () => (
                        <TodoWidget
                            items={displayList}
                            totalCount={totalCount}
                            theme={widgetTheme}
                            widgetHeight={name === 'TodoWidgetLarge' ? 300 : 200}
                            termStartDate={startDate}
                            totalWeeks={weeks}
                        />
                    ),
                    widgetNotFound: () => { }
                });
            });
        } catch (e) {
            console.log('Widget update failed', e);
        }
    };

    useEffect(() => {
        if (!isLoaded) return;
        const saveData = async () => {
            const dataToSave = {
                weekendConfig, periodConfig, timeLayout,
                courseList, scheduleList, currentSchedule, currentWeek, todoList,
                displayConfig, notificationConfig
            };
            try {
                await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(dataToSave));
                updateAndroidWidget();
            } catch (e) { console.error(e); }
        };
        saveData();
    }, [
        weekendConfig, periodConfig, timeLayout, courseList,
        scheduleList, currentSchedule, currentWeek, todoList,
        isLoaded, theme, displayConfig, notificationConfig
    ]);

    const updateWeekendConfig = (config: Partial<WeekendConfig>) => setWeekendConfig(prev => ({ ...prev, ...config }));
    const createSchedule = (name: string, startDate: string, totalWeeks = 25) => {
        const newSchedule: ScheduleInfo = { id: Date.now().toString(), name, termStartDate: startDate, totalWeeks };
        setScheduleList(prev => [...prev, newSchedule]);
        setCurrentSchedule(newSchedule);
        setCurrentWeek(1);
    };
    const switchSchedule = (id: string) => { const target = scheduleList.find(s => s.id === id); if (target) setCurrentSchedule(target); };
    const updateScheduleInfo = (id: string, name: string, startDate: string, totalWeeks: number) => {
        setScheduleList(prev => prev.map(s => s.id === id ? { ...s, name, termStartDate: startDate, totalWeeks } : s));
        if (currentSchedule?.id === id) setCurrentSchedule(prev => prev ? { ...prev, name, termStartDate: startDate, totalWeeks } : null);
    };
    const addCourse = (c: Course) => setCourseList(prev => [...prev, c]);
    const updateCourse = (c: Course) => setCourseList(prev => prev.map(x => x.id === c.id ? c : x));
    const deleteCourse = (id: string) => setCourseList(prev => prev.filter(x => x.id !== id));

    // 🔥 核心修复：添加 type: 'timeInterval' 解决报错
    const scheduleNotificationForTodo = async (todo: Todo): Promise<string | undefined> => {
        if (!notificationConfig.enabled || !todo.reminder) return undefined;

        const [year, month, day] = todo.date.split('-').map(Number);
        const [hour, minute] = todo.startTime.split(':').map(Number);
        const targetDate = new Date(year, month - 1, day, hour, minute);

        const offsetMs = getReminderOffsetMs(todo.reminder.value, todo.reminder.unit);
        const triggerDate = new Date(targetDate.getTime() - offsetMs);
        const now = Date.now();

        // 1. 如果时间已过，不预约
        if (triggerDate.getTime() < now) return undefined;

        // 2. 计算剩余秒数
        const seconds = Math.floor((triggerDate.getTime() - now) / 1000);
        if (seconds <= 0) return undefined;

        try {
            const id = await Notifications.scheduleNotificationAsync({
                content: {
                    title: `⏰ 待办提醒：${todo.title}`,
                    body: `时间：${todo.startTime} ${todo.description ? `\n${todo.description}` : ''}`,
                    sound: true,
                    // Android 需要指定 channelId (与 init 时一致)
                    ...(Platform.OS === 'android' ? { channelId: 'default' } : {}),
                },
                // 🔥 修复：显式指定 type: 'timeInterval'
                trigger: {
                    type: 'timeInterval', // 也可以用 Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL
                    seconds: seconds,
                    repeats: false,
                } as any,
            });
            return id;
        } catch (e) {
            console.error('Schedule failed', e);
            return undefined;
        }
    };

    const addTodo = async (t: Todo) => {
        const notifId = await scheduleNotificationForTodo(t);
        const newTodo = { ...t, notificationId: notifId };
        setTodoList(prev => [...prev, newTodo]);
    };

    const updateTodo = async (t: Todo) => {
        // 先取消旧通知
        if (t.notificationId) {
            try {
                await Notifications.cancelScheduledNotificationAsync(t.notificationId);
            } catch (e) { /* ignore */ }
        }

        // 预约新通知
        const newNotifId = await scheduleNotificationForTodo(t);
        const updatedTodo = { ...t, notificationId: newNotifId };

        setTodoList(prev => prev.map(x => x.id === t.id ? updatedTodo : x));
    };

    const deleteTodo = async (id: string) => {
        const target = todoList.find(t => t.id === id);
        if (target?.notificationId) {
            try {
                await Notifications.cancelScheduledNotificationAsync(target.notificationId);
            } catch (e) { /* ignore */ }
        }
        setTodoList(prev => prev.filter(x => x.id !== id));
    };

    const clearAllSchedules = () => {
        setScheduleList([]);
        setCourseList([]);
        setCurrentSchedule(null);
        setCurrentWeek(1);
    };

    const deleteSchedule = (id: string) => {
        const newScheduleList = scheduleList.filter(s => s.id !== id);
        setScheduleList(newScheduleList);
        setCourseList(prev => prev.filter(c => c.scheduleId !== id));
        if (currentSchedule?.id === id) setCurrentSchedule(newScheduleList.length > 0 ? newScheduleList[0] : null);
    };
    const exportScheduleData = (id: string) => {
        const targetSchedule = scheduleList.find(s => s.id === id);
        const targetCourses = courseList.filter(c => c.scheduleId === id);
        if (!targetSchedule) return '';
        const exportData = { version: '1.0', type: 'schedule-export', createdAt: new Date().toISOString(), info: targetSchedule, courses: targetCourses };
        return JSON.stringify(exportData, null, 2);
    };

    const exportTodoData = () => {
        const exportData = { version: '1.0', type: 'todo-export', createdAt: new Date().toISOString(), todos: todoList };
        return JSON.stringify(exportData, null, 2);
    };

    const batchAddTodos = (newTodos: Todo[]) => {
        setTodoList(prev => [...prev, ...newTodos]);
    };

    const importScheduleData = (jsonStr: string) => {
        try {
            const data = JSON.parse(jsonStr);
            if (!data.info || !data.courses || !Array.isArray(data.courses)) return false;
            const newScheduleId = Date.now().toString();
            const newSchedule: ScheduleInfo = { id: newScheduleId, name: data.info.name + ' (导入)', termStartDate: data.info.termStartDate, totalWeeks: data.info.totalWeeks || 25 };
            const newCourses = data.courses.map((c: Course) => ({ ...c, id: Date.now().toString() + Math.random().toString(36).substr(2, 5), scheduleId: newScheduleId }));
            setScheduleList(prev => [...prev, newSchedule]);
            setCourseList(prev => [...prev, ...newCourses]);
            return true;
        } catch (e) { return false; }
    };
    const resetToDefault = async () => {
        try {
            await Notifications.cancelAllScheduledNotificationsAsync();
            await AsyncStorage.clear();
            setCourseList([]); setScheduleList([]); setCurrentSchedule(null); setTodoList([]);
            setPeriodConfig({ morning: 4, afternoon: 4, evening: 4 });
            setWeekendConfig({ saturday: true, sunday: true });
            setDisplayConfig(DEFAULT_DISPLAY_CONFIG);
            setTimeLayout(generateTimeLayout({ morning: 4, afternoon: 4, evening: 4 }));
            setCurrentWeek(1);
            setNotificationConfig(DEFAULT_NOTIFICATION_CONFIG);
            if (updateCustomSettings) updateCustomSettings({ backgroundImage: null, bgImageOpacity: 1, borderOpacity: 0.1, courseOpacity: 0.85, transparentHeader: false, forceWhiteContent: false });
        } catch (e) { console.error("重置失败", e); }
    };

    const updateNotificationConfig = (config: Partial<NotificationConfig>) => {
        setNotificationConfig(prev => ({ ...prev, ...config }));
    };

    return (
        <ScheduleContext.Provider value={{
            weekendConfig, updateWeekendConfig, periodConfig, setPeriodConfig, updatePeriodConfig: setPeriodConfig,
            timeLayout, setTimeLayout, updateTimeLayout: setTimeLayout, periodList: timeLayout.map(t => t.id),
            courseList, addCourse, updateCourse, deleteCourse, scheduleList, currentSchedule, createSchedule, switchSchedule, updateScheduleInfo,
            currentWeek, setCurrentWeek, todoList, addTodo, updateTodo, deleteTodo, setTodoList,
            displayConfig, setDisplayConfig, updateDisplayConfig: setDisplayConfig,
            deleteSchedule, clearAllSchedules, exportScheduleData, importScheduleData, resetToDefault,
            exportTodoData, batchAddTodos,
            notificationConfig, updateNotificationConfig
        }}>
            {children}
        </ScheduleContext.Provider>
    );
};

export const useSchedule = () => useContext(ScheduleContext);