package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherIcon(
    iconType: String,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon_anim")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    when (iconType) {
        "rain_light", "rain", "rain_heavy", "storm" -> {
            val dropOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = size.value * 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "drop_fall"
            )
            RainIcon(iconType, size, dropOffset, pulseAlpha, modifier)
        }
        "snow_light", "snow", "snow_heavy" -> {
            val snowOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = size.value * 0.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snow_fall"
            )
            SnowIcon(iconType, size, snowOffset, pulseAlpha, modifier)
        }
        else -> {
            Canvas(modifier = modifier.size(size)) {
                drawModernIcon(iconType, pulseAlpha)
            }
        }
    }
}

private fun DrawScope.drawModernIcon(iconType: String, alpha: Float) {
    when (iconType) {
        "sun" -> drawSunIcon(alpha)
        "moon" -> drawMoonIcon(alpha)
        "cloud_sun" -> drawCloudSunIcon(alpha)
        "cloud_moon" -> drawCloudMoonIcon(alpha)
        "cloud" -> drawCloudOnlyIcon(alpha)
        "haze" -> drawHazeIcon(alpha)
        "fog" -> drawFogIcon(alpha)
        "wind" -> drawWindIcon(alpha)
        else -> drawCloudOnlyIcon(alpha)
    }
}

// ============ Sun (Apple style) ============

private fun DrawScope.drawSunIcon(alpha: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension * 0.20f

    // Soft warm gradient sun body
    val sunBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFFF9C4).copy(alpha = alpha),
            Color(0xFFFFD54F).copy(alpha = alpha),
            Color(0xFFFFB300).copy(alpha = alpha * 0.9f)
        ),
        center = Offset(cx, cy),
        radius = r * 1.2f
    )

    // Outer glow - very soft
    drawCircle(Color(0xFFFFF176).copy(alpha = alpha * 0.12f), r * 2.8f, Offset(cx, cy))
    drawCircle(Color(0xFFFFF176).copy(alpha = alpha * 0.08f), r * 3.5f, Offset(cx, cy))

    // Sun body with gradient
    drawCircle(sunBrush, r, Offset(cx, cy))

    // Clean thin rays - Apple style uses gentle, even rays
    val rayInner = r * 1.35f
    val rayOuter = r * 1.9f
    val rayColor = Color(0xFFFFD54F).copy(alpha = alpha * 0.85f)

    for (i in 0 until 8) {
        val angle = i * 45f
        rotate(angle, pivot = Offset(cx, cy)) {
            // Thin rounded ray
            drawLine(
                rayColor,
                Offset(cx, cy - rayInner),
                Offset(cx, cy - rayOuter),
                strokeWidth = r * 0.14f,
                cap = StrokeCap.Round
            )
        }
    }
}

// ============ Moon (Apple style) ============

private fun DrawScope.drawMoonIcon(alpha: Float) {
    val cx = size.width * 0.45f
    val cy = size.height * 0.45f
    val r = size.minDimension * 0.24f

    // Soft glow
    drawCircle(Color(0xFFE8EAF6).copy(alpha = alpha * 0.08f), r * 3f, Offset(cx, cy))

    // Moon body with subtle gradient
    val moonBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFF5F5F5).copy(alpha = alpha),
            Color(0xFFE8EAF6).copy(alpha = alpha),
            Color(0xFFC5CAE9).copy(alpha = alpha * 0.9f)
        ),
        center = Offset(cx - r * 0.2f, cy - r * 0.2f),
        radius = r * 1.3f
    )
    drawCircle(moonBrush, r, Offset(cx, cy))

    // Crescent cutout - smooth dark overlay
    drawCircle(
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF1A1A2E).copy(alpha = alpha),
                Color(0xFF16213E).copy(alpha = alpha * 0.95f)
            ),
            center = Offset(cx + r * 0.45f, cy - r * 0.2f),
            radius = r * 0.9f
        ),
        r * 0.85f,
        Offset(cx + r * 0.45f, cy - r * 0.2f)
    )

    // Small stars with cross sparkle
    val starColor = Color(0xFFFFF9C4).copy(alpha = alpha * 0.7f)
    val stars = listOf(
        Triple(size.width * 0.78f, size.height * 0.22f, 2.0f),
        Triple(size.width * 0.88f, size.height * 0.48f, 1.5f),
        Triple(size.width * 0.72f, size.height * 0.72f, 1.8f)
    )
    stars.forEach { (sx, sy, sr) ->
        val starR = size.minDimension * sr / 80f
        drawCircle(starColor, starR, Offset(sx, sy))
        drawLine(starColor, Offset(sx - starR * 1.8f, sy), Offset(sx + starR * 1.8f, sy),
            strokeWidth = starR * 0.35f, cap = StrokeCap.Round)
        drawLine(starColor, Offset(sx, sy - starR * 1.8f), Offset(sx, sy + starR * 1.8f),
            strokeWidth = starR * 0.35f, cap = StrokeCap.Round)
    }
}

