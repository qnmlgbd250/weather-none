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
// 100% white — headlines, temperature, key data
val TextPrimary = Color(0xFFFFFFFF)
// 85% white — weather description, labels
val TextSecondary = Color(0xD9FFFFFF)
// 60% white — tertiary labels, footnotes, timestamps
val TextTertiary = Color(0x99FFFFFF)
// 40% white — disabled / placeholder text
val TextDisabled = Color(0x66FFFFFF)

// ============ Alert Semantic Colors ============
val AlertRed = Color(0xFFFF4444)
val AlertOrange = Color(0xFFFF8C00)
val AlertYellow = Color(0xFFFFD54F)   // same hue as WarmGold
val AlertBlue = Color(0xFF4488FF)

// ============ Precipitation Bar Colors ============
val PrecipBarTop = Color(0xFFB0EAFF)
val PrecipBarBottom = Color(0xFF92DDFE)
val PrecipBarShadow = Color(0xFF92DDFE)

// ============ Interactive State Colors ============
val PressedOverlay = Color(0x14FFFFFF)   // ~8% white overlay on press
val DisabledOverlay = Color(0x1A000000)  // ~10% black overlay on disabled
val CardBorderDay = Color(0x66FFFFFF)    // 40% white — visible in bright sun
val CardBorderNight = Color(0x22FFFFFF)  // 13% white — subtle at night

// ============ iOS Weather-style Background Gradients ============
// Each gradient uses 5 stops for smooth, natural sky transitions

// Sunny (Day) — deep cobalt to medium sky blue
val SunnyGradient = listOf(
    Color(0xFF1565A0),
    Color(0xFF2980C0),
    Color(0xFF4A9BD5),
    Color(0xFF5AADD8),
    Color(0xFF6DB5E0)
)

// Sunny (Night) — deep navy to midnight blue
val SunnyNightGradient = listOf(
    Color(0xFF0B1929),
    Color(0xFF122640),
    Color(0xFF1A3355),
    Color(0xFF1E3D65),
    Color(0xFF234A72)
)

// Partly Cloudy (Day) — medium blue to medium sky
val PartialCloudGradient = listOf(
    Color(0xFF3578A8),
    Color(0xFF4E92C0),
    Color(0xFF6AABD5),
    Color(0xFF6DA8C8),
    Color(0xFF7AADD0)
)

// Partly Cloudy (Night) — indigo to blue-violet
val PartialCloudNightGradient = listOf(
    Color(0xFF141E3A),
    Color(0xFF1C2B50),
    Color(0xFF253965),
    Color(0xFF2E4778),
    Color(0xFF375590)
)

// Cloudy (Day) — blue-gray atmospheric
val CloudyGradient = listOf(
    Color(0xFF5E7D8E),
    Color(0xFF7A99AA),
    Color(0xFF8AA5B5),
    Color(0xFF94ADBA),
    Color(0xFF9CB3BE)
)

// Cloudy (Night) — dark blue-gray
val CloudyNightGradient = listOf(
    Color(0xFF1C2834),
    Color(0xFF283848),
    Color(0xFF35485C),
    Color(0xFF3F5468),
    Color(0xFF4A6075)
)

// Rainy (Day) — moody blue-gray
val RainyGradient = listOf(
    Color(0xFF44596B),
    Color(0xFF566E82),
    Color(0xFF607888),
    Color(0xFF6A8290),
    Color(0xFF748C98)
)

// Rainy (Night) — deep blue-black
val RainyNightGradient = listOf(
    Color(0xFF0F1A24),
    Color(0xFF182636),
    Color(0xFF213348),
    Color(0xFF2A3F55),
    Color(0xFF324A60)
)

// Snowy (Day) — cold gray-blue
val SnowyGradient = listOf(
    Color(0xFF6E8A9E),
    Color(0xFF8BA5B5),
    Color(0xFF96AEB8),
    Color(0xFF9DB5BE),
    Color(0xFFA5BBC4)
)

// Snowy (Night) — muted blue-gray
val SnowyNightGradient = listOf(
    Color(0xFF1E2D38),
    Color(0xFF2A3D4D),
    Color(0xFF364D60),
    Color(0xFF425D72),
    Color(0xFF4E6D82)
)

// Haze/Fog (Day & Night) — warm brown-gray, atmospheric
val HazeGradient = listOf(
    Color(0xFF7A6B5E),
    Color(0xFF948578),
    Color(0xFF9A8B7E),
    Color(0xFFA29388),
    Color(0xFFAA9B90)
)

// Windy (Day & Night) — teal-blue, fresh
val WindyGradient = listOf(
    Color(0xFF2A7B72),
    Color(0xFF3A968C),
    Color(0xFF45A098),
    Color(0xFF4DA8A0),
    Color(0xFF55B0A8)
)

// Night Fallback — deep night sky
val NightFallbackGradient = SunnyNightGradient

// Secondary screens — glass-mist palette.
// Deeper teal-green base so translucent frosted panels glow.
val SecondaryScreenGradient = listOf(
    Color(0xFF1A3B35),
    Color(0xFF223F3A),
    Color(0xFF2B4842)
)
// Frost glass panels — layered like main-page GlassCard
val SecondaryPanel = Color(0x26FFFFFF)          // ~15% white frost
val SecondaryPanelStrong = Color(0x3DFFFFFF)     // ~24% white frost
val SecondaryPanelBorder = Color(0x33FFFFFF)     // 20% white border
val SecondaryTextPrimary = Color(0xF0FFFFFF)     // near-white for dark bg
val SecondaryTextSecondary = Color(0xB3FFFFFF)   // 70% white
val SecondaryAccent = Color(0xFF7ECAB0)          // soft teal accent
val SecondaryAlert = Color(0xFFD4A574)
val DialogPanel = Color(0xFF2F5049)              // opaque, slightly lighter than bg
val DialogInnerPanel = Color(0xFF3A5E56)         // slightly lighter than panel
val DialogPanelBorder = Color(0x33FFFFFF)        // 20% white border
val DialogTextPrimary = Color(0xF0FFFFFF)       // near-white for dark dialog
val DialogTextSecondary = Color(0xB3FFFFFF)     // 70% white

// ============ Accent Colors ============
val PrecipitationBlue = Color(0xFF29B6F6)
val HumidityBlue = Color(0xFF42A5F5)