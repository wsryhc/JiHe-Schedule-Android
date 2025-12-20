// src/widget/TodoWidget.tsx
import React from 'react';
import { FlexWidget, TextWidget } from 'react-native-android-widget';

interface WidgetItem {
    type: 'course' | 'todo';
    time: string;
    endTime: string;
    title: string;
    subtitle: string;
    color: string;
    tag?: string;
}

interface WidgetProps {
    items: WidgetItem[];
    totalCount: number;
    theme: {
        background: string; text: string; card: string; subText: string; border: string; primary: string;
    };
    widgetHeight?: number;
    termStartDate?: string;
    totalWeeks?: number;
    // 🔥 新增：是否为安全模式 (四周留白)
    isSafeMode?: boolean;
}

export function TodoWidget({ items, totalCount, theme, widgetHeight, termStartDate, totalWeeks, isSafeMode = false }: WidgetProps) {
    const now = new Date();
    const dateStr = `${now.getMonth() + 1}月${now.getDate()}日`;
    const weekDayStr = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()];

    // 布局参数
    // 🔥 核心修改：如果是安全模式，四周强制留出 14dp 的空白，防止被某些主题裁切
    const ROOT_MARGIN = isSafeMode ? 14 : 0;
    const PADDING_VERTICAL = 10;
    const HEADER_HEIGHT = 22;
    const HEADER_MARGIN = 6;
    const ITEM_HEIGHT = 48;
    const ITEM_MARGIN = 4;
    const SYSTEM_PADDING_BUFFER = 0;

    // 动态计算逻辑
    const safeHeight = widgetHeight || 200;
    // ROOT_MARGIN 变大后，内容区域会自动变小，计算出的 maxVisibleItems 也会相应减少
    const availableContentHeight = safeHeight - (ROOT_MARGIN * 2) - PADDING_VERTICAL - HEADER_HEIGHT - HEADER_MARGIN - SYSTEM_PADDING_BUFFER;

    let maxVisibleItems = Math.floor(availableContentHeight / (ITEM_HEIGHT + ITEM_MARGIN));
    if (maxVisibleItems < 1) maxVisibleItems = 1;

    const displayList = items.slice(0, maxVisibleItems);
    const overflowCount = totalCount - displayList.length;

    const bgColor = theme?.background || '#ffffff';
    const textColor = theme?.text || '#000000';
    const subTextColor = theme?.subText || '#666666';
    const cardColor = theme?.card || '#f5f5f5';
    const primaryColor = theme?.primary || '#2196F3';

    let weekDisplayText = "";
    if (termStartDate && totalWeeks) {
        try {
            const [y, m, d] = termStartDate.split('-').map(Number);
            const start = new Date(y, m - 1, d);
            const current = new Date(now.getFullYear(), now.getMonth(), now.getDate());

            const diffTime = current.getTime() - start.getTime();
            const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

            const currentWeek = Math.floor(diffDays / 7) + 1;

            if (diffDays < 0) {
                weekDisplayText = "假期中";
            } else if (currentWeek > totalWeeks) {
                weekDisplayText = "假期中";
            } else {
                weekDisplayText = `第${currentWeek}周`;
            }
        } catch (e) { }
    }

    return (
        <FlexWidget
            clickAction="OPEN_APP"
            style={{
                height: 'match_parent',
                width: 'match_parent',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
            }}
        >
            <FlexWidget
                style={{
                    height: 'match_parent',
                    width: 'match_parent',
                    backgroundColor: bgColor as any,
                    borderRadius: 16,
                    // 🔥 这里应用了边距，普通版为0，异常使用版为14
                    margin: ROOT_MARGIN,
                    paddingVertical: PADDING_VERTICAL / 2,
                    paddingHorizontal: 12,
                    flexDirection: 'column',
                }}
            >
                {/* Header */}
                <FlexWidget style={{
                    width: 'match_parent',
                    flexDirection: 'row',
                    alignItems: 'center',
                    marginBottom: HEADER_MARGIN,
                    height: HEADER_HEIGHT
                }}>

                    {/* 左侧标题 */}
                    <FlexWidget style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <FlexWidget style={{ width: 4, height: 14, backgroundColor: primaryColor as any, borderRadius: 2, marginRight: 8 }} />
                        <TextWidget
                            text={`今日安排 / ${weekDayStr}`}
                            style={{ fontSize: 13, color: textColor as any, fontWeight: 'bold' }}
                        />
                    </FlexWidget>

                    {/* 中间占位符 */}
                    <FlexWidget style={{ flex: 1 }} />

                    {/* 右侧：周数 + 日期 */}
                    <FlexWidget style={{ flexDirection: 'row', alignItems: 'center' }}>
                        {weekDisplayText ? (
                            <TextWidget
                                text={weekDisplayText}
                                style={{ fontSize: 12, color: primaryColor as any, fontWeight: 'bold', marginRight: 8 }}
                            />
                        ) : null}
                        <TextWidget text={dateStr} style={{ fontSize: 11, color: subTextColor as any }} />
                    </FlexWidget>
                </FlexWidget>

                {/* List Area */}
                <FlexWidget style={{ flex: 1, width: 'match_parent', flexDirection: 'column', justifyContent: 'flex-start' }}>
                    {displayList.length === 0 ? (
                        <FlexWidget style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
                            <TextWidget text="☕️" style={{ fontSize: 24, marginBottom: 4 }} />
                            <TextWidget text="今天暂无安排" style={{ fontSize: 12, color: subTextColor as any }} />
                        </FlexWidget>
                    ) : (
                        displayList.map((item, index) => {
                            const isLastItem = index === displayList.length - 1;
                            const showOverflow = isLastItem && overflowCount > 0;

                            return (
                                <FlexWidget
                                    key={index}
                                    style={{
                                        flexDirection: 'row',
                                        width: 'match_parent',
                                        height: ITEM_HEIGHT,
                                        marginBottom: isLastItem ? 0 : ITEM_MARGIN,
                                        alignItems: 'center'
                                    }}
                                >
                                    {/* Time Column */}
                                    <FlexWidget style={{ flexDirection: 'column', justifyContent: 'center', alignItems: 'flex-end', width: 42, marginRight: 8 }}>
                                        <TextWidget text={item.time} style={{ fontSize: 13, color: textColor as any, fontWeight: 'bold' }} />
                                        <TextWidget text={item.endTime} style={{ fontSize: 10, color: subTextColor as any }} />
                                    </FlexWidget>

                                    {/* Color Bar */}
                                    <FlexWidget style={{ width: 4, height: 32, borderRadius: 2, backgroundColor: item.color as any, marginRight: 8 }} />

                                    {/* Content Card */}
                                    <FlexWidget style={{
                                        flex: 1, height: 'match_parent', backgroundColor: cardColor as any, borderRadius: 8, paddingHorizontal: 10, justifyContent: 'center', flexDirection: 'row', alignItems: 'center'
                                    }}>
                                        <FlexWidget style={{ flex: 1, flexDirection: 'column', justifyContent: 'center' }}>
                                            <FlexWidget style={{ flexDirection: 'row', alignItems: 'center' }}>
                                                {item.tag && item.tag !== '默认' && (
                                                    <FlexWidget style={{
                                                        backgroundColor: item.color as any,
                                                        borderRadius: 4,
                                                        paddingHorizontal: 4,
                                                        paddingVertical: 2,
                                                        marginRight: 6,
                                                        justifyContent: 'center', alignItems: 'center'
                                                    }}>
                                                        <TextWidget
                                                            text={item.tag}
                                                            style={{ fontSize: 10, color: '#ffffff', fontWeight: 'bold' }}
                                                        />
                                                    </FlexWidget>
                                                )}
                                                <TextWidget
                                                    text={item.title}
                                                    style={{ fontSize: 14, color: textColor as any, fontWeight: 'bold' }}
                                                    maxLines={1}
                                                />
                                            </FlexWidget>

                                            <TextWidget text={item.subtitle} style={{ fontSize: 11, color: subTextColor as any }} maxLines={1} />
                                        </FlexWidget>

                                        {/* Overflow Badge */}
                                        {showOverflow && (
                                            <FlexWidget style={{
                                                marginLeft: 4,
                                                backgroundColor: bgColor as any,
                                                borderRadius: 6,
                                                paddingHorizontal: 6,
                                                paddingVertical: 2,
                                                alignItems: 'center',
                                                justifyContent: 'center'
                                            }}>
                                                <TextWidget
                                                    text={`+${overflowCount}`}
                                                    style={{ fontSize: 11, color: primaryColor as any, fontWeight: 'bold' }}
                                                />
                                            </FlexWidget>
                                        )}
                                    </FlexWidget>
                                </FlexWidget>
                            )
                        })
                    )}
                </FlexWidget>
            </FlexWidget>
        </FlexWidget>
    );
}