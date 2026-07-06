package com.skypulse.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.LocalWeatherTheme

@Suppress("UNUSED_PARAMETER")
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

    val context = LocalContext.current
    val density = LocalDensity.current
    val weatherTheme = LocalWeatherTheme.current
    val precipitationColor = weatherTheme.precipitationIconColor.toArgb()
    val sizePx = remember(size, density) {
        with(density) { size.roundToPx() }.coerceAtLeast(1)
    }
    val bitmap = remember(context, iconType, sizePx, precipitationColor) {
        WeatherSvgRenderer.renderBitmap(context, iconType, sizePx, precipitationColor)
    }
    val iconModifier = if (iconType == "clear-day") {
        modifier.size(size).graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
    } else {
        modifier.size(size)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = iconModifier
        )
    }
}

@Composable
private fun MoonIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        val scale = s / 128f

        // Preserved Meteocons moon path (128x128 canvas)
        // Path: 5 vertices (last repeats first to close)
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

        val gradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFD54F), Color(0xFFFFCA28)),
            startY = 32f * scale,
            endY = 96f * scale
        )
        drawPath(path = path, brush = gradient, style = Fill)

        drawPath(
            path = path,
            color = Color(0xFFF9AF03),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
