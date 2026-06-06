## [1.9.0] - 2026-06-02

- fix: 娣囶喖顦查崺搴＄閸掓銆冮弬鏉款杻閸╁骸绔堕崥搴ㄥ櫢閺傛媽绻橀崗銉︽閸╁骸绔舵稉銏犮亼閻ㄥ嫮鐝甸幀浣规蒋娴犲爼妫舵０?
- 娣囶喖顦?init 閸фぞ绗?ensureCurrentLocationCity 娑斿妫块惃鍕鎼村繐鍟跨粣渚婄礉濞ｈ濮?citiesLoadJob?.join() 缁涘绶熼崚婵嗩潗閸栨牕鐣幋?
## [1.8.98] - 2026-06-01

- fix: 濞撯晛瀹抽弶鈥愁嚠濮ｆ柨瀹虫导妯哄閿涙艾顔旀惔?dp->8dp閿涘本鍧婇崝鐘殿伂閻愮懓娓鹃悙?
- fix: 闁劕鐨弮鍫曨暕閹躲儲绮撮崝銊ユ倵閺勫墽銇氶垾婊呭箛閸︺劉鈧繃璇為崝銊﹀瘹缁€鍝勬珤
- 鐞涖儱鍘朾ackground鐎电厧鍙?

## [1.8.99] - 2026-06-01

- revert: 閸ョ偞绮村〒鈺佸閺夆€愁嚠濮ｆ柨瀹虫导妯哄(8dp+缁旑垳鍋ｉ崷鍡欏仯)
- revert: 閸ョ偞绮撮柅鎰毈閺冨爼顣╅幎銉︾泊閸斻劍瀵氱粈鍝勬珤
- 娣囨繄鏆€閸斻劎鏁惧鎯扮箿閺€鐟板З(Minutely=150, Hourly=300, Daily=450)

# SkyPulse 闂?Changelog & Memory

> Auto-maintained by Codex. Each modification bumps the patch version.


## [1.8.88] - 2026-05-31

- **闁哄倹婢橀·鍐浖瀹€鍕〃閻忓繐绻掔划宥嗙?2x2**: 鐎归潻缂氱粭鍌滄喆閹烘挻鈻旂紒鈧搹鍦勘缂佺姭鍋撳ù锝呯Ф閻ゅ棝鏁?閻庢稒顨愮槐姘舵晬鐏炴垝绠〒姘€鍌濆幀闂傚倹娼欓悿鍕籍閼稿吀鍒婇幖杈捐缁辨繂顔忛敂鎸庢珷閹煎瓨娲熼崕瀛樺緞閳哄倻姣滈柣婊勫閽栧嫰宕仦鐐粯濡?闁哄牃鍋撳ù锝呭娣囶垶鏁嶇仦钘夌濞戞挸锕ㄩ～妤佸緞閳哄倻姣滈柛銉у亾閻栵綁鏁嶅畝鍕赋闁艰鐓″▓銏″緞閳哄倻姣滈柛娆惷€?
- **WorkManager闁煎浜滈悾鐐▕婢跺﹤鐏ュ┑顔碱儏鐎?*: 閻庡湱鍋熼獮鍢媜nfiguration.Provider闁挎稑鐬间簺闂傚嫨鍊濈划顖滄媼椤ョ棴itializationProvider
## [1.8.75] - 2026-05-29

- **闁糕晛楠哥粩鍫曞礆濡ゅ嫨鈧啴宕￠敍鍕暬濡ゅ倹锚鐎瑰磭绮欓崘鑼毎**: 缂佸顭峰▍搴ㄥ级閳ュ弶顐芥繛鎾冲级閻撳鏁嶇仦鐐闁硅鍠楀﹢顓㈠礉閻樼儤绁伴柡鍐ㄥ閺併倝宕￠悩杈╃Т缂佹绠戦敐鐐哄礂閸滃啰绀夐柛妤嬬磿婢ф牗绋夊鍛櫃闁搞儳濮甸弳鐔煎箲椤旂厧顫ｉ弶鐐测偓鐔插亾鐏炵晫娼旂€殿喒鍋?闁衡偓閸撲胶绱?
- **闂傚牊鐟╃划顖炲礆闁垮鐓€**: 閺夆晜绋戦崣鍡涘春鎼达紕顏抽柛鎺擃殙閵嗗啴寮懜鍨闁硅鍠栧﹢顏堝触鎼粹€抽叡闁告梻濮惧ù鍥晬鐏炶偐鐟濈憸鏉垮船閹肩兘宕￠敍鍕暬閻㈩垰鍟惇?

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

