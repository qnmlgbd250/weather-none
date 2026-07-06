package com.skypulse.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.PathEffect

@Composable
fun WeatherIcon(
    iconType: String,
    size: Dp = 80.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (iconType == "clear-night") {
        MoonIcon(size = size, modifier = modifier)
        return
    }

    if (iconType == "wind") {
        WindIcon(size = size, animated = animated, modifier = modifier)
        return
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("meteocons/fill/$iconType.json")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (animated) LottieConstants.IterateForever else 1
    )
    // clear-day 的 Lottie 动画视觉填充率比其他图标高，缩放 85% 使其视觉大小一致
    val iconModifier = if (iconType == "clear-day") {
        modifier.size(size).graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
    } else {
        modifier.size(size)
    }
    LottieAnimation(
        composition = composition,
        progress = { if (animated) progress else 0.5f },
        modifier = iconModifier
    )
}

@Composable
private fun MoonIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val scale = s / 128f

        // Original Lottie path from clear-night.json (128x128 canvas)
        // Lottie path: 5 vertices (last repeats first to close)
        // v = vertices, i = in-tangent (relative to vertex), o = out-tangent (relative to vertex)
        val v = arrayOf(
            floatArrayOf(60.3018f, 32.582f),
            floatArrayOf(95.3252f, 72.5146f),
            floatArrayOf(64.5361f, 95.5f),
            floatArrayOf(32.5f, 63.8984f),
            floatArrayOf(60.3018f, 32.582f)
        )
        val o = arrayOf(
            floatArrayOf(-5.0201f, 21.1179f),
            floatArrayOf(-3.8059f, 13.2556f),
            floatArrayOf(-17.6986f, 0f),
            floatArrayOf(0f, -16.0296f),
            floatArrayOf(0f, 0f)
        )
        val inn = arrayOf(
            floatArrayOf(0f, 0f),
            floatArrayOf(-21.7251f, 1.8331f),
            floatArrayOf(14.6625f, -0.0002f),
            floatArrayOf(0.0001f, 17.446f),
            floatArrayOf(-15.6952f, 2.0458f)
        )

        val path = Path().apply {
            moveTo(v[0][0] * scale, v[0][1] * scale)
            for (i in 0 until 4) {
                val p0 = v[i]
                val p1 = v[i + 1]
                cubicTo(
                    (p0[0] + o[i][0]) * scale,
                    (p0[1] + o[i][1]) * scale,
                    (p1[0] + inn[i + 1][0]) * scale,
                    (p1[1] + inn[i + 1][1]) * scale,
                    p1[0] * scale,
                    p1[1] * scale
                )
            }
            close()
        }

        // Gradient fill: warm yellow for daytime visibility
        val gradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFD54F), Color(0xFFFFCA28)),
            startY = 32f * scale,
            endY = 96f * scale
        )
        drawPath(path = path, brush = gradient, style = Fill)

        // Gold stroke matching original
        drawPath(
            path = path,
            color = Color(0xFFF9AF03),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun WindIcon(
    size: Dp,
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    // If animated, animate the dash phase (flowing effect)
    val infiniteTransition = rememberInfiniteTransition(label = "WindOffset")
    val offsetProgress by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -105f, // negative for left-to-right flow direction (one full dash+gap loop)
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "offset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val scale = s / 128f

        // Top wind line
        val path1 = Path().apply {
            moveTo(24f * scale, 58f * scale)
            lineTo(95f * scale, 58f * scale)
            cubicTo(
                100.52f * scale, 58f * scale,
                105f * scale, 53.27f * scale,
                105f * scale, 47.45f * scale
            )
            cubicTo(
                105f * scale, 38.4f * scale,
                93.98f * scale, 33.35f * scale,
                87.79f * scale, 40.14f * scale
            )
        }

        // Bottom wind line
        val path2 = Path().apply {
            moveTo(24f * scale, 70f * scale)
            lineTo(67.62f * scale, 70f * scale)
            cubicTo(
                73.35f * scale, 70f * scale,
                78f * scale, 74.73f * scale,
                78f * scale, 80.56f * scale
            )
            cubicTo(
                78f * scale, 89.87f * scale,
                66.42f * scale, 94.52f * scale,
                60.13f * scale, 87.87f * scale
            )
        }

        // Use dashed flowing effect only when animated, solid lines when static (prevents looking missing)
        val dashEffect = if (animated) {
            PathEffect.dashPathEffect(
                intervals = floatArrayOf(80f * scale, 25f * scale),
                phase = offsetProgress * scale
            )
        } else {
            null
        }

        val strokeWidthPx = if (size <= 40.dp) 1.8.dp.toPx() else 3.dp.toPx()

        drawPath(
            path = path1,
            color = Color.White,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = dashEffect
            )
        )

        drawPath(
            path = path2,
            color = Color.White,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = dashEffect
            )
        )
    }
}