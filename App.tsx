// App.tsx
import React from 'react';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { PaperProvider, Title } from 'react-native-paper';
import { Ionicons } from '@expo/vector-icons';

import IndexPage from './src/pages/IndexPage';
import SettingsPage from './src/pages/SettingsPage';
import TimeSettingPage from './src/pages/TimeSettingPage';
import RemindPage from './src/pages/RemindPage';
import CourseEditPage from './src/pages/CourseEditPage';
import ScheduleManagementPage from './src/pages/ScheduleManagementPage';
import TodoEditPage from './src/pages/TodoEditPage';
import ScheduleCreatePage from './src/pages/ScheduleCreatePage';
import NotificationSettingPage from './src/pages/NotificationSettingPage';
// 🔥 1. 引入新页面
import TodoManagementPage from './src/pages/TodoManagementPage';

import { ThemeProvider, useTheme } from './src/context/ThemeContext';
import { ScheduleProvider, useSchedule } from './src/context/ScheduleContext';

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

// 底部导航栏配置
function BottomTabs() {
  const { theme } = useTheme();
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ focused, size }) => {
          let iconName: any = 'alert';
          if (route.name === 'Schedule') {
            iconName = focused ? 'calendar' : 'calendar-outline';
          } else if (route.name === 'Todo') {
            iconName = focused ? 'list' : 'list-outline';
          } else if (route.name === 'Settings') {
            iconName = focused ? 'settings' : 'settings-outline';
          }
          return <Ionicons name={iconName} size={size} color={focused ? theme.primary : theme.subText} />;
        },
        headerTitleAlign: 'center',
        headerTitleStyle: { textAlign: 'center', alignSelf: 'center' },
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.subText,
        tabBarStyle: { backgroundColor: theme.card, borderTopColor: theme.border },
        headerStyle: { backgroundColor: theme.card },
        headerTintColor: theme.text,
      })}
    >
      <Tab.Screen name="Todo" component={RemindPage} options={{ title: '待办' }} />
      <Tab.Screen name="Schedule" component={IndexPage} options={{ title: '课表' }} />
      <Tab.Screen name="Settings" component={SettingsPage} options={{ title: '设置' }} />
    </Tab.Navigator>
  );
}

function AppNavigation() {
  const { isDarkMode } = useTheme();

  return (
    <NavigationContainer theme={isDarkMode ? DarkTheme : DefaultTheme}>
      <Stack.Navigator>
        {/* 主要 Tab 页面 */}
        <Stack.Screen name="MainTabs" component={BottomTabs} options={{ headerShown: false }} />

        {/* 新建/导入课表 */}
        <Stack.Screen name="ScheduleCreate" component={ScheduleCreatePage}
          options={{ title: '新建课表', headerBackTitle: '返回' }} />

        {/* 二级页面 (覆盖底部栏) */}
        <Stack.Screen name="TimeSettings" component={TimeSettingPage} options={{ title: '设置时间' }} />
        <Stack.Screen name="CourseEdit" component={CourseEditPage} options={{ title: '编辑课程' }} />
        <Stack.Screen name="ScheduleManagement" component={ScheduleManagementPage} options={{ title: '课表管理' }} />
        <Stack.Screen name="TodoEdit" component={TodoEditPage} options={{ title: '编辑待办' }} />
        <Stack.Screen name="NotificationSetting" component={NotificationSettingPage} options={{ title: '消息提醒' }} />

        {/* 🔥 2. 注册待办管理页面 */}
        <Stack.Screen name="TodoManagement" component={TodoManagementPage} options={{ title: '待办管理' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <ScheduleProvider>
        <PaperProvider>
          <AppNavigation />
        </PaperProvider>
      </ScheduleProvider>
    </ThemeProvider>
  );
}