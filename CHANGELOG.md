# SkyPulse 閳?Changelog & Memory

> Auto-maintained by Codex. Each modification bumps the patch version.

---

## [1.8.75] - 2026-05-29

- **城市列表卡片高度稳定**: 移除条件渲染，数据未加载时用占位符填充，卡片不再因数据加载而展开/收缩
- **静默刷新**: 进入城市列表时数据在后台加载，不影响卡片布局

## [1.8.74] - 2026-05-29

- **Smooth alert carousel**: Replaced LazyColumn scroll with AnimatedContent vertical slide + crossfade
- **No more flicker**: Each alert slides out upward while the next slides in from below, with fade overlay

## [1.8.73] - 2026-05-29

- **Smooth text offset**: Replaced bouncy spring with 	ween(350ms, FastOutSlowInEasing)
- **Smoother alert scroll**: Replaced manual Animatable offset with nimateScrollToItem, no more flicker on loop

## [1.8.72] - 2026-05-29

- **Smooth text animation**: Replaced instant offset with spring-physics nimateDpAsState (bouncy damping, low stiffness)
- **Status slide-in**: Refresh status now fades in with vertical slide from below, fades out with upward slide
- **Longer transitions**: fadeIn 300ms, fadeOut 200ms for smoother feel

## [1.8.71] - 2026-05-29

- **Fix text alignment**: Text now uses CenterStart alignment so it stays on same line as icons when idle
- **Adjusted offset**: Text moves up 10dp from centered position when refresh is active

## [1.8.70] - 2026-05-29

### Location Header Animation Fix

- **Only city text moves**: Removed Row-level offset animation, applied offset only to city name Text
- **Fixed icon/button position**: Location icon and right-side buttons (menu/settings) stay completely stationary
- **Status inside text area**: Refresh status (spinner + text) appears at BottomStart of 36dp text Box
- **Smooth transition**: City text slides up 8dp via offset animation, status fades in below via AnimatedVisibility
- **Removed old status box**: Eliminated separate 12dp status Box that was below the Row

## [1.8.65] 閳?2026-05-29

### UI 璺?Color System Overhaul

- **Text hierarchy**: Added TextTertiary (60% white) and TextDisabled (40% white) for clearer visual hierarchy
- **Alert tokens**: Extracted AlertRed / AlertOrange / AlertYellow / AlertBlue from inline hardcodes to Color.kt
- **Precipitation tokens**: Added PrecipBarTop / PrecipBarBottom / PrecipBarShadow replacing hardcoded values
- **Chart contrast**: HourlyForecast chart labels now use TextSecondary instead of hardcoded alpha
- **GlassCard day/night**: Dynamic border opacity 閳?CardBorderDay (40%) vs CardBorderNight (13%)
- **Night mode**: WeatherTheme now carries cardBorderColor, 	extTertiary, pressedOverlay, disabledOverlay
- **Interactive states**: Added PressedOverlay (8% white) and DisabledOverlay (10% black) tokens
- **DailyForecast**: Date labels and weather descriptions use TextTertiary for depth

**Build workflow**: APK renamed to versioned filename + auto-upload to cloud clipboard

**Files changed** (9):
Color.kt 璺?WeatherTheme.kt 璺?Theme.kt 璺?GlassCard.kt 璺?MinutelyPrecipitation.kt 璺?WeatherScreen.kt 璺?CurrentWeather.kt 璺?HourlyForecast.kt 璺?DailyForecast.kt 璺?WeatherUtils.kt

---
## [1.8.85] 鈥?2026-05-31

- fix: 图标占比微调

## [1.8.84] 鈥?2026-05-31

- fix: 图标前景尺寸调整

## [1.8.83] 鈥?2026-05-31

- fix: 应用图标裁剪适配

## [1.8.82] 鈥?2026-05-31

- feat: 更换应用图标

## [1.8.81] 鈥?2026-05-31

- fix: 预警详情页标题与返回图标颜色改为与关于页一致

## [1.8.80] 鈥?2026-05-31

- fix: 预警详情页背景改为与城市列表/关于页一致的深色渐变

## [1.8.79] 鈥?2026-05-31

- fix: 多条预警轮播点击后详情页直接展示全部预警

## [1.8.78] 鈥?2026-05-31

- feat: 预警按钮点击跳转预警详情页


## [1.8.75] - 2026-05-29

- **城市列表卡片高度稳定**: 移除条件渲染，数据未加载时用占位符填充，卡片不再因数据加载而展开/收缩
- **静默刷新**: 进入城市列表时数据在后台加载，不影响卡片布局

## [1.8.74] - 2026-05-29

- **Smooth alert carousel**: Replaced LazyColumn scroll with AnimatedContent vertical slide + crossfade
- **No more flicker**: Each alert slides out upward while the next slides in from below, with fade overlay

