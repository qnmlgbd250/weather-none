## [1.9.0] - 2026-06-02

- fix: 淇鍩庡競鍒楄〃鏂板鍩庡競鍚庨噸鏂拌繘鍏ユ椂鍩庡競涓㈠け鐨勭珵鎬佹潯浠堕棶棰?
- 淇 init 鍧椾笌 ensureCurrentLocationCity 涔嬮棿鐨勬椂搴忓啿绐侊紝娣诲姞 citiesLoadJob?.join() 绛夊緟鍒濆鍖栧畬鎴?
## [1.8.98] - 2026-06-01

- fix: 娓╁害鏉″姣斿害浼樺寲锛氬搴?dp->8dp锛屾坊鍔犵鐐瑰渾鐐?
- fix: 閫愬皬鏃堕鎶ユ粴鍔ㄥ悗鏄剧ず鈥滅幇鍦ㄢ€濇诞鍔ㄦ寚绀哄櫒
- 琛ュ厖background瀵煎叆

## [1.8.99] - 2026-06-01

- revert: 鍥炴粴娓╁害鏉″姣斿害浼樺寲(8dp+绔偣鍦嗙偣)
- revert: 鍥炴粴閫愬皬鏃堕鎶ユ粴鍔ㄦ寚绀哄櫒
- 淇濈暀鍔ㄧ敾寤惰繜鏀瑰姩(Minutely=150, Hourly=300, Daily=450)

# SkyPulse 闂?Changelog & Memory

> Auto-maintained by Codex. Each modification bumps the patch version.


## [1.8.88] - 2026-05-31

- **閺傛澘顤冨宀勬桨鐏忓繒绮嶆禒?2x2**: 瀹革缚绗傜憴鎺撴▔缁€铏圭翱缁犫偓娴ｅ秶鐤嗛敍?鐎涙绱氶敍灞戒箯娓氀傝厬闂傛潙鐤勯弮鑸典刊鎼达讣绱濆锔挎櫠鎼存洟鍎存径鈺傜毜閻滄媽钖勯崪灞炬付妤?閺堚偓娴ｅ孩淇敍灞藉礁娑撳﹨顫楁径鈺傜毜閸ョ偓鐖ｉ敍宀勫帳閼规煡娈㈡径鈺傜毜閸欐ê瀵?
- **WorkManager閼奉亜鐣炬稊澶婂灥婵瀵?*: 鐎圭偟骞嘋onfiguration.Provider閿涘瞼些闂勩倝绮拋顥痭itializationProvider
## [1.8.75] - 2026-05-29

- **閸╁骸绔堕崚妤勩€冮崡锛勫妤傛ê瀹崇粙鍐茬暰**: 缁夊娅庨弶鈥叉濞撳弶鐓嬮敍灞炬殶閹诡喗婀崝鐘烘祰閺冨墎鏁ら崡鐘辩秴缁楋箑锝為崗鍜冪礉閸楋紕澧栨稉宥呭晙閸ョ姵鏆熼幑顔煎鏉炲€熲偓灞界潔瀵偓/閺€鍓佺級
- **闂堟瑩绮崚閿嬫煀**: 鏉╂稑鍙嗛崺搴＄閸掓銆冮弮鑸垫殶閹诡喖婀崥搴″酱閸旂姾娴囬敍灞肩瑝瑜板崬鎼烽崡锛勫鐢啫鐪?

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

## [1.8.65] 闂?2026-05-29

### UI 閻?Color System Overhaul

- **Text hierarchy**: Added TextTertiary (60% white) and TextDisabled (40% white) for clearer visual hierarchy
- **Alert tokens**: Extracted AlertRed / AlertOrange / AlertYellow / AlertBlue from inline hardcodes to Color.kt
- **Precipitation tokens**: Added PrecipBarTop / PrecipBarBottom / PrecipBarShadow replacing hardcoded values
- **Chart contrast**: HourlyForecast chart labels now use TextSecondary instead of hardcoded alpha
- **GlassCard day/night**: Dynamic border opacity 闂?CardBorderDay (40%) vs CardBorderNight (13%)
- **Night mode**: WeatherTheme now carries cardBorderColor, 	extTertiary, pressedOverlay, disabledOverlay
- **Interactive states**: Added PressedOverlay (8% white) and DisabledOverlay (10% black) tokens
- **DailyForecast**: Date labels and weather descriptions use TextTertiary for depth

