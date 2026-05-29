# Settings Page Design Spec

## Overview

Add a settings/about page to SkyPulse weather app, accessible via a vertical 3-dot icon (MoreVert) in the weather detail screen's header.

## Requirements

- **Entry point**: Vertical 3-dot icon next to the existing Menu icon in `LocationHeader`
- **Navigation**: New `AppScreen.Settings` enum value, managed by `WeatherViewModel`
- **Page style**: Material3 list with icons and dividers
- **Items**:
  - GitHub 仓库 — opens GitHub URL in browser
  - 打赏作者 — shows payment QR codes (支付宝/微信) in a dialog
  - 检查更新 — checks GitHub Releases API for latest version
- **Footer**: App name + current version (`SkyPulse v1.8.34`)
- **Background**: Same `NightFallbackGradient` as CityListScreen for consistency

## Architecture

### Files to Create

1. **`ui/screen/SettingsScreen.kt`** — Settings page composable
2. **`ui/components/DonateDialog.kt`** — QR code dialog for donations

### Files to Modify

1. **`viewmodel/WeatherViewModel.kt`** — Add `Settings` to `AppScreen`, add `navigateToSettings()` and `checkForUpdates()` methods
2. **`ui/screen/WeatherScreen.kt`** — Add `Settings` case to `when(currentScreen)` block, pass `onSettingsClick` to `WeatherContent`
3. **`ui/components/CurrentWeather.kt`** — Add MoreVert icon button in `LocationHeader`, accept `onSettingsClick` callback
4. **`build.gradle.kts`** — May need `androidx.browser:browser` for Custom Tabs (opening GitHub URL)

## Navigation Flow

```
AppScreen.CityDetail
  └── LocationHeader
        ├── [LocationIcon] [CityName] ... [MenuIcon] [MoreVertIcon]
        │                                        │           │
        │                                        │           └── navigateToSettings()
        │                                        └── navigateToCityList()
        │
        └── AppScreen.Settings
              ├── SettingsScreen
              │     ├── GitHub 仓库 → open browser
              │     ├── 打赏作者 → DonateDialog
              │     ├── 检查更新 → GitHub API check
              │     └── Version footer
              └── Back → AppScreen.CityDetail
```

## Settings Page Layout

```
┌────────────────────────────────┐
│  ← 设置                        │  ← TopAppBar with back arrow
├────────────────────────────────┤
│                                │
│  [GitHub icon]  GitHub 仓库  > │  ← ListItem, opens browser
│  ───────────────────────────── │
│  [Heart icon]   打赏作者    > │  ← ListItem, shows QR dialog
│  ───────────────────────────── │
│  [Refresh icon] 检查更新    > │  ← ListItem, checks GitHub API
│                                │
│                                │
│                                │
│       SkyPulse v1.8.34         │  ← Footer text, centered
│                                │
└────────────────────────────────┘
```

## Check for Updates Logic

1. Call `https://api.github.com/repos/{owner}/{repo}/releases/latest`
2. Extract `tag_name` from response
3. Compare with `BuildConfig.VERSION_NAME`
4. If newer version found: show dialog with download link
5. If current: show Toast "已是最新版本"
6. On error: show Toast "检查更新失败，请稍后重试"

## Donate Dialog

- Material3 `AlertDialog` with two QR code images side by side (支付宝 / 微信)
- QR images stored in `res/drawable/` as `qr_alipay.png` and `qr_wechat.png`
- User provides actual QR images; for now use placeholder drawables

## Dependencies

- `androidx.browser:browser` — for Chrome Custom Tabs (opening GitHub URL)
- OkHttp — already included, for GitHub API call

## Visual Style

- Background: `NightFallbackGradient` (consistent with other screens)
- Text colors: `TextPrimary`, `TextSecondary` (from theme)
- Icons: Material Icons Outlined
- List items: Standard `ListItem` composable from Material3