// ============ Cloud + Sun (Apple style) ============

private fun DrawScope.drawCloudSunIcon(alpha: Float) {
    // Sun behind cloud - upper right
    val sunCx = size.width * 0.65f
    val sunCy = size.height * 0.30f
    val sunR = size.minDimension * 0.16f

    // Sun glow
    drawCircle(Color(0xFFFFF176).copy(alpha = alpha * 0.10f), sunR * 3f, Offset(sunCx, sunCy))

    // Sun body
    val sunBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFFF9C4).copy(alpha = alpha),
            Color(0xFFFFD54F).copy(alpha = alpha)
        ),
        center = Offset(sunCx, sunCy),
        radius = sunR * 1.2f
    )
    drawCircle(sunBrush, sunR, Offset(sunCx, sunCy))

    // Sun rays (visible ones)
    val rayColor = Color(0xFFFFD54F).copy(alpha = alpha * 0.7f)
    for (i in 0 until 8) {
        val angle = i * 45f
        rotate(angle, pivot = Offset(sunCx, sunCy)) {
            drawLine(
                rayColor,
                Offset(sunCx, sunCy - sunR * 1.3f),
                Offset(sunCx, sunCy - sunR * 1.75f),
                strokeWidth = sunR * 0.12f,
                cap = StrokeCap.Round
            )
        }
    }

    // Cloud in foreground
    drawAppleCloud(
        cx = size.width * 0.40f,
        cy = size.height * 0.60f,
        cloudW = size.width * 0.72f,
        cloudH = size.height * 0.40f,
        alpha = alpha
    )
}

// ============ Cloud + Moon (Apple style) ============

private fun DrawScope.drawCloudMoonIcon(alpha: Float) {
    // Moon behind cloud
    val moonCx = size.width * 0.68f
    val moonCy = size.height * 0.28f
    val moonR = size.minDimension * 0.15f

    drawCircle(Color(0xFFE8EAF6).copy(alpha = alpha * 0.08f), moonR * 2.5f, Offset(moonCx, moonCy))

    val moonBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFF5F5F5).copy(alpha = alpha),
            Color(0xFFE8EAF6).copy(alpha = alpha)
        ),
        center = Offset(moonCx, moonCy),
        radius = moonR * 1.2f
    )
    drawCircle(moonBrush, moonR, Offset(moonCx, moonCy))
    drawCircle(Color(0xFF1A1A2E).copy(alpha = alpha), moonR * 0.78f,
        Offset(moonCx + moonR * 0.4f, moonCy - moonR * 0.15f))

    // Cloud
    drawAppleCloud(
        cx = size.width * 0.40f,
        cy = size.height * 0.60f,
        cloudW = size.width * 0.72f,
        cloudH = size.height * 0.40f,
        alpha = alpha
    )
}

// ============ Cloud only (Apple style) ============

private fun DrawScope.drawCloudOnlyIcon(alpha: Float) {
    // Back cloud - lighter, offset
    drawAppleCloud(
        cx = size.width * 0.55f,
        cy = size.height * 0.35f,
        cloudW = size.width * 0.58f,
        cloudH = size.height * 0.32f,
        alpha = alpha * 0.45f
    )
    // Front cloud
    drawAppleCloud(
        cx = size.width * 0.42f,
        cy = size.height * 0.58f,
        cloudW = size.width * 0.72f,
        cloudH = size.height * 0.40f,
        alpha = alpha
    )
}

