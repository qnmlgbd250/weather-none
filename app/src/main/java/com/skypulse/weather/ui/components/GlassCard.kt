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

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isSunnyDay: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val cardBaseColor = if (isSunnyDay) Color(0xFF467CD6) else Color.White
    val borderColor = if (isSunnyDay) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.2f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        cardBaseColor.copy(alpha = if (isSunnyDay) 0.35f else 0.25f),
                        cardBaseColor.copy(alpha = if (isSunnyDay) 0.15f else 0.10f)
                    )
                )
            )
            .border(
                BorderStroke(0.5.dp, borderColor),
                RoundedCornerShape(20.dp)
            ),
        content = content
    )
}