### UI 闁?Color System Overhaul

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
Color.kt 闁?WeatherTheme.kt 闁?Theme.kt 闁?GlassCard.kt 闁?MinutelyPrecipitation.kt 闁?WeatherScreen.kt 闁?CurrentWeather.kt 闁?HourlyForecast.kt 闁?DailyForecast.kt 闁?WeatherUtils.kt


## [1.8.88] - 2026-05-31

- **闁哄倹婢橀·鍐浖瀹€鍕〃閻忓繐绻掔划宥嗙?2x2**: 鐎归潻缂氱粭鍌滄喆閹烘挻鈻旂紒鈧搹鍦勘缂佺姭鍋撳ù锝呯Ф閻ゅ棝鏁?閻庢稒顨愮槐姘舵晬鐏炴垝绠〒姘€鍌濆幀闂傚倹娼欓悿鍕籍閼稿吀鍒婇幖杈捐缁辨繂顔忛敂鎸庢珷閹煎瓨娲熼崕瀛樺緞閳哄倻姣滈柣婊勫閽栧嫰宕仦鐐粯濡?闁哄牃鍋撳ù锝呭娣囶垶鏁嶇仦钘夌濞戞挸锕ㄩ～妤佸緞閳哄倻姣滈柛銉у亾閻栵綁鏁嶅畝鍕赋闁艰鐓″▓銏″緞閳哄倻姣滈柛娆惷€?
- **WorkManager闁煎浜滈悾鐐▕婢跺﹤鐏ュ┑顔碱儏鐎?*: 閻庡湱鍋熼獮鍢媜nfiguration.Provider闁挎稑鐬间簺闂傚嫨鍊濈划顖滄媼椤ョ棴itializationProvider
## [1.8.87] 闂?2026-05-31

- fix: 闁硅鍔樼粋鎺撱偊閿濆牓妯嬪ù锝堟硶閺併倝鎯囬悢椋庢澖闁轰胶澧楀畵?

## [1.8.86] 闂?2026-05-31

- feat: 闁硅鍔樼粋鎺戭嚕閸︻厾宕跺褏鍋涙慨鐐哄箥閹惧湱銈诲Δ锔肩秬闂冨潡鏁嶅☉妯荤闁哄秴娲ら崯鈧紓鍌楁櫅閻剚绋夐埀顒勬倷?
## [1.8.85] 闂?2026-05-31

- fix: 闁搞儳鍋撻悥锝夊础閻樺磭妲风€甸偊鍠涢惃?

## [1.8.84] 闂?2026-05-31

- fix: 闁搞儳鍋撻悥锝夊礈瀹ュ棙鐝悘蹇撴惈椤曨厾鎷崘鈺傛

## [1.8.83] 闂?2026-05-31

- fix: 閹煎瓨姊婚弫銈夊炊閻愵剛鍨奸悷浣风婢光偓闂侇偄鍊块崢?

## [1.8.82] 闂?2026-05-31

- feat: 闁哄洤鐡ㄥ畷鍙夋償閺冨倹鏆忛柛銉у亾閻?

## [1.8.81] 闂?2026-05-31

- fix: 濡澘瀚鐔烘嫚閿旇棄鍓板銈呯仛閻栵絾锛愬Ο鍦憿閺夆晜鏌ㄥú鏍炊閻愵剛鍨煎Λ鐗堢矎婢瑰﹪寮ㄩ柅娑滅濞戞挸楠搁崣褎绂嶆惔銊ｂ偓澶嬬▔閳ь剟鎳?
## [1.8.80] 闂?2026-05-31

- fix: 濡澘瀚鐔烘嫚閿旇棄鍓板銈勭祷閸庢寮查娑欐毉濞戞捁妗ㄧ粭宀勫春鎼达紕顏抽柛鎺擃殙閵?闁稿繐鍘栫花顒併亜閸忓摜顏遍柤宄邦嚟濞堟垵菐鏉堫偄顥忔繛鎾村姇瑜?

