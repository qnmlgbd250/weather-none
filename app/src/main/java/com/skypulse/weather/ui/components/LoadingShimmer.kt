package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f)
        ),
        start = Offset(shimmerTranslate, shimmerTranslate),
        end = Offset(shimmerTranslate + 300f, shimmerTranslate + 300f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Location placeholder
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush)
        )

        // Icon placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(androidx.compose.ui.Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(shimmerBrush)
        )

        // Temperature placeholder
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(60.dp)
                .align(androidx.compose.ui.Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerBrush)
        )

        // Description placeholder
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(20.dp)
                .align(androidx.compose.ui.Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card placeholders
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shimmerBrush)
            )
        }
    }
}