// ============ Haze (Apple style) ============

private fun DrawScope.drawHazeIcon(alpha: Float) {
    // Small cloud at top
    drawAppleCloud(
        cx = size.width * 0.5f,
        cy = size.height * 0.28f,
        cloudW = size.width * 0.58f,
        cloudH = size.height * 0.30f,
        alpha = alpha * 0.55f
    )

    // Haze bars - soft, rounded, fading
    val barColor = Color(0xFFCFD8DC).copy(alpha = alpha * 0.55f)
    val bars = listOf(
        Triple(0.56f, 0.72f, 0.035f),
        Triple(0.67f, 0.55f, 0.03f),
        Triple(0.78f, 0.65f, 0.028f),
        Triple(0.89f, 0.45f, 0.025f)
    )
    bars.forEach { (yFrac, lenFrac, hFrac) ->
        val barW = size.width * lenFrac
        val barH = size.height * hFrac
        val startX = (size.width - barW) / 2f
        val y = size.height * yFrac - barH / 2f
        drawRoundRect(
            color = barColor.copy(alpha = barColor.alpha * (1f - (yFrac - 0.56f) * 1.5f)),
            topLeft = Offset(startX, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH / 2f)
        )
    }
}

// ============ Fog (Apple style) ============

private fun DrawScope.drawFogIcon(alpha: Float) {
    // Small cloud
    drawAppleCloud(
        cx = size.width * 0.5f,
        cy = size.height * 0.22f,
        cloudW = size.width * 0.52f,
        cloudH = size.height * 0.26f,
        alpha = alpha * 0.45f
    )

    // Fog lines - soft, fading
    val fogColor = Color(0xFFCFD8DC).copy(alpha = alpha * 0.5f)
    val lines = listOf(
        Triple(0.48f, 0.68f, 0.028f),
        Triple(0.58f, 0.82f, 0.025f),
        Triple(0.68f, 0.55f, 0.022f),
        Triple(0.78f, 0.72f, 0.02f),
        Triple(0.88f, 0.48f, 0.018f)
    )
    lines.forEachIndexed { i, (yFrac, lenFrac, hFrac) ->
        val barW = size.width * lenFrac
        val barH = size.height * hFrac
        val startX = (size.width - barW) / 2f
        val y = size.height * yFrac - barH / 2f
        drawRoundRect(
            color = fogColor.copy(alpha = fogColor.alpha * (1f - i * 0.18f)),
            topLeft = Offset(startX, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH / 2f)
        )
    }
}

// ============ Wind (Apple style) ============

private fun DrawScope.drawWindIcon(alpha: Float) {
    val windColor = Color(0xFFB0BEC5).copy(alpha = alpha)

    // Three clean curved wind lines
    val lines = listOf(
        Triple(0.28f, 0.62f, 0.5f),
        Triple(0.46f, 0.78f, 0.65f),
        Triple(0.64f, 0.50f, 0.4f)
    )

    lines.forEach { (yFrac, lenFrac, curveFrac) ->
        val y = size.height * yFrac
        val lineLen = size.width * lenFrac
        val startX = (size.width - lineLen) / 2f
        val curveAmt = size.height * curveFrac * 0.07f

        // Smooth curved line
        val path = Path().apply {
            moveTo(startX, y)
            cubicTo(
                startX + lineLen * 0.35f, y - curveAmt,
                startX + lineLen * 0.65f, y + curveAmt * 0.4f,
                startX + lineLen, y - curveAmt * 0.25f
            )
        }
        drawPath(path, windColor, style = Stroke(
            width = size.width * 0.035f,
            cap = StrokeCap.Round
        ))

        // Arrow head at end
        val endX = startX + lineLen
        val endY = y - curveAmt * 0.25f
        val arrowSize = size.width * 0.05f
        val arrowPath = Path().apply {
            moveTo(endX, endY)
            lineTo(endX - arrowSize * 1.4f, endY - arrowSize * 0.7f)
            lineTo(endX - arrowSize * 1.1f, endY + arrowSize * 0.5f)
            close()
        }
        drawPath(arrowPath, windColor)
    }
}