## [1.8.79] 闂?2026-05-31

- fix: 濠㈣埖纰嶅顖涳紣閸曨噮鍔呴弶鐑嗗枟閹搁亶鎮欓悷鏉挎瘖闁告艾姘﹂娑㈠箚閸涙番鈧鎯勭€涙ê澶嶉悘鐐存礈閵囨岸宕楅妸鈺佸姤濡澘瀚?

## [1.8.78] 闂?2026-05-31

- feat: 濡澘瀚鐔煎箰婢舵劖灏﹂柣鎰嚀閸ゎ喚鎹勭€圭姵绁Λ鏉垮椤掔喓鎷犻敂钘夊壈濡?

## [1.8.75] - 2026-05-29

- **闁糕晛楠哥粩鍫曞礆濡ゅ嫨鈧啴宕￠敍鍕暬濡ゅ倹锚鐎瑰磭绮欓崘鑼毎**: 缂佸顭峰▍搴ㄥ级閳ュ弶顐芥繛鎾冲级閻撳鏁嶇仦鐐闁硅鍠楀﹢顓㈠礉閻樼儤绁伴柡鍐ㄥ閺併倝宕￠悩杈╃Т缂佹绠戦敐鐐哄礂閸滃啰绀夐柛妤嬬磿婢ф牗绋夊鍛櫃闁搞儳濮甸弳鐔煎箲椤旂厧顫ｉ弶鐐测偓鐔插亾鐏炵晫娼旂€殿喒鍋?闁衡偓閸撲胶绱?
- **闂傚牊鐟╃划顖炲礆闁垮鐓€**: 閺夆晜绋戦崣鍡涘春鎼达紕顏抽柛鎺擃殙閵嗗啴寮懜鍨闁硅鍠栧﹢顏堝触鎼粹€抽叡闁告梻濮惧ù鍥晬鐏炶偐鐟濈憸鏉垮船閹肩兘宕￠敍鍕暬閻㈩垰鍟惇?

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
## [1.8.69] 闂?2026-05-29

- Refresh status: location text slides up via offset animation, status fades in below, no page shift

## [1.8.68] 闂?2026-05-29

- Refresh status: replace fixed-height Box with expandVertics animation below location text

## [1.8.67] 闂?2026-05-29

- Fix refresh indicator clipping: increase status row height 12dp->16dp to fit 14dp spinner without layout shift

## [1.8.66] 闂?2026-05-29

- Fix refresh indicator clipping: remove fixed height constraint on status row

## [1.8.65] 闂?2026-05-29

- UI color system overhaul: text hierarchy, alert tokens, precipitation tokens, chart contrast, GlassCard day/night, night mode, interactive states


## [1.8.64] 闂?baseline

- Project handed to Codex. Starting version: 1.8.64 (versionCode 144).


## [1.8.88] - 2026-05-31

- **闁哄倹婢橀·鍐浖瀹€鍕〃閻忓繐绻掔划宥嗙?2x2**: 鐎归潻缂氱粭鍌滄喆閹烘挻鈻旂紒鈧搹鍦勘缂佺姭鍋撳ù锝呯Ф閻ゅ棝鏁?閻庢稒顨愮槐姘舵晬鐏炴垝绠〒姘€鍌濆幀闂傚倹娼欓悿鍕籍閼稿吀鍒婇幖杈捐缁辨繂顔忛敂鎸庢珷閹煎瓨娲熼崕瀛樺緞閳哄倻姣滈柣婊勫閽栧嫰宕仦鐐粯濡?闁哄牃鍋撳ù锝呭娣囶垶鏁嶇仦钘夌濞戞挸锕ㄩ～妤佸緞閳哄倻姣滈柛銉у亾閻栵綁鏁嶅畝鍕赋闁艰鐓″▓銏″緞閳哄倻姣滈柛娆惷€?
- **WorkManager闁煎浜滈悾鐐▕婢跺﹤鐏ュ┑顔碱儏鐎?*: 閻庡湱鍋熼獮鍢媜nfiguration.Provider闁挎稑鐬间簺闂傚嫨鍊濈划顖滄媼椤ョ棴itializationProvider
## [1.8.75] - 2026-05-29

