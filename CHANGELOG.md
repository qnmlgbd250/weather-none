# SkyPulse 閳?Changelog & Memory

> Auto-maintained by Codex. Each modification bumps the patch version.

---

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
