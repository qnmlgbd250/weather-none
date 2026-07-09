package com.skypulse.weather.ui.theme

import androidx.compose.ui.graphics.Color

// ============ Primary Palette ============
val SkyBlue = Color(0xFF4FC3F7)
val WarmGold = Color(0xFFFFD54F)

// ============ Surface Colors ============
val CardSurface = Color(0x33FFFFFF)
val CardSurfaceLight = Color(0x1AFFFFFF)
val CardSurfaceNight = Color(0x1AFFFFFF)
val CardSurfaceNightLight = Color(0x0DFFFFFF)

// ============ Text Hierarchy ============
// 100% white 鈥?headlines, temperature, key data
val TextPrimary = Color(0xFFFFFFFF)
// 85% white 鈥?weather description, labels
val TextSecondary = Color(0xD9FFFFFF)
// 60% white 鈥?tertiary labels, footnotes, timestamps
val TextTertiary = Color(0x99FFFFFF)
// 40% white 鈥?disabled / placeholder text
val TextDisabled = Color(0x66FFFFFF)

// ============ Alert Semantic Colors ============
val AlertRed = Color(0xFFC05050)
val AlertOrange = Color(0xFFFFB74D)
val AlertYellow = Color(0xFFFFD54F)   // same hue as WarmGold
val AlertBlue = Color(0xFF4488FF)

// ============ Precipitation Bar Colors ============
val PrecipBarTop = Color(0xFFB0EAFF)
val PrecipBarBottom = Color(0xFF92DDFE)
val PrecipBarShadow = Color(0xFF92DDFE)

// ============ Interactive State Colors ============
val PressedOverlay = Color(0x14FFFFFF)   // ~8% white overlay on press
val DisabledOverlay = Color(0x1A000000)  // ~10% black overlay on disabled
val CardBorderDay = Color(0x66FFFFFF)    // 40% white 鈥?visible in bright sun
val CardBorderNight = Color(0x22FFFFFF)  // 13% white 鈥?subtle at night

// ============ iOS Weather-style Background Gradients ============
// Each gradient uses 5 stops for smooth, natural sky transitions

// Sunny (Day) ── clear sky blue, with a deeper top for readable white text
val SunnyGradient = listOf(
    Color(0xFF1F5F9C),  // 顶部天顶深蓝
    Color(0xFF2A75B3),
    Color(0xFF3788C8),
    Color(0xFF4B9DDD),
    Color(0xFF65B3EA)   // 底部地平线浅亮蓝
)

// Sunny (Night) ── deep navy to midnight blue
val SunnyNightGradient = listOf(
    Color(0xFF0B1929),
    Color(0xFF122640),
    Color(0xFF1A3355),
    Color(0xFF1E3D65),
    Color(0xFF234A72)
)

// Partly Cloudy (Day) ── medium blue, slightly desaturated
val PartialCloudGradient = listOf(
    Color(0xFF2F6692),
    Color(0xFF3F7EAA),
    Color(0xFF5C98C0),
    Color(0xFF78ABC9),
    Color(0xFF9FC0D6)
)

// Partly Cloudy (Night) ── indigo to blue-violet
val PartialCloudNightGradient = listOf(
    Color(0xFF141E3A),
    Color(0xFF1C2B50),
    Color(0xFF253965),
    Color(0xFF2E4778),
    Color(0xFF375590)
)

// Cloudy (Day) ── rich slate indigo-gray overcast sky
val CloudyGradient = listOf(
    Color(0xFF4B5A66),  // 深邃灰蓝
    Color(0xFF576875),
    Color(0xFF647685),
    Color(0xFF738694),
    Color(0xFF8195A3)   // 底部淡雅灰蓝
)

// Cloudy (Night) ── dark blue-gray
val CloudyNightGradient = listOf(
    Color(0xFF1C2834),
    Color(0xFF283848),
    Color(0xFF35485C),
    Color(0xFF3F5468),
    Color(0xFF4A6075)
)

// Rainy (Day) ── deep blue-gray, darkest and heaviest
val RainyGradient = listOf(
    Color(0xFF3E4F5E),  // 压抑沉重的深雨云色
    Color(0xFF495B6C),
    Color(0xFF55697B),
    Color(0xFF62778A),
    Color(0xFF708799)
)

