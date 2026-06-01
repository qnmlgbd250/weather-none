## [1.9.0] - 2026-06-02

- fix: 修复城市列表新增城市后重新进入时城市丢失的竞态条件问题
- 修复 init 块与 ensureCurrentLocationCity 之间的时序冲突，添加 citiesLoadJob?.join() 等待初始化完成
## [1.8.98] - 2026-06-01

- fix: 温度条对比度优化：宽度6dp->8dp，添加端点圆点
- fix: 逐小时预报滚动后显示“现在”浮动指示器
- 补充background导入

## [1.8.99] - 2026-06-01

- revert: 回滚温度条对比度优化(8dp+端点圆点)
- revert: 回滚逐小时预报滚动指示器
- 保留动画延迟改动(Minutely=150, Hourly=300, Daily=450)

# SkyPulse 闁?Changelog & Memory

> Auto-maintained by Codex. Each modification bumps the patch version.


## [1.8.88] - 2026-05-31

- **鏂板妗岄潰灏忕粍浠?2x2**: 宸︿笂瑙掓樉绀虹簿绠€浣嶇疆锛?瀛楋級锛屽乏渚т腑闂村疄鏃舵俯搴︼紝宸︿晶搴曢儴澶╂皵鐜拌薄鍜屾渶楂?鏈€浣庢俯锛屽彸涓婅澶╂皵鍥炬爣锛岄厤鑹查殢澶╂皵鍙樺寲
- **WorkManager鑷畾涔夊垵濮嬪寲**: 瀹炵幇Configuration.Provider锛岀Щ闄ら粯璁nitializationProvider
## [1.8.75] - 2026-05-29

- **鍩庡競鍒楄〃鍗＄墖楂樺害绋冲畾**: 绉婚櫎鏉′欢娓叉煋锛屾暟鎹湭鍔犺浇鏃剁敤鍗犱綅绗﹀～鍏咃紝鍗＄墖涓嶅啀鍥犳暟鎹姞杞借€屽睍寮€/鏀剁缉
- **闈欓粯鍒锋柊**: 杩涘叆鍩庡競鍒楄〃鏃舵暟鎹湪鍚庡彴鍔犺浇锛屼笉褰卞搷鍗＄墖甯冨眬

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

## [1.8.65] 闁?2026-05-29

### UI 鐠?Color System Overhaul

- **Text hierarchy**: Added TextTertiary (60% white) and TextDisabled (40% white) for clearer visual hierarchy
- **Alert tokens**: Extracted AlertRed / AlertOrange / AlertYellow / AlertBlue from inline hardcodes to Color.kt
- **Precipitation tokens**: Added PrecipBarTop / PrecipBarBottom / PrecipBarShadow replacing hardcoded values
- **Chart contrast**: HourlyForecast chart labels now use TextSecondary instead of hardcoded alpha
- **GlassCard day/night**: Dynamic border opacity 闁?CardBorderDay (40%) vs CardBorderNight (13%)
- **Night mode**: WeatherTheme now carries cardBorderColor, 	extTertiary, pressedOverlay, disabledOverlay
- **Interactive states**: Added PressedOverlay (8% white) and DisabledOverlay (10% black) tokens
- **DailyForecast**: Date labels and weather descriptions use TextTertiary for depth

**Build workflow**: APK renamed to versioned filename + auto-upload to cloud clipboard

**Files changed** (9):
Color.kt 鐠?WeatherTheme.kt 鐠?Theme.kt 鐠?GlassCard.kt 鐠?MinutelyPrecipitation.kt 鐠?WeatherScreen.kt 鐠?CurrentWeather.kt 鐠?HourlyForecast.kt 鐠?DailyForecast.kt 鐠?WeatherUtils.kt


## [1.8.88] - 2026-05-31

- **鏂板妗岄潰灏忕粍浠?2x2**: 宸︿笂瑙掓樉绀虹簿绠€浣嶇疆锛?瀛楋級锛屽乏渚т腑闂村疄鏃舵俯搴︼紝宸︿晶搴曢儴澶╂皵鐜拌薄鍜屾渶楂?鏈€浣庢俯锛屽彸涓婅澶╂皵鍥炬爣锛岄厤鑹查殢澶╂皵鍙樺寲
- **WorkManager鑷畾涔夊垵濮嬪寲**: 瀹炵幇Configuration.Provider锛岀Щ闄ら粯璁nitializationProvider
## [1.8.87] 閳?2026-05-31

- fix: 鎹愯禒楦ｈ阿浣跨敤鐪熷疄鏁版嵁

