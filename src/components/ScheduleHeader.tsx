// src/components/ScheduleHeader.tsx
import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { Surface, IconButton } from 'react-native-paper';
import { useTheme } from '../context/ThemeContext'; 

interface ScheduleHeaderProps {
    displayWeek: number;
    realCurrentWeek: number;
    maxWeeks: number;
    chineseDate: string;
    onPrevWeek: () => void;
    onNextWeek: () => void;
    onTitlePress: () => void;
    theme?: any; 
    getCardColor: () => string;
    // 🔥 新增：用于接收“假期中”等自定义副标题
    customSubtitle?: string | null; 
}

// 使用 React.memo 确保只有 props 变了才刷新
const ScheduleHeader = React.memo(({
    displayWeek,
    realCurrentWeek,
    maxWeeks,
    chineseDate,
    onPrevWeek,
    onNextWeek,
    onTitlePress,
    theme: propTheme, 
    getCardColor,
    customSubtitle // 🔥 解构出新属性
}: ScheduleHeaderProps) => {

    const { theme: contextTheme } = useTheme();
    const theme = propTheme || contextTheme;

    return (
        <Surface style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 8, backgroundColor: getCardColor() }} elevation={0}>
            <View style={{ marginLeft: 10 }}>
                {/* 日期保持不变 */}
                <Text style={{ fontSize: 18, fontWeight: 'bold', color: theme.text }}>{chineseDate}</Text>
                
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                    {/* 圆点逻辑不变：只要展示周 != 真实周，就是红色。假期时这俩肯定不相等，所以是红色，符合预期 */}
                    <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: displayWeek === realCurrentWeek ? '#4CAF50' : '#FF5252', marginRight: 4 }} />
                    
                    {/* 🔥 修改文字显示逻辑：优先显示传入的自定义文本（假期），否则显示原来的逻辑 */}
                    <Text style={{ fontSize: 11, color: theme.subText }}>
                        {customSubtitle || (displayWeek === realCurrentWeek ? '本周' : `非本周 (第${realCurrentWeek}周)`)}
                    </Text>
                </View>
            </View>

            <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: theme.card + '80', borderRadius: 16, height: 32, paddingRight: 4 }}>
                {/* 左箭头：第一周不可点 */}
                <IconButton icon="chevron-left" size={18} onPress={onPrevWeek} disabled={displayWeek <= 1} style={{ margin: 0, width: 24, height: 24 }} />
                
                <TouchableOpacity onPress={onTitlePress} style={{ paddingHorizontal: 6 }}>
                    <Text style={{ fontSize: 15, fontWeight: 'bold', color: theme.primary }}>第 {displayWeek} 周</Text>
                </TouchableOpacity>
                
                {/* 右箭头：最后一周不可点 */}
                <IconButton 
                    icon="chevron-right" 
                    size={18} 
                    onPress={onNextWeek} 
                    disabled={displayWeek >= maxWeeks} 
                    style={{ margin: 0, width: 24, height: 24 }} 
                />
            </View>
        </Surface>
    );
});

export default ScheduleHeader;