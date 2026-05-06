package com.skypulse.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

private class RainDrop(
    var x: Float,
    var y: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float,
    val angle: Float
)

@Composable
fun RainEffect(
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { (1.5f + intensity * 1f).dp.toPx() }

    val dropCount = (8 + intensity * 28).toInt()
    val baseSpeed = 800f + intensity * 1700f
    val minLength = 15f + intensity * 20f
    val maxLength = 25f + intensity * 30f
    val angleOffset = intensity * 10f
    val minAlpha = 0.2f + intensity * 0.3f
    val maxAlpha = 0.4f + intensity * 0.3f

    val drops = remember(intensity) {
        Array(dropCount) {
            RainDrop(
                x = Random.nextFloat() * 2000f,
                y = Random.nextFloat() * 3000f,
                speed = baseSpeed * (0.8f + Random.nextFloat() * 0.4f),
                length = minLength + Random.nextFloat() * (maxLength - minLength),
                alpha = minAlpha + Random.nextFloat() * (maxAlpha - minAlpha),
                angle = angleOffset * (0.5f + Random.nextFloat() * 0.5f) *
                        (if (Random.nextBoolean()) 1f else 0.8f)
            )
        }
    }

    var tick by remember { mutableStateOf(0) }
    var lastFrameNanos by remember { mutableStateOf(0L) }

    LaunchedEffect(intensity) {
        lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            val currentNanos = withFrameNanos { it }
            val deltaTime = ((currentNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = currentNanos
            for (drop in drops) {
                drop.y += drop.speed * deltaTime
                drop.x += sin(drop.angle * 0.01745f) * drop.speed * deltaTime * 0.3f
            }
            tick++
            delay(33)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read tick to trigger redraw each frame
        @Suppress("UNUSED_EXPRESSION")
        tick
        val w = size.width
        val h = size.height
        for (drop in drops) {
            if (drop.y > h + drop.length) {
                drop.y = -drop.length
                drop.x = Random.nextFloat() * w
            }
            if (drop.x > w) drop.x = 0f
            if (drop.x < 0f) drop.x = w

            val endX = drop.x + sin(drop.angle * 0.01745f) * drop.length
            val endY = drop.y + drop.length * 0.95f
            drawLine(
                color = Color.White.copy(alpha = drop.alpha),
                start = Offset(drop.x, drop.y),
                end = Offset(endX, endY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}