// ============ Rain (Apple style) ============

@Composable
private fun RainIcon(
    iconType: String,
    iconSize: Dp,
    dropOffset: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val dropTint = when (iconType) {
        "rain_light" -> Color(0xFF64B5F6)
        "rain" -> Color(0xFF42A5F5)
        "rain_heavy" -> Color(0xFF1E88E5)
        "storm" -> Color(0xFF1E88E5)
        else -> Color(0xFF42A5F5)
    }
    val dropCount = when (iconType) {
        "rain_light" -> 1
        "rain" -> 2
        "rain_heavy" -> 3
        "storm" -> 3
        else -> 2
    }
    val showLightning = iconType == "storm"

    Canvas(modifier = modifier.size(iconSize)) {
        val cloudHeight = size.height * 0.48f
        val dropSection = size.height * 0.35f
        val gap = size.height * 0.06f
        val total = cloudHeight + gap + dropSection
        val topOff = ((size.height - total) / 2f).coerceAtLeast(0f)

        // Cloud
        drawAppleCloud(
            cx = size.width / 2f,
            cy = topOff + cloudHeight * 0.5f,
            cloudW = size.width * 0.82f,
            cloudH = cloudHeight,
            alpha = alpha
        )

        // Teardrop rain drops
        val dropsTop = topOff + cloudHeight + gap
        val dropLen = dropSection * 0.38f
        val spacing = size.width * 0.22f
        val cx = size.width / 2f

        val positions = when (dropCount) {
            1 -> listOf(cx)
            2 -> listOf(cx - spacing / 2f, cx + spacing / 2f)
            3 -> listOf(cx - spacing, cx, cx + spacing)
            else -> listOf(cx)
        }

        positions.forEach { x ->
            val startY = dropsTop + (dropOffset % (dropSection * 0.28f))
            val dW = size.width * 0.022f

            // Main teardrop
            val dropPath = Path().apply {
                moveTo(x, startY)
                cubicTo(x - dW, startY + dropLen * 0.4f, x - dW, startY + dropLen * 0.7f, x, startY + dropLen)
                cubicTo(x + dW, startY + dropLen * 0.7f, x + dW, startY + dropLen * 0.4f, x, startY)
                close()
            }
            drawPath(dropPath, dropTint.copy(alpha = alpha * 0.85f))

            // Trailing smaller drop
            val gap2 = dropSection * 0.32f
            val startY2 = startY - gap2
            if (startY2 > dropsTop - dropSection * 0.12f) {
                val dLen2 = dropLen * 0.55f
                val dW2 = dW * 0.65f
                val dropPath2 = Path().apply {
                    moveTo(x, startY2)
                    cubicTo(x - dW2, startY2 + dLen2 * 0.4f, x - dW2, startY2 + dLen2 * 0.7f, x, startY2 + dLen2)
                    cubicTo(x + dW2, startY2 + dLen2 * 0.7f, x + dW2, startY2 + dLen2 * 0.4f, x, startY2)
                    close()
                }
                drawPath(dropPath2, dropTint.copy(alpha = alpha * 0.4f))
            }
        }

        // Lightning for storm
        if (showLightning) {
            val lx = cx + size.width * 0.13f
            val ly = dropsTop + dropSection * 0.02f
            val boltPath = Path().apply {
                moveTo(lx, ly)
                lineTo(lx - size.width * 0.055f, ly + dropSection * 0.35f)
                lineTo(lx + size.width * 0.015f, ly + dropSection * 0.28f)
                lineTo(lx - size.width * 0.035f, ly + dropSection * 0.72f)
                lineTo(lx + size.width * 0.055f, ly + dropSection * 0.25f)
                lineTo(lx, ly + dropSection * 0.30f)
                close()
            }
            drawPath(boltPath, Color(0xFFFFD54F).copy(alpha = alpha * 0.9f))
        }
    }
}

// ============ Snow (Apple style) ============