- **闁糕晛楠哥粩鍫曞礆濡ゅ嫨鈧啴宕￠敍鍕暬濡ゅ倹锚鐎瑰磭绮欓崘鑼毎**: 缂佸顭峰▍搴ㄥ级閳ュ弶顐芥繛鎾冲级閻撳鏁嶇仦鐐闁硅鍠楀﹢顓㈠礉閻樼儤绁伴柡鍐ㄥ閺併倝宕￠悩杈╃Т缂佹绠戦敐鐐哄礂閸滃啰绀夐柛妤嬬磿婢ф牗绋夊鍛櫃闁搞儳濮甸弳鐔煎箲椤旂厧顫ｉ弶鐐测偓鐔插亾鐏炵晫娼旂€殿喒鍋?闁衡偓閸撲胶绱?
- **闂傚牊鐟╃划顖炲礆闁垮鐓€**: 閺夆晜绋戦崣鍡涘春鎼达紕顏抽柛鎺擃殙閵嗗啴寮懜鍨闁硅鍠栧﹢顏堝触鎼粹€抽叡闁告梻濮惧ù鍥晬鐏炶偐鐟濈憸鏉垮船閹肩兘宕￠敍鍕暬閻㈩垰鍟惇?

## 濡炪倕婀卞ú鎵媼閺夎法绠撻柨娑樻箲odex 闁煎浜滄慨鈺冪磼鐎涙ê袘闁?
### GitHub 闂佹澘绉堕悿?
- **濞寸姵鎸哥花?*: github.com/qnmlgbd250/weather-none
- **闁告帒妫欓弫?*: main
- **GitHub Token (classic)**: {GITHUB_TOKEN}
- **Release ID**: 闂侇偅淇虹换?API 闁哄被鍎撮?/repos/qnmlgbd250/weather-none/releases 闁兼儳鍢茶ぐ?

### 闁哄啨鍎遍悥鑸垫交椤撴繂鏁╂繛缈犺兌閳诲ジ鏁嶉崼銉у笡閻犱降鍊х槐?
1. 濞ｅ浂鍠楅弫鍏肩閿濆洨鍨?闁?闁煎浜滄慨鈺呮焻閹烘埈鏉婚柣妤€鐗婂﹢浼村矗閸戙倗绀剉ersionCode + versionName闁?2. 闁哄洤鐡ㄩ弻?CHANGELOG.md
3. 闁哄瀚紓?Release APK
4. 闂佹彃绉撮幊锟犲触?APK 濞?`SkyPulse-v{闁绘鐗婂﹢浼村矗缁?apk`
5. 濞戞挸锕ｇ槐?APK 闁告帡顣︾花顖滅博椤栨艾顥呴悹鎰摠濠㈡﹢鏁嶅姝漸rl -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"`
6. Git 闁圭粯鍔掑?+ push 闁?origin/main
7. **濞戞挸绉撮崹鍗烆嚈?GitHub Release**