## [1.8.86] 閳?2026-05-31

- feat: 鎹愯禒寮圭獥澧炲姞鎵撹祻楦ｈ阿锛涘浘鏍囧啀缂╁皬涓€鐐?
## [1.8.85] 閳?2026-05-31

- fix: 鍥炬爣鍗犳瘮寰皟

## [1.8.84] 閳?2026-05-31

- fix: 鍥炬爣鍓嶆櫙灏哄璋冩暣

## [1.8.83] 閳?2026-05-31

- fix: 搴旂敤鍥炬爣瑁佸壀閫傞厤

## [1.8.82] 閳?2026-05-31

- feat: 鏇存崲搴旂敤鍥炬爣

## [1.8.81] 閳?2026-05-31

- fix: 棰勮璇︽儏椤垫爣棰樹笌杩斿洖鍥炬爣棰滆壊鏀逛负涓庡叧浜庨〉涓€鑷?
## [1.8.80] 閳?2026-05-31

- fix: 棰勮璇︽儏椤佃儗鏅敼涓轰笌鍩庡競鍒楄〃/鍏充簬椤典竴鑷寸殑娣辫壊娓愬彉

## [1.8.79] 閳?2026-05-31

- fix: 澶氭潯棰勮杞挱鐐瑰嚮鍚庤鎯呴〉鐩存帴灞曠ず鍏ㄩ儴棰勮

## [1.8.78] 閳?2026-05-31

- feat: 棰勮鎸夐挳鐐瑰嚮璺宠浆棰勮璇︽儏椤?

## [1.8.75] - 2026-05-29

- **鍩庡競鍒楄〃鍗＄墖楂樺害绋冲畾**: 绉婚櫎鏉′欢娓叉煋锛屾暟鎹湭鍔犺浇鏃剁敤鍗犱綅绗﹀～鍏咃紝鍗＄墖涓嶅啀鍥犳暟鎹姞杞借€屽睍寮€/鏀剁缉
- **闈欓粯鍒锋柊**: 杩涘叆鍩庡競鍒楄〃鏃舵暟鎹湪鍚庡彴鍔犺浇锛屼笉褰卞搷鍗＄墖甯冨眬

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
## [1.8.69] 閳?2026-05-29

- Refresh status: location text slides up via offset animation, status fades in below, no page shift

## [1.8.68] 閳?2026-05-29

- Refresh status: replace fixed-height Box with expandVertics animation below location text

## [1.8.67] 閳?2026-05-29

- Fix refresh indicator clipping: increase status row height 12dp->16dp to fit 14dp spinner without layout shift

## [1.8.66] 閳?2026-05-29

- Fix refresh indicator clipping: remove fixed height constraint on status row

## [1.8.65] 闁?2026-05-29

- UI color system overhaul: text hierarchy, alert tokens, precipitation tokens, chart contrast, GlassCard day/night, night mode, interactive states


## [1.8.64] 闁?baseline

- Project handed to Codex. Starting version: 1.8.64 (versionCode 144).


## [1.8.88] - 2026-05-31

- **鏂板妗岄潰灏忕粍浠?2x2**: 宸︿笂瑙掓樉绀虹簿绠€浣嶇疆锛?瀛楋級锛屽乏渚т腑闂村疄鏃舵俯搴︼紝宸︿晶搴曢儴澶╂皵鐜拌薄鍜屾渶楂?鏈€浣庢俯锛屽彸涓婅澶╂皵鍥炬爣锛岄厤鑹查殢澶╂皵鍙樺寲
- **WorkManager鑷畾涔夊垵濮嬪寲**: 瀹炵幇Configuration.Provider锛岀Щ闄ら粯璁nitializationProvider
## [1.8.75] - 2026-05-29

- **鍩庡競鍒楄〃鍗＄墖楂樺害绋冲畾**: 绉婚櫎鏉′欢娓叉煋锛屾暟鎹湭鍔犺浇鏃剁敤鍗犱綅绗﹀～鍏咃紝鍗＄墖涓嶅啀鍥犳暟鎹姞杞借€屽睍寮€/鏀剁缉
- **闈欓粯鍒锋柊**: 杩涘叆鍩庡競鍒楄〃鏃舵暟鎹湪鍚庡彴鍔犺浇锛屼笉褰卞搷鍗＄墖甯冨眬

## 椤圭洰璁板繂锛圕odex 鑷姩缁存姢锛?
### GitHub 閰嶇疆
- **浠撳簱**: github.com/qnmlgbd250/weather-none
- **鍒嗘敮**: main
- **GitHub Token (classic)**: {GITHUB_TOKEN}
- **Release ID**: 閫氳繃 API 鏌ヨ /repos/qnmlgbd250/weather-none/releases 鑾峰彇

