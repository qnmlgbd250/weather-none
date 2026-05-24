package com.skypulse.weather.ui.theme

import androidx.compose.ui.graphics.Color

// ============ Primary Palette ============
val SkyBlue = Color(0xFF4FC3F7)
val WarmGold = Color(0xFFFFD54F)

// ============ Surface Colors ============
val CardSurface = Color(0x33FFFFFF)
val CardSurfaceLight = Color(0x1AFFFFFF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xD9FFFFFF)

// ============ iOS Weather-style Background Gradients ============
// Each gradient uses 5 stops for smooth, natural sky transitions

// Sunny (Day) — deep cobalt to bright sky blue
val SunnyGradient = listOf(
    Color(0xFF1565A0),
    Color(0xFF2980C0),
    Color(0xFF4A9BD5),
    Color(0xFF6DB5E5),
    Color(0xFF95CDF0)
)

// Sunny (Night) — deep navy to midnight blue
val SunnyNightGradient = listOf(
    Color(0xFF0B1929),
    Color(0xFF122640),
    Color(0xFF1A3355),
    Color(0xFF1E3D65),
    Color(0xFF234A72)
)

// Partly Cloudy (Day) — medium blue to light sky
val PartialCloudGradient = listOf(
    Color(0xFF3578A8),
    Color(0xFF4E92C0),
    Color(0xFF6AABD5),
    Color(0xFF85C0E5),
    Color(0xFFA0D2F0)
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
    Color(0xFF94B1BF),
    Color(0xFFADC7D3),
    Color(0xFFC2D8E2)
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
    Color(0xFF688296),
    Color(0xFF7B95A8),
    Color(0xFF8EA7B8)
)

// Rainy (Night) — deep blue-black
val RainyNightGradient = listOf(
    Color(0xFF0F1A24),
    Color(0xFF182636),
    Color(0xFF213348),
    Color(0xFF2A3F55),
    Color(0xFF324A60)
)

// Snowy (Day) — cold gray-blue to near white
val SnowyGradient = listOf(
    Color(0xFF6E8A9E),
    Color(0xFF8BA5B5),
    Color(0xFFA5BCC8),
    Color(0xFFBDD0DA),
    Color(0xFFD5E3EA)
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
    Color(0xFFAA9C90),
    Color(0xFFBFB2A6),
    Color(0xFFD0C5BA)
)

// Windy (Day & Night) — teal-blue, fresh
val WindyGradient = listOf(
    Color(0xFF2A7B72),
    Color(0xFF3A968C),
    Color(0xFF4DAEA3),
    Color(0xFF62C4B8),
    Color(0xFF7CD8CC)
)

// Night Fallback — deep night sky
val NightFallbackGradient = SunnyNightGradient

// ============ Accent Colors ============
val PrecipitationBlue = Color(0xFF29B6F6)
val HumidityBlue = Color(0xFF42A5F5)
