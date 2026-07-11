package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalWeatherTheme.current
    val shape = RoundedCornerShape(SkyPulseDesignSystem.Radius.card)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.cardTintColor),
        content = content
    )
}
