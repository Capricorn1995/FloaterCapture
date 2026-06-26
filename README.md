# FloaterCapture - Android 悬浮窗内容抓取助手

一个强大的 Android 应用程序，通过悬浮窗和无障碍服务，帮助用户从其他应用中检测、预览和下载多媒体内容。

## 功能特性

- **悬浮窗控制面板** — 可拖拽的悬浮窗 FAB，点击展开控制面板，实时显示媒体抓取统计
- **智能媒体识别** — 利用无障碍服务遍历 App 界面节点树，自动识别图片、视频、文档等媒体
- **多 App 规则适配** — 内置 40+ 常用 App 的视图识别规则，覆盖国内外主流社交、电商、浏览器
- **批量下载管理** — 支持多文件并发下载，可暂停/恢复，实时进度通知
- **媒体历史列表** — 按类型和来源筛选浏览所有已捕获媒体
- **屏幕截图** — 支持 MediaProjection 系统级屏幕截图
- **深色模式** — 完整适配系统深色主题
- **设置持久化** — WiFi only、下载通知、并发数等设置自动保存

## 架构概览

```
com.floatercapture
├── data/
│   ├── db/          # 数据库（内存存储 + DataStore）
│   ├── model/       # 数据模型（MediaItem, DownloadTask, AppRule）
│   └── repository/  # 数据仓库
├── service/         # 核心服务
│   ├── FloatingWindowService  # 悬浮窗管理
│   ├── MediaCaptureService    # 无障碍媒体捕获
│   ├── DownloadService        # 下载引擎
│   └── ScreenCaptureService   # 屏幕截图
├── ui/
│   ├── main/        # 主界面（首页、媒体列表、下载列表、预览）
│   ├── settings/    # 设置页
│   └── theme/       # Material 3 主题
└── util/            # 工具类（权限、通知、文件、App规则）
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 显示悬浮窗控制面板 |
| `BIND_ACCESSIBILITY_SERVICE` | 监听其他 App 界面，提取媒体内容 |
| `FOREGROUND_SERVICE` | 保持服务在后台运行 |
| `INTERNET` | 下载媒体文件 |
| `POST_NOTIFICATIONS` | 显示下载进度通知 |
| `READ_MEDIA_IMAGES/VIDEO` | 访问媒体库（Android 13+） |

## 构建说明

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- Gradle 8.2+
- Kotlin 1.9.20
- compileSdk 34 / minSdk 24

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/Capricorn1995/FloaterCapture.git
cd FloaterCapture

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 使用指南

1. **首次启动** — 打开应用，按照引导授予悬浮窗权限和无障碍服务权限
2. **开始捕获** — 点击首页"开始捕获"按钮或从设置页开启悬浮窗
3. **浏览内容** — 切换到其他 App（微信、微博、抖音等），悬浮窗会自动检测媒体内容
4. **下载管理** — 点击悬浮窗展开控制面板，查看捕获统计，一键下载全部
5. **查看结果** — 在"媒体"标签页浏览所有捕获内容，支持按类型和来源筛选

## 支持的 App（部分列表）

### 国内 App
微信 · QQ · 微博 · 抖音 · 快手 · 小红书 · 知乎 · B站 · 淘宝 · 京东 · 拼多多 · 今日头条 · 网易云音乐 · 美图秀秀 · 豆瓣 · 酷安 · 最右

### 国际 App
Telegram · Twitter/X · Instagram · TikTok · YouTube · Facebook · WhatsApp · Snapchat · Reddit · Pinterest · Spotify

### 浏览器
Chrome · Firefox · Edge · Samsung Internet · UC Browser

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **数据库**: Room (内存存储) + DataStore Preferences
- **网络**: OkHttp 4
- **图片**: Coil
- **架构**: MVVM + Repository Pattern

## 许可证

MIT License
