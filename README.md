# GarPulse

[![Release](https://img.shields.io/github/v/release/addycloud/garpulse)](https://github.com/addycloud/garpulse/releases/latest)
[![License: GPL-3.0-or-later](https://img.shields.io/github/license/addycloud/garpulse)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![minSdk 28](https://img.shields.io/badge/minSdk-28-3DDC84?logo=android&logoColor=white)

**English** | [简体中文](#简体中文)

A private, **read-only** health metrics widget app for Android. GarPulse reads your health data from [Health Connect](https://developer.android.com/health-and-fitness/guidance/health-connect) and the [Garmin Personal API](https://developer.garmin.com/connect-api/), and shows it right on your home screen — no accounts, no trackers, your data stays on your device.

> No accounts. No trackers. Just your data, on your device.

## Features

- **Universal widget** — one freely resizable widget (1×1 to 4×5) that shows any mix of metrics, icons, or a photo; edit-first add flow lets you configure before it lands on the home screen
- **Multi-source with priority** — Health Connect, Garmin (Global) and Garmin (China) can each be enabled, disabled and reordered; a value comes from the first enabled source that has data
- **Garmin Personal API** — direct connection with your own personal API key, both international and China endpoints supported (keys can be switched off without being lost)
- **Image widgets** — use your own photo as the background, with metric cards floating on top
- **Adaptive layout** — text auto-shrinks, wraps and ellipsizes so every card stays readable from 2 up to 10 columns
- **Configurable refresh** — background refresh interval plus fixed daily times; widgets also refresh instantly when you open the app
- **Tap shortcuts** — each metric, the background or the refresh strip can launch an app or an in-app page
- **In-app updates** — check for new releases inside the app; it picks the fastest mirror (GitHub / Gitee)
- **Export** — export your configured launch targets as txt (handy to hand to an AI), and your health data as CSV
- **Material You** — dynamic colors, dark mode, multiple launcher icon styles

## Download

**GitHub Releases** — <https://github.com/addycloud/garpulse/releases/latest>
Direct APK: `https://github.com/addycloud/garpulse/releases/download/v1.2.11/garpulse-v1.2.11.apk`

**Gitee Releases (mirror)** — <https://gitee.com/addycloud/GarPulse/releases/latest>
Direct APK: `https://gitee.com/addycloud/GarPulse/releases/download/v1.2.11/garpulse-v1.2.11.apk`

Testing channel (debug build, may be ahead of the release): `https://github.com/addycloud/garpulse/releases/tag/v1.2.11-dev`
Debug builds from every commit are available as [GitHub Actions artifacts](https://github.com/addycloud/garpulse/actions) (`garpulse-debug-apk`, requires a GitHub login). Source code is mirrored on Gitee: <https://gitee.com/addycloud/GarPulse>

## Data sources

| Source | Requires | Notes |
|---|---|---|
| Health Connect | Android 14+ built-in, installable on 9+ | Reads data synced by your phone/wearables |
| Garmin (Global) | [Personal API key](https://connectus.garmin.com/modern/settings/personalApiToken) | Direct, key stays on device |
| Garmin (China) | [Personal API key](https://connect.garmin.cn/app/settings/personalApiToken) | Direct, key stays on device |

All sources are off-by-default switches except Health Connect; reorder them to set the priority. Keys are never echoed back and disabling a source keeps its key.

## Privacy

- No analytics, no ads, no network calls except the Garmin endpoints you configure and release checks on GitHub/Gitee
- Health data is read on device and rendered on device; nothing is uploaded unless you export it yourself
- Keys and tokens live in app-private storage

## Build

Requirements: Android Studio (or CLI + Android SDK), JDK 17.

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

```bash
./gradlew assembleRelease
```

Release builds need signing configuration (`keystore.properties` or CI secrets); without it the APK is unsigned.

## License

[GPL-3.0-or-later](LICENSE)

---

## 简体中文

**[English](#garpulse-1)** | 简体中文

一款注重隐私、**只读**的 Android 健康数据桌面小组件应用。GarPulse 从 [Health Connect](https://developer.android.com/health-and-fitness/guidance/health-connect) 和[佳明 Personal API](https://developer.garmin.com/connect-api/) 读取健康数据，直接显示在桌面上——不需要账号、没有跟踪、数据全部留在本机。

> 不要账号。不要跟踪。只要你的数据，留在你的设备上。

## 功能特性

- **万能小组件** —— 一个自由缩放的小组件（1×1 到 4×5），任意组合指标、图标或照片；先编辑后添加，配置好在落到桌面
- **多数据源 + 优先级** —— 健康同步（Health Connect）、佳明国际区、佳明国区可分别启用/关闭并排序，数值取第一个有数据的来源
- **佳明 Personal API** —— 使用自己的个人 API 密钥直连，国际区和国区端点都支持（关闭来源不会丢密钥）
- **图片小组件** —— 用自己的照片做背景，指标卡片浮在上面
- **自适应排版** —— 文字自动缩小、换行、省略，2 到 10 列都保证可读
- **可配置刷新** —— 后台刷新间隔 + 每天定点更新；打开应用时小组件立即刷新
- **点按快捷** —— 指标、背景、刷新条都可以设置为启动某应用或应用内页面
- **应用内更新** —— 应用内检查新版本，自动探测 GitHub / Gitee 择优下载
- **导出** —— 一键导出点按目标列表 txt（方便交给 AI），健康数据导出 CSV
- **Material You** —— 动态取色、深色模式、多种桌面图标风格

## 下载

**Gitee 版本发布** —— <https://gitee.com/addycloud/GarPulse/releases/latest>
APK 直链：`https://gitee.com/addycloud/GarPulse/releases/download/v1.2.11/garpulse-v1.2.11.apk`

**GitHub 版本发布（镜像）** —— <https://github.com/addycloud/garpulse/releases/latest>
APK 直链：`https://github.com/addycloud/garpulse/releases/download/v1.2.11/garpulse-v1.2.11.apk`

测试版（debug，可能比正式版更新）：`https://gitee.com/addycloud/GarPulse/releases/tag/v1.2.11-dev`
每次提交的 debug 包可在 [GitHub Actions](https://github.com/addycloud/garpulse/actions) 的 Artifacts 中下载（`garpulse-debug-apk`，需登录 GitHub）。源码仓库地址：<https://gitee.com/addycloud/GarPulse>

## 数据来源

| 来源 | 前置条件 | 说明 |
|---|---|---|
| 健康同步（Health Connect） | Android 14+ 内置，9+ 可安装 | 读取手机/穿戴设备同步的数据 |
| 佳明国际区 | [个人 API 密钥](https://connectus.garmin.com/modern/settings/personalApiToken) | 直连，密钥保存在本机 |
| 佳明国区 | [个人 API 密钥](https://connect.garmin.cn/app/settings/personalApiToken) | 直连，密钥保存在本机 |

除健康同步默认开启外，其余来源均为默认关闭；上下移动即可调整优先级。密钥不会再次显示，关闭来源也不会丢失已保存的密钥。

## 隐私

- 无统计、无广告；除你配置的佳明端点和 GitHub/Gitee 版本检查外没有任何网络请求
- 健康数据只在本机读取和渲染；除非你主动导出，否则不会上传
- 密钥与令牌保存在应用私有存储中

## 构建

环境要求：Android Studio（或命令行 + Android SDK）、JDK 17。

```bash
./gradlew assembleDebug
```

debug 包输出在 `app/build/outputs/apk/debug/`。

```bash
./gradlew assembleRelease
```

正式构建需要签名配置（`keystore.properties` 或 CI secrets），否则产物为未签名 APK。

## 许可证

[GPL-3.0-or-later](LICENSE)