### 闁告瑦鍨规晶妤€霉娴ｈ　鏌ら柨娑樼墔缁酣鎮介妸锕€鐓曞☉鎾诡嚙婵晝鎲版担鍦勾"闁告瑦鍨规晶?闁哄啳鍩栨晶鐣屾偘瀹€瀣
1. 闁圭瑳鍡╂斀濞戞挸锕ㄩ崼顏堝籍閵夈儳鍩楅弶鈺婂幒閸烆剙霉娴ｈ　鏌ら柣銊ュ婢у秹寮垫径瀣靛妱濡?2. 闁?tag + push tag
3. 闂侇偅淇虹换?GitHub API 闁告帗绋戠紓?Release闁挎稑鐗忛弫?Node.js 闁告瑦鍨奸顒€效閸岋妇绀夌痪顓у枙缁绘碍绋夐鐔哥€?UTF-8 缂傚倹鐗滈悥婊冾潰閿濆洠鈧﹢鏁?4. 闂侇偅淇虹换?GitHub API 濞戞挸锕ｇ槐?APK 闁?Release 闂傚嫬瀚▎?
1. 濞ｅ浂鍠楅弫鍏肩閿濆洨鍨?闁?闁煎浜滄慨鈺呮焻閹烘埈鏉婚柣妤€鐗婂﹢浼村矗閸戙倗绀剉ersionCode + versionName闁?2. 闁哄洤鐡ㄩ弻?CHANGELOG.md
3. 闁哄瀚紓?Release APK闁挎稒顒歳adlew assembleRelease闁挎稑婀濧VA_HOME = C:\Program Files\Android\Android Studio\jbr闁?4. 闂佹彃绉撮幊锟犲触?APK 濞?SkyPulse-v{闁绘鐗婂﹢浼村矗缁?apk
5. 濞戞挸锕ｇ槐?APK 闁告帡顣︾花顖滅博椤栨艾顥呴悹鎰摠濠㈡﹢鏁嶅婕竢l -X POST -F "file=@{apk}" "http://114.132.226.161:5000/api/files?room=2027"
6. Git 闁圭粯鍔掑?+ 闁?tag + push 闁?origin/main
7. 闂侇偅淇虹换?GitHub API 闁告帗绋戠紓?Release闁挎稑鐗忛弫?Node.js 闁告瑦鍨奸顒€效閸岋妇绀夌痪顓у枙缁绘碍绋夐鐔哥€?UTF-8 缂傚倹鐗滈悥婊冾潰閿濆洠鈧﹢鏁?8. 闂侇偅淇虹换?GitHub API 濞戞挸锕ｇ槐?APK 闁?Release 闂傚嫬瀚▎?

### 闁告瑦鍨规晶妤冩啺娴ｅ湱婀?
- **Release 闁硅绻楅崼顏囩疀閸涙番鈧繑鎷呯捄銊︽殢濞戞搩鍘介弸?*
- **APK 闊洤鎳橀妴蹇旂▔婵犱胶鐐婇柛?Release 闂傚嫬瀚▎?*
- **GitHub API 閻犲鍟伴弫銈嗘媴鐠恒劍鏆?Node.js**闁挎稑婀werShell Invoke-RestMethod 濞戞搩鍘介弸鍐磽閺嶎偆鍨抽柡鍫濐樀濡埖锛愬鍫㈢
- **濞寸姴鎳庡﹢顏堟偨閵婏箑鐓曞☉鎾诡嚙婵晝鎲版担鍦勾闁告瑦鍨规晶妤呭籍閼搁潧顤呴柟绗涘棭鏀介柛娆愬灩婢ф霉娴ｈ　鏌?*

### 缂佹稒鍎抽幃鏇㈡煀瀹ュ洨鏋?
- Keystore: app/release-keystore.jks
- storePassword: weather123
- keyAlias: weather-app
- keyPassword: weather123

### 闁哄瀚紓鎾绘偝椤栨凹鏆?
- JAVA_HOME: C:\Program Files\Android\Android Studio\jbr
- Gradle: C:\Users\phil\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5
- 濞戞挸绉烽崗姗€鎮?gradlew闁挎稑婢僡ndbox 濞村吋宀稿Ο鍡楊潰?wrapper 闁汇劌瀚紞澶岀磼濠婂嫷姊鹃柡灞诲劵缁辨岸鏁嶇仦鐣岀畱濡炪倛宕靛ú鍧楀箳閵夈劎娈堕柣?gradle.bat
## [1.8.89] 闁?2026-05-31

- Fixed widget crash caused by Color.hashCode() instead of toArgb() in gradient rendering

## [1.8.90] 闁?2026-05-31

- Fixed widget crash: setImageViewBitmap was called on RelativeLayout instead of ImageView

## [1.8.91] 闁?2026-05-31

- Fixed widget: removed self-drawn rounded corners (let system handle), fixed content clipping

## [1.8.92] 闁?2026-05-31

- Widget: smart location name, pin icon, temperature closer to city

## [1.8.93] 闁?2026-05-31

- Widget location icon matches main app Material LocationOn icon

## [1.8.94] 闁?2026-05-31

- Widget: increased font sizes for temperature and text

## [1.8.95] 闁?2026-05-31

- Widget: temp 38sp, min/max order fixed, pin icon lowered

## [1.8.96] 闁?2026-05-31

- Widget: fixed pin/city alignment using LinearLayout row

## [1.8.97] 闁?2026-06-01

- fix: 閸╁骸绔堕崚妤勩€冮悙鐟板毊閸忔湹绮崺搴＄閸氬孩妯夌粈鍝勵嚠鎼存柨銇夊鏃€鏆熼幑?

## [1.9.1] - 2026-06-02

- 鑷姩鍖栧彂甯冩祴璇?

## [1.9.2] - 2026-06-02

- 鑷姩鍖栧彂甯冩祴璇?

## [1.9.3] - 2026-06-02

- 棰勮璇︽儏椤靛幓鎺夌姸鎬佸拰浣嶇疆淇℃伅锛屼粎鏄剧ず鏍囬鍜屽唴瀹癸紱涓婚〉浣嶇疆浼樺厛鏄剧ず寤虹瓚鐗㏄OI鍚嶇О

## [1.9.4] - 2026-06-02

- 淇棰勮璇︽儏涔辩爜锛涙爣棰樺彧淇濈暀棰勮绫诲瀷锛涗慨澶嶅煄甯傚垏鎹㈠悗棰勮閿欎贡锛涗富椤典綅缃紭鍏堟樉绀哄缓绛戠墿鍚嶇О

## [1.9.5] - 2026-06-02

- 浣嶇疆鏄剧ず浣跨敤AMap SDK poiName鐩存帴鑾峰彇寤虹瓚鐗╁悕绉帮紝瀵归綈v1.8.96閫昏緫

## [1.9.6] - 2026-06-02

- 浣嶇疆鏄剧ず浼樺寲锛氬尯鍜屽競鍚屾椂瀛樺湪鏃跺彧鏄剧ず鍖?

## [1.9.7] - 2026-06-02

- 淇灏忕粍浠朵笉鏄剧ず鏁版嵁锛氭暟鎹簮瀵归綈WeatherCache锛涙棤鏁版嵁鏃舵樉绀洪粯璁よ儗鏅紱棣栨鍒涘缓绔嬪嵆鍒锋柊锛涗慨澶峴hortenLocation涔辩爜

## [1.9.8] - 2026-06-02

- 淇灏忕粍浠跺穿婧冿細鎭㈠WorkManager鍒濆鍖栵紝WorkManager璋冪敤鍔犲紓甯镐繚鎶?

## [1.9.9] - 2026-06-02

- 淇灏忕粍浠舵棤鏁版嵁锛歛pp鍚姩鏃跺悓姝ョ紦瀛樺埌WeatherCache渚涘皬缁勪欢璇诲彇

## [1.9.10] - 2026-06-02

- 灏忕粍浠剁粺涓€浠嶹eatherDataStore璇诲彇鏁版嵁锛岀Щ闄eatherCache渚濊禆

## [1.9.11] - 2026-06-02

- 灏忕粍浠剁紦瀛樿鍙栧拷鐣ヨ繃鏈燂紱MainActivity鏀逛负singleTask淇杩斿洖鎵嬪娍

## [1.9.12] - 2026-06-02

- 灏忕粍浠跺洖褰扴haredPreferences鐩存帴璇诲啓WeatherCache锛屼富app鍙屽啓淇濊瘉鍚屾

## [1.9.14] - 2026-06-02

- 灏忕粍浠秓nUpdate鍚屾璇籛eatherCache锛屼笉渚濊禆Worker

## [1.9.15] - 2026-06-02

- 灏忕粍浠跺鍔犺皟璇曟棩蹇楀畾浣嶆暟鎹鍙栭棶棰?

## [1.9.16] - 2026-06-02

- 鏍规不灏忕粍浠讹細涓籥pp鍩庡競鏁版嵁鍚屾鍐欏叆CityManager(SharedPreferences)渚涘皬缁勪欢璇诲彇

## [1.9.18] - 2026-06-02

- 淇灏忕粍浠讹細搴曢儴娓╁害鍜屽ぉ姘斿浘鏍囨樉绀?

## [1.9.19] - 2026-06-02

- 灏忕粍浠跺彧鏄剧ず褰撳墠瀹氫綅鍩庡競澶╂皵锛孏PS鏇存柊鍚庤嚜鍔ㄥ埛鏂板皬缁勪欢

## [1.9.21] - 2026-06-02

- 鍏充簬椤垫坊鍔犳暟鎹潵婧愯鏄?

## [1.9.22] - 2026-06-02

- 鍏充簬椤垫坊鍔犳暟鎹潵婧?

## [1.9.23] - 2026-06-02

- 娓呯悊鎶€鏈€哄姟锛氱Щ闄ゅ皬缁勪欢璋冭瘯鏃ュ織銆佹竻鐞唋oadCached姝讳唬鐮併€佸皬缁勪欢鍥炬爣鍐呭瓨+纾佺洏缂撳瓨

## [1.9.24] - 2026-06-02

- 璁剧疆椤甸潰閲嶆瀯+澶╂皵閫氱煡鍔熻兘锛氶檷闆?棰勮/鍙樻俯/澶ч/鍙伴鎻愰啋

## [1.9.59] - 2026-06-04

- 澶╂皵璇︽儏鍙傛暟杩佺Щ鑷冲鏃ラ鎶ヤ笅鏂癸紝6鍗＄墖鐭╅樀鎺掑垪锛屾柊澧炴皵鍘嬪拰鑳借搴?

## [1.9.60] - 2026-06-04

- 璇︽儏鍗＄墖鏂瑰舰绛夊ぇ銆佹皵鍘嬪崟浣嶇櫨甯曘€佺煩闃甸棿璺濈粺涓€

## [1.9.61] - 2026-06-04

- 淇姘斿帇鏄剧ず锛歅a杞櫨甯?/100)