**Build workflow**: APK renamed to versioned filename + auto-upload to cloud clipboard

**Files changed** (9):
Color.kt 閻?WeatherTheme.kt 閻?Theme.kt 閻?GlassCard.kt 閻?MinutelyPrecipitation.kt 閻?WeatherScreen.kt 閻?CurrentWeather.kt 閻?HourlyForecast.kt 閻?DailyForecast.kt 閻?WeatherUtils.kt


## [1.8.88] - 2026-05-31

- **閺傛澘顤冨宀勬桨鐏忓繒绮嶆禒?2x2**: 瀹革缚绗傜憴鎺撴▔缁€铏圭翱缁犫偓娴ｅ秶鐤嗛敍?鐎涙绱氶敍灞戒箯娓氀傝厬闂傛潙鐤勯弮鑸典刊鎼达讣绱濆锔挎櫠鎼存洟鍎存径鈺傜毜閻滄媽钖勯崪灞炬付妤?閺堚偓娴ｅ孩淇敍灞藉礁娑撳﹨顫楁径鈺傜毜閸ョ偓鐖ｉ敍宀勫帳閼规煡娈㈡径鈺傜毜閸欐ê瀵?
- **WorkManager閼奉亜鐣炬稊澶婂灥婵瀵?*: 鐎圭偟骞嘋onfiguration.Provider閿涘瞼些闂勩倝绮拋顥痭itializationProvider
## [1.8.87] 闁?2026-05-31

- fix: 閹规劘绂掓ウ锝堥樋娴ｈ法鏁ら惇鐔风杽閺佺増宓?

## [1.8.86] 闁?2026-05-31

- feat: 閹规劘绂掑鍦崶婢х偛濮為幍鎾圭セ妤︼綀闃块敍娑樻禈閺嶅洤鍟€缂傗晛鐨稉鈧悙?
## [1.8.85] 闁?2026-05-31

- fix: 閸ョ偓鐖ｉ崡鐘崇槷瀵邦喛鐨?

## [1.8.84] 闁?2026-05-31

- fix: 閸ョ偓鐖ｉ崜宥嗘珯鐏忓搫顕拫鍐╂殻

## [1.8.83] 闁?2026-05-31

- fix: 鎼存梻鏁ら崶鐐垼鐟佷礁澹€闁倿鍘?

## [1.8.82] 闁?2026-05-31

- feat: 閺囧瓨宕叉惔鏃傛暏閸ョ偓鐖?

## [1.8.81] 闁?2026-05-31

- fix: 妫板嫯顒熺拠锔藉剰妞ゅ灚鐖ｆ０妯圭瑢鏉╂柨娲栭崶鐐垼妫版粏澹婇弨閫涜礋娑撳骸鍙ф禍搴ㄣ€夋稉鈧懛?
## [1.8.80] 闁?2026-05-31

- fix: 妫板嫯顒熺拠锔藉剰妞や絻鍎楅弲顖涙暭娑撹桨绗岄崺搴＄閸掓銆?閸忓厖绨い鍏哥閼峰娈戝ǎ杈濞撴劕褰?

## [1.8.79] 闁?2026-05-31

- fix: 婢舵碍娼０鍕劅鏉烆喗鎸遍悙鐟板毊閸氬氦顕涢幆鍛淬€夐惄瀛樺复鐏炴洜銇氶崗銊╁劥妫板嫯顒?

## [1.8.78] 闁?2026-05-31

- feat: 妫板嫯顒熼幐澶愭尦閻愮懓鍤捄瀹犳祮妫板嫯顒熺拠锔藉剰妞?