@Composable
private fun SnowIcon(
    iconType: String,
    iconSize: Dp,
    snowOffset: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val snowCount = when (iconType) {
        "snow_light" -> 3
        "snow" -> 5
        "snow_heavy" -> 7
        else -> 5
    }
    val snowColor = Color(0xFFBBDEFB).copy(alpha = alpha * 0.85f)

    Canvas(modifier = modifier.size(iconSize)) {
        val cloudHeight = size.height * 0.44f
        val snowSection = size.height * 0.38f
        val gap = size.height * 0.06f
        val total = cloudHeight + gap + snowSection
        val topOff = ((size.height - total) / 2f).coerceAtLeast(0f)

        // Cloud
        drawAppleCloud(
            cx = size.width / 2f,
            cy = topOff + cloudHeight * 0.5f,
            cloudW = size.width * 0.78f,
            cloudH = cloudHeight,
            alpha = alpha
        )

        // Snowflakes - clean dot + cross style
        val snowTop = topOff + cloudHeight + gap
        val flakeR = size.minDimension * 0.022f

        val xPositions = when (snowCount) {
            3 -> listOf(0.25f, 0.5f, 0.75f)
            5 -> listOf(0.15f, 0.32f, 0.5f, 0.68f, 0.85f)
            7 -> listOf(0.1f, 0.25f, 0.38f, 0.5f, 0.62f, 0.75f, 0.9f)
            else -> listOf(0.3f, 0.5f, 0.7f)
        }

        xPositions.forEachIndexed { i, xFrac ->
            val x = size.width * xFrac
            val phaseOffset = i * size.height * 0.055f
            val yOffset = ((snowOffset + phaseOffset) % (snowSection * 0.55f))
            val y = snowTop + yOffset

            if (y < snowTop + snowSection) {
                val sr = flakeR * (1f + (i % 2) * 0.25f)
                // Center dot
                drawCircle(snowColor, sr * 0.55f, Offset(x, y))
                // Six clean arms
                for (arm in 0 until 6) {
                    val angle = arm * 60f
                    val rad = Math.toRadians(angle.toDouble())
                    val endX = x + sr * cos(rad).toFloat()
                    val endY = y + sr * sin(rad).toFloat()
                    drawLine(snowColor, Offset(x, y), Offset(endX, endY),
                        strokeWidth = sr * 0.3f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

// ============ Shared: Apple-style cloud ============

private fun DrawScope.drawAppleCloud(
    cx: Float,
    cy: Float,
    cloudW: Float,
    cloudH: Float,
    alpha: Float
) {
    // Soft white cloud with subtle gradient
    val cloudBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFAFAFA).copy(alpha = alpha),
            Color(0xFFE0E0E0).copy(alpha = alpha * 0.9f)
        ),
        startY = cy - cloudH * 0.4f,
        endY = cy + cloudH * 0.3f
    )

    // Base rectangle (flat bottom)
    val baseY = cy + cloudH * 0.22f
    drawRoundRect(
        color = Color(0xFFEEEEEE).copy(alpha = alpha),
        topLeft = Offset(cx - cloudW * 0.36f, baseY - cloudH * 0.06f),
        size = Size(cloudW * 0.72f, cloudH * 0.18f),
        cornerRadius = CornerRadius(cloudH * 0.09f)
    )

    // Cloud body circles with gradient
    val circles = listOf(
        Triple(cx - cloudW * 0.14f, cy - cloudH * 0.04f, cloudH * 0.30f),
        Triple(cx + cloudW * 0.06f, cy - cloudH * 0.11f, cloudH * 0.34f),
        Triple(cx + cloudW * 0.22f, cy + 0f, cloudH * 0.26f),
        Triple(cx - cloudW * 0.26f, cy + cloudH * 0.08f, cloudH * 0.22f),
        Triple(cx + cloudW * 0.13f, cy + cloudH * 0.08f, cloudH * 0.24f),
        Triple(cx, cy + cloudH * 0.04f, cloudH * 0.32f),
    )

    circles.forEach { (x, y, r) ->
        drawCircle(cloudBrush, r, Offset(x, y))
    }
}
