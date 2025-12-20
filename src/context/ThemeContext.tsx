// src/context/ThemeContext.tsx
import React, { createContext, useState, useContext, useEffect, useMemo } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { MD3LightTheme, MD3DarkTheme, Provider as PaperProvider, adaptNavigationTheme } from 'react-native-paper';
import {
    DarkTheme as NavigationDarkTheme,
    DefaultTheme as NavigationDefaultTheme,
    ThemeProvider as NavigationThemeProvider,
} from '@react-navigation/native';

const THEME_STORAGE_KEY = 'MY_APP_THEME_V1';
const CUSTOM_SETTINGS_KEY = 'MY_APP_CUSTOM_SETTINGS_V1';
const DEFAULT_PRIMARY = '#2196F3';

export type ThemeMode = 'light' | 'dark' | 'auto';

export interface CustomSettings {
    backgroundImage?: string | null;
    bgImageOpacity?: number;
    borderOpacity?: number;
    courseOpacity?: number;
    transparentHeader?: boolean;
    forceWhiteContent?: boolean;

    // 日程页个性化字段
    remindBackgroundImage?: string | null;
    remindBgImageOpacity?: number;
    remindTransparentHeader?: boolean;
    remindForceWhiteContent?: boolean;
    // 🔥 新增：事项卡片透明度 & 日历格透明度
    remindItemOpacity?: number;
    remindCalendarCellOpacity?: number;
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
    themeMode: ThemeMode;
    setThemeMode: (mode: ThemeMode) => void;
    primaryColor: string;
    changePrimaryColor: (color: string) => void;
    theme: any;
    customSettings: CustomSettings;
    updateCustomSettings: (settings: Partial<CustomSettings>) => void;
    isDarkMode: boolean;
}

const ThemeContext = createContext<ThemeContextType>({} as ThemeContextType);

export const ThemeProvider = ({ children }: any) => {
    const systemScheme = useColorScheme();
    const [themeMode, setThemeModeState] = useState<ThemeMode>('auto');
    const [primaryColor, setPrimaryColor] = useState(DEFAULT_PRIMARY);

    const [customSettings, setCustomSettings] = useState<CustomSettings>({
        backgroundImage: null,
        bgImageOpacity: 1,
        borderOpacity: 0.1,
        courseOpacity: 0.85,
        transparentHeader: false,
        forceWhiteContent: false,

        remindBackgroundImage: null,
        remindBgImageOpacity: 1,
        remindTransparentHeader: false,
        remindForceWhiteContent: false,
        // 🔥 新增默认值
        remindItemOpacity: 0.85,
        remindCalendarCellOpacity: 0.1,
    });

    const [isLoaded, setIsLoaded] = useState(false);

    useEffect(() => {
        const load = async () => {
            try {
                const storedTheme = await AsyncStorage.getItem(THEME_STORAGE_KEY);
                if (storedTheme) {
                    const parsed = JSON.parse(storedTheme);
                    if (parsed.themeMode) setThemeModeState(parsed.themeMode);
                    if (parsed.primaryColor) setPrimaryColor(parsed.primaryColor);
                }

                const storedSettings = await AsyncStorage.getItem(CUSTOM_SETTINGS_KEY);
                if (storedSettings) {
                    setCustomSettings(prev => ({ ...prev, ...JSON.parse(storedSettings) }));
                }
            } catch (e) {
                console.error("加载设置失败", e);
            } finally {
                setIsLoaded(true);
            }
        };
        load();
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

    const setThemeMode = (mode: ThemeMode) => {
        setThemeModeState(mode);
    };

    const changePrimaryColor = (color: string) => {
        setPrimaryColor(color);
    };

    const updateCustomSettings = (newSettings: Partial<CustomSettings>) => {
        setCustomSettings(prev => ({ ...prev, ...newSettings }));
    };

    const isDarkMode = useMemo(() => {
        if (themeMode === 'auto') return systemScheme === 'dark';
        return themeMode === 'dark';
    }, [themeMode, systemScheme]);

    const { paperTheme, appTheme } = useMemo(() => {
        const baseTheme = isDarkMode ? MD3DarkTheme : MD3LightTheme;

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
            },
            fonts: baseTheme.fonts,
        };

        const extendedTheme = {
            ...combinedTheme,
            text: combinedTheme.colors.onSurface,
            subText: combinedTheme.colors.onSurfaceVariant,
            background: combinedTheme.colors.background,
            card: combinedTheme.colors.surface,
            border: combinedTheme.colors.outline,
            primary: primaryColor
        };

        return { paperTheme: combinedTheme, appTheme: extendedTheme };
    }, [isDarkMode, primaryColor]);

    const navigationTheme = isDarkMode ? NavigationDarkTheme : NavigationDefaultTheme;
    // @ts-ignore
    navigationTheme.colors.primary = primaryColor;
    // @ts-ignore
    navigationTheme.colors.background = appTheme.colors.background;
    // @ts-ignore
    navigationTheme.colors.card = appTheme.colors.card;
    // @ts-ignore
    navigationTheme.colors.text = appTheme.colors.text;

    return (
        <ThemeContext.Provider value={{
            isDarkMode, themeMode, setThemeMode,
            primaryColor, changePrimaryColor,
            theme: appTheme,
            customSettings, updateCustomSettings
        }}>
            <PaperProvider theme={paperTheme}>
                <NavigationThemeProvider value={navigationTheme}>
                    {children}
                </NavigationThemeProvider>
            </PaperProvider>
        </ThemeContext.Provider>
    );
};

export const useTheme = () => useContext(ThemeContext);