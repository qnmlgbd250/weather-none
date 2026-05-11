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

import androidx.compose.ui.geometry.Offset

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isSunnyDay: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val cardBaseColor = Color.White
    
    // For sunny/bright days, we want the card to be a bit more "solid" to stand out against the sky
    val topAlpha = if (isSunnyDay) 0.28f else 0.18f
    val bottomAlpha = if (isSunnyDay) 0.12f else 0.08f
    
    // Premium border with diagonal light source simulation
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isSunnyDay) 0.65f else 0.45f),
            Color.White.copy(alpha = if (isSunnyDay) 0.15f else 0.10f),
            Color.White.copy(alpha = if (isSunnyDay) 0.35f else 0.25f)
        ),
        start = Offset(0f, 0f),
        end = Offset.Infinite
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        cardBaseColor.copy(alpha = topAlpha),
                        cardBaseColor.copy(alpha = bottomAlpha)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, borderBrush),
                RoundedCornerShape(22.dp)
            ),
        content = content
    )
}