## [1.9.62] - 2026-06-04

- 缁熶竴鎵€鏈夊崱鐗囬棿璺濅负8dp

## [1.9.63] - 2026-06-04

- 鍗＄墖鍦嗚22dp鈫?2dp锛屽疄鍐典笌姒傚喌闂磋窛鍔犲鑷?6dp

## [1.9.64] - 2026-06-04

- 鍦嗚12dp鈫?6dp锛屽疄鍐典笌姒傚喌闂磋窛22dp

## [1.9.65] - 2026-06-04

- 灏忕粍浠剁嫭绔嬪埛鏂帮細onUpdate鏃剁珛鍗宠Е鍙慦orker鎷夊彇鏈€鏂版暟鎹?

## [1.9.66] - 2026-06-04

- 璇︽儏鍗＄墖娣诲姞鍥炬爣锛氱传澶栫嚎/浣撴劅/婀垮害/椋庡姏/姘斿帇/鑳借搴?

## [1.9.67] - 2026-06-04

- 璇︽儏鍗＄墖涓夎甯冨眬锛氬浘鏍?24dp)/鏍囩/鏁版嵁灞呬腑鎺掑垪

## [1.9.68] - 2026-06-04

- 璇︽儏鍗＄墖鍐呭鎭㈠宸﹀榻?

