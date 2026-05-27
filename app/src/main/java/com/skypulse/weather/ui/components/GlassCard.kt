package com.skypulse.weather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.LocalWeatherTheme

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalWeatherTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(theme.cardTintColor)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        theme.cardFrostColor.copy(alpha = theme.cardTopAlpha),
                        theme.cardFrostColor.copy(alpha = theme.cardBottomAlpha)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, theme.cardBorderBrush),
                RoundedCornerShape(22.dp)
            ),
        content = content
    )
}