## [1.8.73] - 2026-05-29

- **Smooth text offset**: Replaced bouncy spring with 	ween(350ms, FastOutSlowInEasing)
- **Smoother alert scroll**: Replaced manual Animatable offset with nimateScrollToItem, no more flicker on loop

## [1.8.72] - 2026-05-29

- **Smooth text animation**: Replaced instant offset with spring-physics nimateDpAsState (bouncy damping, low stiffness)
- **Status slide-in**: Refresh status now fades in with vertical slide from below, fades out with upward slide
- **Longer transitions**: fadeIn 300ms, fadeOut 200ms for smoother feel

## [1.8.71] - 2026-05-29

- **Fix text alignment**: Text now uses CenterStart alignment so it stays on same line as icons when idle
- **Adjusted offset**: Text moves up 10dp from centered position when refresh is active

## [1.8.70] - 2026-05-29

### Location Header Animation Fix

- **Only city text moves**: Removed Row-level offset animation, applied offset only to city name Text
- **Fixed icon/button position**: Location icon and right-side buttons (menu/settings) stay completely stationary
- **Status inside text area**: Refresh status (spinner + text) appears at BottomStart of 36dp text Box
- **Smooth transition**: City text slides up 8dp via offset animation, status fades in below via AnimatedVisibility
- **Removed old status box**: Eliminated separate 12dp status Box that was below the Row
## [1.8.69] 鈥?2026-05-29

- Refresh status: location text slides up via offset animation, status fades in below, no page shift

## [1.8.68] 鈥?2026-05-29

- Refresh status: replace fixed-height Box with expandVertics animation below location text

## [1.8.67] 鈥?2026-05-29

- Fix refresh indicator clipping: increase status row height 12dp->16dp to fit 14dp spinner without layout shift

## [1.8.66] 鈥?2026-05-29

- Fix refresh indicator clipping: remove fixed height constraint on status row

## [1.8.65] 閳?2026-05-29

- UI color system overhaul: text hierarchy, alert tokens, precipitation tokens, chart contrast, GlassCard day/night, night mode, interactive states


## [1.8.64] 閳?baseline

- Project handed to Codex. Starting version: 1.8.64 (versionCode 144).

---

## [1.8.75] - 2026-05-29

- **城市列表卡片高度稳定**: 移除条件渲染，数据未加载时用占位符填充，卡片不再因数据加载而展开/收缩
- **静默刷新**: 进入城市列表时数据在后台加载，不影响卡片布局

## 项目记忆（Codex 自动维护）

### GitHub 配置
- **仓库**: github.com/qnmlgbd250/weather-none
- **分支**: main
- **GitHub Token (classic)**: {GITHUB_TOKEN}
- **Release ID**: 通过 API 查询 /repos/qnmlgbd250/weather-none/releases 获取

### 日常迭代流程（默认）
1. 修改代码 → 自动递增版本号（versionCode + versionName）
2. 更新 CHANGELOG.md
3. 构建 Release APK
4. 重命名 APK 为 `SkyPulse-v{版本号}.apk`
5. 上传 APK 到云端剪贴板：`curl -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"`
6. Git 提交 + push 到 origin/main
7. **不创建 GitHub Release**

### 发版流程（仅用户主动要求"发版"时执行）
1. 执行上述日常迭代流程的所有步骤
2. 打 tag + push tag
3. 通过 GitHub API 创建 Release（用 Node.js 发请求，确保中文 UTF-8 编码正确）
4. 通过 GitHub API 上传 APK 到 Release 附件
1. 修改代码 → 自动递增版本号（versionCode + versionName）
2. 更新 CHANGELOG.md
3. 构建 Release APK：gradlew assembleRelease（JAVA_HOME = C:\Program Files\Android\Android Studio\jbr）
4. 重命名 APK 为 SkyPulse-v{版本号}.apk
5. 上传 APK 到云端剪贴板：curl -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"
6. Git 提交 + 打 tag + push 到 origin/main
7. 通过 GitHub API 创建 Release（用 Node.js 发请求，确保中文 UTF-8 编码正确）
8. 通过 GitHub API 上传 APK 到 Release 附件

### 发版要求
- **Release 描述必须使用中文**
- **APK 必须上传到 Release 附件**
- **GitHub API 调用使用 Node.js**（PowerShell Invoke-RestMethod 中文编码有问题）
- **仅在用户主动要求发版时才执行发版流程**

### 签名配置
- Keystore: app/release-keystore.jks
- storePassword: weather123
- keyAlias: weather-app
- keyPassword: weather123

### 构建环境
- JAVA_HOME: C:\Program Files\Android\Android Studio\jbr
- Gradle: C:\Users\phil\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5
- 不能用 gradlew（sandbox 会阻止 wrapper 的网络检查），必须直接调用 gradle.bat