## [1.9.69] - 2026-06-04

- 璇︽儏鍗＄墖鏍囩瀛楀彿 labelSmall鈫抣abelMedium

## [1.9.70] - 2026-06-04

- 璇︽儏鍗＄墖鎭㈠宸﹀榻?

## [1.9.71] - 2026-06-04

- 涓婚〉搴曢儴娣诲姞鏁版嵁鏉ユ簮鍜屽畾浣嶆湇鍔¤鏄?

## [1.9.72] - 2026-06-04

- 涓婚〉搴曢儴鏁版嵁鏉ユ簮鏂囧瓧涓婇棿璺?dp鈫?2dp

## [1.9.73] - 2026-06-04

- 涓婚〉搴曢儴鏂囧瓧搴曡窛32dp鈫?2dp

## [1.9.74] - 2026-06-04

- 鍒犻櫎璁剧疆椤靛簳閮ㄥ啑浣欐枃瀛楋紙鏁版嵁鏉ユ簮宸茶縼绉昏嚦涓婚〉锛?

## [1.9.75] - 2026-06-04

- 璁剧疆椤垫仮澶峇Q缇ゅ拰鐗堟湰鍙凤紝浠呭垹闄ゆ暟鎹潵婧?

## [1.9.76] - 2026-06-04

- 璁剧疆椤靛簳閮ㄦ枃瀛楀簳璺?2dp鈫?2dp

