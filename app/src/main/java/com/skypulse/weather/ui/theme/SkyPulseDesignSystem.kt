package com.skypulse.weather.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SkyPulseDesignSystem {
    object Colors {
        val accent = SkyBlue
        val accentWarm = WarmGold

        val textPrimary = TextPrimary
        val textSecondary = TextSecondary
        val textTertiary = TextTertiary
        val textDisabled = TextDisabled

        val glassBorderDay = Color.White.copy(alpha = 0.28f)
        val glassBorderNight = Color.White.copy(alpha = 0.14f)
        val glassDivider = Color.White.copy(alpha = 0.16f)
        val glassSummary = Color.White.copy(alpha = 0.14f)
        val glassSummaryNight = Color.White.copy(alpha = 0.09f)

        val settingsBackground = IosSettingsBg
        val settingsSurface = IosCardBg
        val settingsDivider = IosDividerColor
        val settingsTextPrimary = IosTextPrimary
        val settingsTextSecondary = IosTextSecondary
        val settingsAccent = IosAccentBlue
    }

    object Radius {
        val card = 20.dp
        val cityCard = 18.dp
        val settingsCard = 16.dp
        val control = 14.dp
        val small = 6.dp
        val pill = 50.dp
    }

    object Elevation {
        val glass = 6.dp
        val cityCard = 3.dp
        val flat = 0.dp
    }

    object Border {
        val hairline = 0.5.dp
        val thin = 1.dp
    }

    object Spacing {
        val screenHorizontal = 16.dp
        val homeHorizontal = 20.dp
        val cardVertical = 14.dp
        val sectionGap = 8.dp
        val contentGap = 12.dp
        val itemGap = 8.dp
    }

    object TypographyScale {
        val temperature = 100.sp
        val temperatureDegree = 48.sp
    }

    object Motion {
        const val fastMillis = 200
        const val standardMillis = 350
        const val cardEnterMillis = 600
        const val heroEnterMillis = 800
        const val cardEnterDelayMillis = 300
        const val lifecycleSkipMillis = 1200L
    }

    object TouchTarget {
        val compact = 40.dp
        val default = 48.dp
        val listRow = 52.dp
    }
}
