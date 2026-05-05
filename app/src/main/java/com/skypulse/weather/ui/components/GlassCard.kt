package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isSunnyDay: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val cardColor = if (isSunnyDay) Color(0xFF467CD6) else Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor.copy(alpha = if (isSunnyDay) 0.25f else 0.15f))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        cardColor.copy(alpha = if (isSunnyDay) 0.3f else 0.2f),
                        cardColor.copy(alpha = if (isSunnyDay) 0.1f else 0.05f)
                    )
                )
            ),
        content = content
    )
}
