// src/context/ThemeContext.tsx
import React, { createContext, useContext, useState, useMemo, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { MD3LightTheme, MD3DarkTheme, adaptNavigationTheme } from 'react-native-paper';
import { DarkTheme as NavigationDarkTheme, DefaultTheme as NavigationDefaultTheme } from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const THEME_STORAGE_KEY = 'MY_APP_THEME_V1';
const CUSTOM_SETTINGS_KEY = 'MY_APP_CUSTOM_SETTINGS_V1';
const DEFAULT_PRIMARY = '#2196F3';

export type ThemeMode = 'light' | 'dark' | 'auto';

export interface CustomSettings {
    backgroundImage?: string | null; 
    borderOpacity: number;    // 🔥 改为：边框透明度 (0 - 1)
    courseOpacity: number;    // 课程块透明度 (0 - 1)
    bgImageOpacity: number;   // 背景图片本身的透明度 (0 - 1)
    transparentHeader: boolean; 
    forceWhiteContent?: boolean;
}

// 颜色透明度处理工具
export const setAlpha = (color: string, opacity: number) => {
    if (!color) return 'transparent';
    if (color.startsWith('#')) {
        let hex = color.replace('#', '');
        if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
        const alpha = Math.round(Math.min(1, Math.max(0, opacity)) * 255).toString(16).padStart(2, '0');
        return `#${hex}${alpha}`;
    }
    if (color.startsWith('rgb')) {
        const matches = color.match(/\d+/g);
        if (matches && matches.length >= 3) {
            const [r, g, b] = matches;
            return `rgba(${r}, ${g}, ${b}, ${opacity})`;
        }
    }
    return color;
};

interface ThemeContextType {
    isDarkMode: boolean;
    themeMode: ThemeMode;
    setThemeMode: (mode: ThemeMode) => void;
    primaryColor: string;
    changePrimaryColor: (color: string) => void;
    customSettings: CustomSettings;
    updateCustomSettings: (settings: Partial<CustomSettings>) => void;
    theme: any;
}

const ThemeContext = createContext<ThemeContextType>({} as ThemeContextType);

export const ThemeProvider = ({ children }: any) => {
    const systemScheme = useColorScheme();

    const [themeMode, setThemeMode] = useState<ThemeMode>('auto');
    const [primaryColor, setPrimaryColor] = useState(DEFAULT_PRIMARY);
    
    // 🔥 更新默认配置
    const [customSettings, setCustomSettings] = useState<CustomSettings>({
        backgroundImage: null,
        borderOpacity: 0.1, // 默认边框很淡
        courseOpacity: 0.85,
        bgImageOpacity: 1.0,
        transparentHeader: false,
    });
    
    const [isLoaded, setIsLoaded] = useState(false);

    useEffect(() => {
        const loadSettings = async () => {
            try {
                const savedThemeStr = await AsyncStorage.getItem(THEME_STORAGE_KEY);
                if (savedThemeStr) {
                    const settings = JSON.parse(savedThemeStr);
                    if (settings.themeMode) setThemeMode(settings.themeMode);
                    else if (settings.isDarkMode !== undefined) setThemeMode(settings.isDarkMode ? 'dark' : 'light');
                    if (settings.primaryColor) setPrimaryColor(settings.primaryColor);
                }

                const savedCustomStr = await AsyncStorage.getItem(CUSTOM_SETTINGS_KEY);
                if (savedCustomStr) {
                    const custom = JSON.parse(savedCustomStr);
                    // 兼容旧数据，如果有 tableOpacity 可以转换或者忽略
                    setCustomSettings(prev => ({ ...prev, ...custom }));
                }
            } catch (e) {
                console.error("加载设置失败", e);
            } finally {
                setIsLoaded(true);
            }
        };
        loadSettings();
    }, []);

    useEffect(() => {
        if (!isLoaded) return;
        const saveSettings = async () => {
            try {
                const themeData = { themeMode, primaryColor };
                await AsyncStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(themeData));
                await AsyncStorage.setItem(CUSTOM_SETTINGS_KEY, JSON.stringify(customSettings));
            } catch (e) {
                console.error("保存设置失败", e);
            }
        };
        saveSettings();
    }, [themeMode, primaryColor, customSettings, isLoaded]);

    const changePrimaryColor = (color: string) => setPrimaryColor(color);
    
    const updateCustomSettings = (newSettings: Partial<CustomSettings>) => {
        setCustomSettings(prev => ({ ...prev, ...newSettings }));
    };

    const isDarkMode = useMemo(() => {
        if (themeMode === 'auto') return systemScheme === 'dark';
        return themeMode === 'dark';
    }, [themeMode, systemScheme]);

    const theme = useMemo(() => {
        const baseTheme = isDarkMode ? MD3DarkTheme : MD3LightTheme;
        const navBaseTheme = isDarkMode ? NavigationDarkTheme : NavigationDefaultTheme;

        const { LightTheme, DarkTheme } = adaptNavigationTheme({
            reactNavigationLight: NavigationDefaultTheme,
            reactNavigationDark: NavigationDarkTheme,
        });

        const combinedTheme = {
            ...baseTheme,
            ...(isDarkMode ? DarkTheme : LightTheme),
            colors: {
                ...baseTheme.colors,
                ...(isDarkMode ? DarkTheme.colors : LightTheme.colors),
                primary: primaryColor,
                primaryContainer: isDarkMode ? primaryColor + '20' : primaryColor + '15',
                onPrimaryContainer: primaryColor,
                secondaryContainer: primaryColor + '10',
            }
        };

        return {
            ...combinedTheme,
            text: combinedTheme.colors.onSurface,
            subText: combinedTheme.colors.onSurfaceVariant,
            background: combinedTheme.colors.background,
            card: combinedTheme.colors.surface,
            border: combinedTheme.colors.outline,
            primary: primaryColor
        };
    }, [isDarkMode, primaryColor]);

    return (
        <ThemeContext.Provider value={{ 
            isDarkMode, themeMode, setThemeMode, 
            primaryColor, changePrimaryColor, 
            customSettings, updateCustomSettings, 
            theme 
        }}>
            {children}
        </ThemeContext.Provider>
    );
};

export const useTheme = () => useContext(ThemeContext);