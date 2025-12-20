// widget-task-handler.tsx
import React from 'react';
import { TodoWidget } from './src/widget/TodoWidget';
import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEY = 'MY_APP_DATA_V1';

const defaultTheme = {
    background: '#ffffff',
    text: '#000000',
    card: '#f0f0f0',
    subText: '#666666',
    border: '#e0e0e0',
    primary: '#2196F3'
};

export async function widgetTaskHandlerModule(props: any) {
    const { widgetAction, widgetName, renderWidget, width, height } = props;

    if (widgetName === 'TodoWidget' || widgetName === 'TodoWidgetLarge') {
        switch (widgetAction) {
            case 'WIDGET_ADDED':
            case 'WIDGET_UPDATE':
            case 'WIDGET_RESIZED':
                
                let items: any[] = [];
                let totalCount = 0;
                // 🔥 新增变量：用于存储课表信息
                let termStartDate: string | undefined;
                let totalWeeks: number | undefined;

                try {
                    const jsonValue = await AsyncStorage.getItem(STORAGE_KEY);
                    if (jsonValue != null) {
                        const data = JSON.parse(jsonValue);
                        const { courseList, todoList, timeLayout, currentSchedule, displayConfig } = data;

                        // 🔥 获取课表设置信息 (用于计算周数)
                        if (currentSchedule) {
                            termStartDate = currentSchedule.termStartDate;
                            totalWeeks = currentSchedule.totalWeeks;
                        }

                        const today = new Date();
                        const todayStr = today.toISOString().split('T')[0];
                        const dayOfWeek = today.getDay() === 0 ? 6 : today.getDay() - 1;

                        let currentWeekNum = 1;
                        if (currentSchedule) {
                            const start = new Date(currentSchedule.termStartDate);
                            const diffTime = today.getTime() - start.getTime();
                            const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
                            if (diffDays >= 0) currentWeekNum = Math.floor(diffDays / 7) + 1;
                        }

                        let todayCourses: any[] = [];
                        if (displayConfig?.widget?.showCourse && currentSchedule) {
                            todayCourses = (courseList || []).filter((c: any) =>
                                c.scheduleId === currentSchedule.id &&
                                c.day === dayOfWeek &&
                                c.weeks.includes(currentWeekNum)
                            ).map((c: any) => {
                                const startSlot = (timeLayout || []).find((t: any) => t.id === c.startPeriod);
                                const endSlot = (timeLayout || []).find((t: any) => t.id === c.endPeriod);
                                return {
                                    type: 'course',
                                    time: startSlot ? startSlot.startTime : '00:00',
                                    endTime: endSlot ? endSlot.endTime : '00:00',
                                    title: c.name,
                                    subtitle: c.classroom || '未知教室',
                                    color: c.color,
                                    sortTime: startSlot ? startSlot.startTime : '00:00'
                                };
                            });
                        }

                        let todayTodos: any[] = [];
                        if (displayConfig?.widget?.showTodo) {
                            todayTodos = (todoList || []).filter((t: any) => t.date === todayStr && !t.completed)
                                .map((t: any) => ({
                                    type: 'todo',
                                    time: t.startTime,
                                    endTime: t.endTime,
                                    title: t.title,
                                    subtitle: t.description || '待办事项',
                                    color: t.color || defaultTheme.primary,
                                    tag: t.tag,
                                    sortTime: t.startTime
                                }));
                        }

                        items = [...todayCourses, ...todayTodos].sort((a, b) => a.sortTime.localeCompare(b.sortTime));
                        totalCount = items.length;
                    }
                } catch (e) {
                    console.error('Widget update failed:', e);
                }

                const defaultHeight = widgetName === 'TodoWidgetLarge' ? 300 : 200;
                
                await renderWidget(
                    <TodoWidget 
                        items={items} 
                        totalCount={totalCount} 
                        theme={defaultTheme} 
                        widgetHeight={height || defaultHeight}
                        // 🔥 传入这两个新参数
                        termStartDate={termStartDate}
                        totalWeeks={totalWeeks}
                    />
                );
                break;

            default:
                break;
        }
    }
}