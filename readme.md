<div align="center">
  <img src="./assets/icon.png" alt="Logo" width="100" height="100">

  <h1 align="center">集盒 (JiHe)</h1>

  <p align="center">
    <strong>一个极简、强大且高度个性化的学生日程管理助手</strong>
  </p>

  <p align="center">
    <img src="https://img.shields.io/badge/Platform-Android-green?style=flat-square" alt="Platform">
    <img src="https://img.shields.io/badge/Built%20with-Expo-black?style=flat-square&logo=expo" alt="Expo">
    <img src="https://img.shields.io/badge/Language-TypeScript-blue?style=flat-square&logo=typescript" alt="TypeScript">
    <img src="https://img.shields.io/badge/UI-React%20Native%20Paper-purple?style=flat-square" alt="UI">
    <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License">
  </p>
</div>

---

## 📖 简介 | Introduction

**集盒** 是一款专为学生群体设计的跨平台应用（目前专注于 Android 体验，未来将会开发ios以及Windows端）。它集成了**智能课程表**、**待办事项管理**、**桌面小组件**以及**强大的个性化系统**。

无论是复杂的大学排课（单双周、多时段），还是琐碎的作业考试提醒，集盒都能帮你井井有条地管理。最重要的是，它允许你深度定制界面，让你的日程表不再单调。

## ✨ 核心功能 | Features

### 📅 智能课程表
* **多课表管理**：支持创建多个学期课表，一键切换。
* **复杂排课支持**：完美支持单双周、自定义周次、每天最多 12 节课的灵活配置。
* **OCR 智能导入**：(Beta) 利用 AI 识别技术，从教务系统截图一键导入课程。
* **可视化视图**：直观的周视图，支持点击查看课程详情。

### ✅ 待办事项 (Todo)
* **分类管理**：支持标签分类（考试、作业、生日、纪念日等）。
* **强力提醒**：支持准点提醒、提前 X 分钟提醒，不再错过 DDL。
* **重复规则**：支持按天、周、月、年重复（适合生日或例会）。
* **双视图**：支持列表视图与日历月视图切换。

### 🎨 深度个性化
* **沉浸式体验**：支持自定义背景图片，调节背景/边框/色块透明度。
* **界面适配**：支持“沉浸式头部导航”与“深色背景文字强制反白”，完美适配各种壁纸。
* **多主题色**：内置多种莫兰迪色系主题，支持深色模式 (Dark Mode) 自动切换。

### 🧩 桌面小组件 (Android Widget)
* **多种尺寸**：提供 `4x2`, `3x2`, `4x4` 等多种尺寸的桌面插件。
* **实时同步**：桌面直接查看今日课程与待办，无需打开 App。
* **兼容模式**：特别提供“异常使用”版组件（Safe Mode），自带边距，防止部分国产 ROM 裁切显示不全。

## 📸 预览 | Screenshots

| 主页 (课程表) | 待办事项 (日历) | 个性化设置 | 桌面小组件 |
| :---: | :---: | :---: | :---: |
| <img src="./docs/screenshots/home.jpg" width="200" alt="Home"> | <img src="./docs/screenshots/todo.jpg" width="200" alt="Todo"> | <img src="./docs/screenshots/settings.jpg" width="200" alt="Settings"> | <img src="./docs/screenshots/widget.jpg" width="200" alt="Widget"> |



## 🛠️ 技术栈 | Tech Stack

* **Core**: [React Native](https://reactnative.dev/), [Expo SDK 52](https://expo.dev/)
* **Language**: [TypeScript](https://www.typescriptlang.org/)
* **UI Framework**: [React Native Paper (v5)](https://callstack.github.io/react-native-paper/)
* **Navigation**: [React Navigation](https://reactnavigation.org/)
* **Storage**: `@react-native-async-storage/async-storage`
* **Widgets**: `react-native-android-widget`
* **Notifications**: `expo-notifications`

## 🚀 快速开始 | Getting Started

* 直接前往[release页面](https://github.com/wsryhc/JiHe-Schedule/releases)下载版本

## ⚠️ 注意事项 | Notes

* **通知权限**：安装后请务必在系统设置中允许“通知权限”，并建议开启“自启动”和“省电策略无限制”，以确保提醒能准时送达。
* **小组件刷新**：如果在添加小组件后显示透明或未刷新，请进入 App 随意添加或修改一个待办事项，即可触发数据同步。

## 🗺️ 开发计划 | Roadmap

- [x] 基础课程表功能
- [x] 待办事项与提醒
- [x] 安卓桌面小组件
- [x] 个性化背景与透明度调节
- [ ] 更多功能的添加与适配
- [ ] iOS 平台适配
- [ ] 桌面端 (Windows/Mac) 支持
- [ ] 课程表导入适配更多学校教务系统

## 🤝 贡献 | Contributing

欢迎提交 Issue 或 Pull Request！如果你有好的想法，欢迎在 Issues 中讨论。

## 📄 许可证 | License

本项目采用 [MIT License](LICENSE) 许可证。

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/wsryhc">wsryhc</a>
</p>