## [1.8.75] - 2026-05-29

- **閸╁骸绔堕崚妤勩€冮崡锛勫妤傛ê瀹崇粙鍐茬暰**: 缁夊娅庨弶鈥叉濞撳弶鐓嬮敍灞炬殶閹诡喗婀崝鐘烘祰閺冨墎鏁ら崡鐘辩秴缁楋箑锝為崗鍜冪礉閸楋紕澧栨稉宥呭晙閸ョ姵鏆熼幑顔煎鏉炲€熲偓灞界潔瀵偓/閺€鍓佺級
- **闂堟瑩绮崚閿嬫煀**: 鏉╂稑鍙嗛崺搴＄閸掓銆冮弮鑸垫殶閹诡喖婀崥搴″酱閸旂姾娴囬敍灞肩瑝瑜板崬鎼烽崡锛勫鐢啫鐪?

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
## [1.8.69] 闁?2026-05-29

- Refresh status: location text slides up via offset animation, status fades in below, no page shift

## [1.8.68] 闁?2026-05-29

- Refresh status: replace fixed-height Box with expandVertics animation below location text

## [1.8.67] 闁?2026-05-29

- Fix refresh indicator clipping: increase status row height 12dp->16dp to fit 14dp spinner without layout shift

## [1.8.66] 闁?2026-05-29

- Fix refresh indicator clipping: remove fixed height constraint on status row

## [1.8.65] 闂?2026-05-29

- UI color system overhaul: text hierarchy, alert tokens, precipitation tokens, chart contrast, GlassCard day/night, night mode, interactive states


## [1.8.64] 闂?baseline

- Project handed to Codex. Starting version: 1.8.64 (versionCode 144).


## [1.8.88] - 2026-05-31

- **閺傛澘顤冨宀勬桨鐏忓繒绮嶆禒?2x2**: 瀹革缚绗傜憴鎺撴▔缁€铏圭翱缁犫偓娴ｅ秶鐤嗛敍?鐎涙绱氶敍灞戒箯娓氀傝厬闂傛潙鐤勯弮鑸典刊鎼达讣绱濆锔挎櫠鎼存洟鍎存径鈺傜毜閻滄媽钖勯崪灞炬付妤?閺堚偓娴ｅ孩淇敍灞藉礁娑撳﹨顫楁径鈺傜毜閸ョ偓鐖ｉ敍宀勫帳閼规煡娈㈡径鈺傜毜閸欐ê瀵?
- **WorkManager閼奉亜鐣炬稊澶婂灥婵瀵?*: 鐎圭偟骞嘋onfiguration.Provider閿涘瞼些闂勩倝绮拋顥痭itializationProvider
## [1.8.75] - 2026-05-29

- **閸╁骸绔堕崚妤勩€冮崡锛勫妤傛ê瀹崇粙鍐茬暰**: 缁夊娅庨弶鈥叉濞撳弶鐓嬮敍灞炬殶閹诡喗婀崝鐘烘祰閺冨墎鏁ら崡鐘辩秴缁楋箑锝為崗鍜冪礉閸楋紕澧栨稉宥呭晙閸ョ姵鏆熼幑顔煎鏉炲€熲偓灞界潔瀵偓/閺€鍓佺級
- **闂堟瑩绮崚閿嬫煀**: 鏉╂稑鍙嗛崺搴＄閸掓銆冮弮鑸垫殶閹诡喖婀崥搴″酱閸旂姾娴囬敍灞肩瑝瑜板崬鎼烽崡锛勫鐢啫鐪?

## 妞ゅ湱娲扮拋鏉跨箓閿涘湑odex 閼奉亜濮╃紒瀛樺Б閿?
### GitHub 闁板秶鐤?
- **娴犳挸绨?*: github.com/qnmlgbd250/weather-none
- **閸掑棙鏁?*: main
- **GitHub Token (classic)**: {GITHUB_TOKEN}
- **Release ID**: 闁俺绻?API 閺屻儴顕?/repos/qnmlgbd250/weather-none/releases 閼惧嘲褰?