// Rainy (Night) ── deep blue-black
val RainyNightGradient = listOf(
    Color(0xFF0F1A24),
    Color(0xFF182636),
    Color(0xFF213348),
    Color(0xFF2A3F55),
    Color(0xFF324A60)
)

// Snowy (Day) ── cold gray-blue
val SnowyGradient = listOf(
    Color(0xFF6E8A9E),
    Color(0xFF8BA5B5),
    Color(0xFF96AEB8),
    Color(0xFF9DB5BE),
    Color(0xFFA5BBC4)
)

// Snowy (Night) ── muted blue-gray
val SnowyNightGradient = listOf(
    Color(0xFF1E2D38),
    Color(0xFF2A3D4D),
    Color(0xFF364D60),
    Color(0xFF425D72),
    Color(0xFF4E6D82)
)

// Haze/Fog (Day) ── warm brown-gray, atmospheric
val HazeGradient = listOf(
    Color(0xFF7A6B5E),
    Color(0xFF948578),
    Color(0xFF9A8B7E),
    Color(0xFFA29388),
    Color(0xFFAA9B90)
)

// Haze/Fog (Night) ── dark purple-gray twilight haze
val HazeNightGradient = listOf(
    Color(0xFF161518),
    Color(0xFF1F1D23),
    Color(0xFF2B2830),
    Color(0xFF38343D),
    Color(0xFF46414C)
)

// Windy (Day) ── teal-blue, fresh
val WindyGradient = listOf(
    Color(0xFF2A7B72),
    Color(0xFF3A968C),
    Color(0xFF45A098),
    Color(0xFF4DA8A0),
    Color(0xFF55B0A8)
)

// Windy (Night) ── dark teal night sky
val WindyNightGradient = listOf(
    Color(0xFF0F252C),
    Color(0xFF142F38),
    Color(0xFF1B3D49),
    Color(0xFF224B5A),
    Color(0xFF295A6C)
)

// Night Fallback 鈥?deep night sky
val NightFallbackGradient = SunnyNightGradient


// ============ iOS Light Settings Style ============
val IosSettingsBg = Color(0xFFF2F2F7)
val IosCardBg = Color(0xFFFFFFFF)
val IosCardRadius = 14
val IosDividerColor = Color(0xFFE5E5EA)
val IosTextPrimary = Color(0xFF1C1C1E)
val IosTextSecondary = Color(0xFF8E8E93)
val IosBackArrow = Color(0xFF333333)
val IosAccentBlue = Color(0xFF007AFF)
val IosSwitchOff = Color(0xFFE5E5EA)
val IosToggleTrackOn = Color(0xFF007AFF)

// Secondary screens 鈥?deep indigo-violet palette.
// Rich purple-blue base for an immersive, modern night atmosphere.
val SecondaryScreenGradient = listOf(
    Color(0xFF141332),
    Color(0xFF191742),
    Color(0xFF1E1B4B)
)
// Frost glass panels 鈥?layered like main-page GlassCard
val SecondaryPanel = Color(0x26FFFFFF)          // ~15% white frost
val SecondaryPanelStrong = Color(0x3DFFFFFF)     // ~24% white frost
val SecondaryPanelBorder = Color(0x33FFFFFF)     // 20% white border
val SecondaryTextPrimary = Color(0xF0FFFFFF)     // near-white for dark bg
val SecondaryTextSecondary = Color(0xB3FFFFFF)   // 70% white
val SecondaryAccent = Color(0xFFA78BFA)          // violet accent
val SecondaryAlert = Color(0xFFE0A050)
val DialogPanel = Color(0xFF1C1945)              // opaque deep indigo
val DialogInnerPanel = Color(0xFF252262)         // slightly lighter indigo
val DialogPanelBorder = Color(0x33FFFFFF)        // 20% white border
val DialogTextPrimary = Color(0xF0FFFFFF)       // near-white for dark dialog
val DialogTextSecondary = Color(0xB3FFFFFF)     // 70% white

// ============ Accent Colors ============
val PrecipitationBlue = Color(0xFF29B6F6)
val HumidityBlue = Color(0xFF42A5F5)