### 鏃ュ父杩唬娴佺▼锛堥粯璁わ級
1. 淇敼浠ｇ爜 鈫?鑷姩閫掑鐗堟湰鍙凤紙versionCode + versionName锛?2. 鏇存柊 CHANGELOG.md
3. 鏋勫缓 Release APK
4. 閲嶅懡鍚?APK 涓?`SkyPulse-v{鐗堟湰鍙穧.apk`
5. 涓婁紶 APK 鍒颁簯绔壀璐存澘锛歚curl -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"`
6. Git 鎻愪氦 + push 鍒?origin/main
7. **涓嶅垱寤?GitHub Release**

### 鍙戠増娴佺▼锛堜粎鐢ㄦ埛涓诲姩瑕佹眰"鍙戠増"鏃舵墽琛岋級
1. 鎵ц涓婅堪鏃ュ父杩唬娴佺▼鐨勬墍鏈夋楠?2. 鎵?tag + push tag
3. 閫氳繃 GitHub API 鍒涘缓 Release锛堢敤 Node.js 鍙戣姹傦紝纭繚涓枃 UTF-8 缂栫爜姝ｇ‘锛?4. 閫氳繃 GitHub API 涓婁紶 APK 鍒?Release 闄勪欢
1. 淇敼浠ｇ爜 鈫?鑷姩閫掑鐗堟湰鍙凤紙versionCode + versionName锛?2. 鏇存柊 CHANGELOG.md
3. 鏋勫缓 Release APK锛歡radlew assembleRelease锛圝AVA_HOME = C:\Program Files\Android\Android Studio\jbr锛?4. 閲嶅懡鍚?APK 涓?SkyPulse-v{鐗堟湰鍙穧.apk
5. 涓婁紶 APK 鍒颁簯绔壀璐存澘锛歝url -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"
6. Git 鎻愪氦 + 鎵?tag + push 鍒?origin/main
7. 閫氳繃 GitHub API 鍒涘缓 Release锛堢敤 Node.js 鍙戣姹傦紝纭繚涓枃 UTF-8 缂栫爜姝ｇ‘锛?8. 閫氳繃 GitHub API 涓婁紶 APK 鍒?Release 闄勪欢

### 鍙戠増瑕佹眰
- **Release 鎻忚堪蹇呴』浣跨敤涓枃**
- **APK 蹇呴』涓婁紶鍒?Release 闄勪欢**
- **GitHub API 璋冪敤浣跨敤 Node.js**锛圥owerShell Invoke-RestMethod 涓枃缂栫爜鏈夐棶棰橈級
- **浠呭湪鐢ㄦ埛涓诲姩瑕佹眰鍙戠増鏃舵墠鎵ц鍙戠増娴佺▼**

### 绛惧悕閰嶇疆
- Keystore: app/release-keystore.jks
- storePassword: weather123
- keyAlias: weather-app
- keyPassword: weather123

### 鏋勫缓鐜
- JAVA_HOME: C:\Program Files\Android\Android Studio\jbr
- Gradle: C:\Users\phil\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5
- 涓嶈兘鐢?gradlew锛坰andbox 浼氶樆姝?wrapper 鐨勭綉缁滄鏌ワ級锛屽繀椤荤洿鎺ヨ皟鐢?gradle.bat
## [1.8.89] 鈥?2026-05-31

- Fixed widget crash caused by Color.hashCode() instead of toArgb() in gradient rendering

## [1.8.90] 鈥?2026-05-31

- Fixed widget crash: setImageViewBitmap was called on RelativeLayout instead of ImageView

## [1.8.91] 鈥?2026-05-31

- Fixed widget: removed self-drawn rounded corners (let system handle), fixed content clipping

## [1.8.92] 鈥?2026-05-31

- Widget: smart location name, pin icon, temperature closer to city

## [1.8.93] 鈥?2026-05-31

- Widget location icon matches main app Material LocationOn icon

## [1.8.94] 鈥?2026-05-31

- Widget: increased font sizes for temperature and text

## [1.8.95] 鈥?2026-05-31

- Widget: temp 38sp, min/max order fixed, pin icon lowered

## [1.8.96] 鈥?2026-05-31

- Widget: fixed pin/city alignment using LinearLayout row

## [1.8.97] 鈥?2026-06-01

- fix: 城市列表点击其他城市后显示对应天气数据