### 閺冦儱鐖舵潻顓濆敩濞翠胶鈻奸敍鍫ョ帛鐠併倧绱?
1. 娣囶喗鏁兼禒锝囩垳 閳?閼奉亜濮╅柅鎺戭杻閻楀牊婀伴崣鍑ょ礄versionCode + versionName閿?2. 閺囧瓨鏌?CHANGELOG.md
3. 閺嬪嫬缂?Release APK
4. 闁插秴鎳￠崥?APK 娑?`SkyPulse-v{閻楀牊婀伴崣绌?apk`
5. 娑撳﹣绱?APK 閸掗绨粩顖氬鐠愬瓨婢橀敍姝歝url -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"`
6. Git 閹绘劒姘?+ push 閸?origin/main
7. **娑撳秴鍨卞?GitHub Release**

### 閸欐垹澧楀ù浣衡柤閿涘牅绮庨悽銊﹀煕娑撹濮╃憰浣圭湴"閸欐垹澧?閺冭埖澧界悰宀嬬礆
1. 閹笛嗩攽娑撳﹨鍫弮銉ョ埗鏉╊厺鍞ù浣衡柤閻ㄥ嫭澧嶉張澶嬵劄妤?2. 閹?tag + push tag
3. 闁俺绻?GitHub API 閸掓稑缂?Release閿涘牏鏁?Node.js 閸欐垼顕Ч鍌︾礉绾喕绻氭稉顓熸瀮 UTF-8 缂傛牜鐖滃锝団€橀敍?4. 闁俺绻?GitHub API 娑撳﹣绱?APK 閸?Release 闂勫嫪娆?
1. 娣囶喗鏁兼禒锝囩垳 閳?閼奉亜濮╅柅鎺戭杻閻楀牊婀伴崣鍑ょ礄versionCode + versionName閿?2. 閺囧瓨鏌?CHANGELOG.md
3. 閺嬪嫬缂?Release APK閿涙radlew assembleRelease閿涘湞AVA_HOME = C:\Program Files\Android\Android Studio\jbr閿?4. 闁插秴鎳￠崥?APK 娑?SkyPulse-v{閻楀牊婀伴崣绌?apk
5. 娑撳﹣绱?APK 閸掗绨粩顖氬鐠愬瓨婢橀敍姝漸rl -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"
6. Git 閹绘劒姘?+ 閹?tag + push 閸?origin/main
7. 闁俺绻?GitHub API 閸掓稑缂?Release閿涘牏鏁?Node.js 閸欐垼顕Ч鍌︾礉绾喕绻氭稉顓熸瀮 UTF-8 缂傛牜鐖滃锝団€橀敍?8. 闁俺绻?GitHub API 娑撳﹣绱?APK 閸?Release 闂勫嫪娆?

### 閸欐垹澧楃憰浣圭湴
- **Release 閹诲繗鍫箛鍛淬€忔担璺ㄦ暏娑擃厽鏋?*
- **APK 韫囧懘銆忔稉濠佺炊閸?Release 闂勫嫪娆?*
- **GitHub API 鐠嬪啰鏁ゆ担璺ㄦ暏 Node.js**閿涘湧owerShell Invoke-RestMethod 娑擃厽鏋冪紓鏍垳閺堝妫舵０姗堢礆
- **娴犲懎婀悽銊﹀煕娑撹濮╃憰浣圭湴閸欐垹澧楅弮鑸靛閹笛嗩攽閸欐垹澧楀ù浣衡柤**

### 缁涙儳鎮曢柊宥囩枂
- Keystore: app/release-keystore.jks
- storePassword: weather123
- keyAlias: weather-app
- keyPassword: weather123

### 閺嬪嫬缂撻悳顖氼暔
- JAVA_HOME: C:\Program Files\Android\Android Studio\jbr
- Gradle: C:\Users\phil\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5
- 娑撳秷鍏橀悽?gradlew閿涘澃andbox 娴兼岸妯嗗?wrapper 閻ㄥ嫮缍夌紒婊勵梾閺屻儻绱氶敍灞界箑妞よ崵娲块幒銉ㄧ殶閻?gradle.bat
## [1.8.89] 閳?2026-05-31

- Fixed widget crash caused by Color.hashCode() instead of toArgb() in gradient rendering

## [1.8.90] 閳?2026-05-31

- Fixed widget crash: setImageViewBitmap was called on RelativeLayout instead of ImageView

## [1.8.91] 閳?2026-05-31

- Fixed widget: removed self-drawn rounded corners (let system handle), fixed content clipping

## [1.8.92] 閳?2026-05-31

- Widget: smart location name, pin icon, temperature closer to city

## [1.8.93] 閳?2026-05-31

- Widget location icon matches main app Material LocationOn icon

## [1.8.94] 閳?2026-05-31

- Widget: increased font sizes for temperature and text

## [1.8.95] 閳?2026-05-31

- Widget: temp 38sp, min/max order fixed, pin icon lowered

## [1.8.96] 閳?2026-05-31

- Widget: fixed pin/city alignment using LinearLayout row

## [1.8.97] 閳?2026-06-01

- fix: 鍩庡競鍒楄〃鐐瑰嚮鍏朵粬鍩庡競鍚庢樉绀哄搴斿ぉ姘旀暟鎹?

## [1.9.1] - 2026-06-02

- 自动化发布测试

## [1.9.2] - 2026-06-02

- 自动化发布测试

## [1.9.3] - 2026-06-02

- 预警详情页去掉状态和位置信息，仅显示标题和内容；主页位置优先显示建筑物POI名称

## [1.9.4] - 2026-06-02

- 修复预警详情乱码；标题只保留预警类型；修复城市切换后预警错乱；主页位置优先显示建筑物名称

## [1.9.5] - 2026-06-02

- 位置显示使用AMap SDK poiName直接获取建筑物名称，对齐v1.8.96逻辑

## [1.9.6] - 2026-06-02

- 位置显示优化：区和市同时存在时只显示区

## [1.9.7] - 2026-06-02

- 修复小组件不显示数据：数据源对齐WeatherCache；无数据时显示默认背景；首次创建立即刷新；修复shortenLocation乱码

## [1.9.8] - 2026-06-02

- 修复小组件崩溃：恢复WorkManager初始化，WorkManager调用加异常保护

## [1.9.9] - 2026-06-02

- 修复小组件无数据：app启动时同步缓存到WeatherCache供小组件读取

## [1.9.10] - 2026-06-02

- 小组件统一从WeatherDataStore读取数据，移除WeatherCache依赖

## [1.9.11] - 2026-06-02

- 小组件缓存读取忽略过期；MainActivity改为singleTask修复返回手势

## [1.9.12] - 2026-06-02

- 小组件回归SharedPreferences直接读写WeatherCache，主app双写保证同步

## [1.9.14] - 2026-06-02

- 小组件onUpdate同步读WeatherCache，不依赖Worker

## [1.9.15] - 2026-06-02

- 小组件增加调试日志定位数据读取问题

## [1.9.16] - 2026-06-02

- 根治小组件：主app城市数据同步写入CityManager(SharedPreferences)供小组件读取

## [1.9.18] - 2026-06-02

- 修复小组件：底部温度和天气图标显示

## [1.9.19] - 2026-06-02

- 小组件只显示当前定位城市天气，GPS更新后自动刷新小组件

## [1.9.21] - 2026-06-02

- 关于页添加数据来源说明

## [1.9.22] - 2026-06-02

- 关于页添加数据来源

## [1.9.23] - 2026-06-02

- 清理技术债务：移除小组件调试日志、清理loadCached死代码、小组件图标内存+磁盘缓存

## [1.9.24] - 2026-06-02

- 设置页面重构+天气通知功能：降雨/预警/变温/大风/台风提醒