## [1.9.77] - 2026-06-04

- 璁剧疆椤靛姞navigationBarsPadding锛屽垹闄ゅ浣欏簳閮⊿pacer

## [1.9.78] - 2026-06-04

- 瀹氫綅鏀逛负浼樺厛鏄剧ず鏈€杩戠壒寰佸湴鐐癸紝涓嶅啀鍖归厤澶у帵/骞垮満鍏抽敭璇?

## [1.9.80] - 2026-06-05

- 淇瀹氫綅鍚嶇О璺冲彉锛氬悓鍦扮偣200绫冲唴澶嶇敤缂撳瓨鍚嶇О

## [1.9.81] - 2026-06-05

- 褰诲簳娓呴櫎閫嗗湴鐞嗙紪鐮佷腑鐨勫ぇ鍘?骞垮満鍏抽敭璇嶄紭鍏堝尮閰?

## [1.9.82] - 2026-06-05

- 灏忕粍浠剁嫭绔嬪畾浣嶅埛鏂帮細Worker鍚庡彴鏇存柊褰撳墠浣嶇疆鍧愭爣鍜屽悕绉?

## [1.9.83] - 2026-06-05

- 涓婚〉鍙充笂瑙掓寜閽敼涓烘煍鍏夊鎸夐挳锛屽煄甯傚垪琛ㄥ拰璁剧疆鍥炬爣绮捐嚧鍖?

## [1.9.84] - 2026-06-05

- 涓婚〉鍙充笂瑙掓寜閽幓闄ら伄缃╋紝淇濈暀瀹借Е鎺у尯鍩?

## [1.9.85] - 2026-06-05

- 鍘绘帀鍙充笂瑙掓寜閽伄缃╋紝鎸夐挳闂磋窛璋冨皬

## [1.9.86] - 2026-06-05

- 淇鏂囦欢缂栫爜涔辩爜闂

## [1.9.87] - 2026-06-05

- 鎵撹祻鍚嶅崟澧炲姞 *椋?楼3

## [1.9.88] - 2026-06-05

- 淇鎵撹祻鍚嶅崟*椋庢樉绀轰贡鐮?

## [1.9.95] - 2026-06-06

- fix: 通知内容优化 - 短临降水/变温/大风/极端天气提醒聚焦事件详情，统一项目文件编码为UTF-8

## [1.9.96] - 2026-06-06

- fix: 定位名称缓存优化 - 持久化缓存+扩大500米防抖半径，解决同一位置POI名称跳变问题

## [1.9.97] - 2026-06-06

- feat: 主页右上角图标升级 - 毛玻璃圆形底座+更换为列表/齿轮图标，适配整体UI风格

## [1.9.98] - 2026-06-06

- fix: 主页图标修正 - 城市列表改为PlaylistAdd(横线+加号)、设置改为MoreHoriz(宽距省略号)，去掉毛玻璃底座回归纯图标

## [1.9.99] - 2026-06-06

- fix: 还原主页右上角图标为原始Menu/MoreVert，预警横幅位置上移4dp

## [1.9.100] - 2026-06-06

- feat: 通知点击跳转主页 - 所有天气通知点击后自动打开APP主页

## [1.9.101] - 2026-06-06

- feat: 底部详情卡片优化 - 紫外线/气压/能见度数字+单位分层显示，风力卡片标签改为风向+等级，能见度单位改为千米

## [1.9.102] - 2026-06-06

- fix: 详情卡片字号统一+修复气压能见度数值缺失 - 数值字号与体感温度一致，单位字号13sp，修复模板表达式丢失

## [1.9.103] - 2026-06-06

- fix: 详情卡片单位字号调整为12sp，单位底部对齐数值底部

## [1.9.104] - 2026-06-06

- fix: 详情卡片单位底部基线对齐 - 修复alignByBaseline与Alignment.Bottom冲突

## [1.9.105] - 2026-06-06

- fix: 详情卡片单位底部对齐 - 双方alignByBaseline确保数值与单位基线对齐
