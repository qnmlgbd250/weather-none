package com.skypulse.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.ui.theme.IosTextPrimary
import com.skypulse.weather.ui.theme.IosTextSecondary
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem
import com.skypulse.weather.util.WeatherUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableCityListRow(
    city: City,
    weather: WeatherResponse?,
    isCurrentLocation: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deleteButtonWidth = 80.dp
    val deleteButtonWidthPx = with(LocalDensity.current) { deleteButtonWidth.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(),
        label = "swipeOffset"
    )

    val skycon = weather?.result?.realtime?.skycon
    val isDay = WeatherUtils.isCurrentlyDay(weather?.result?.daily)
    val gradientColors = getCityCardGradient(skycon, isDay)

    Box(modifier = modifier.fillMaxWidth()) {
        // Delete button layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(SkyPulseDesignSystem.Radius.cityCard))
                .background(Color(0xFFFF3B30)),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = { onDelete(); offsetX = 0f },
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // City card with weather gradient
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                
                .pointerInput(isCurrentLocation) {
                    if (isCurrentLocation) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < -deleteButtonWidthPx / 2) -deleteButtonWidthPx else 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-deleteButtonWidthPx, 0f)
                        }
                    )
                }
                .clip(RoundedCornerShape(SkyPulseDesignSystem.Radius.cityCard))
                .background(
                    Brush.linearGradient(colors = gradientColors)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(
                    horizontal = SkyPulseDesignSystem.Spacing.homeHorizontal,
                    vertical = SkyPulseDesignSystem.Spacing.contentGap
                )
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                // Row 1: city name (left) + temperature (right)
                val nameStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentLocation) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(0.65f)
                    ) {
                        val textMeasurer = rememberTextMeasurer()
                        val containerWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                        val overflows = remember(city.name, containerWidthPx) {
                            textMeasurer.measure(text = city.name, style = nameStyle)
                                .size.width > containerWidthPx
                        }
                        Box(
                            modifier = Modifier.then(
                                if (overflows) Modifier.fadingEdge() else Modifier
                            )
                        ) {
                            Text(
                                text = city.name,
                                style = nameStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (weather != null) "${WeatherUtils.formatTemperature(weather.result?.realtime?.temperature)}" else "--",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: air quality (left) + weather description (right)
                val aqiValue = weather?.result?.realtime?.air_quality?.aqi?.chn?.toInt()
                val aqiDesc = aqiValue?.let {
                    when {
                        it <= 50 -> "空气优"
                        it <= 100 -> "空气良"
                        it <= 150 -> "空气轻度污染"
                        it <= 200 -> "空气中度污染"
                        else -> "空气重度污染"
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (aqiDesc != null) "$aqiDesc $aqiValue" else "--",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    val weatherDesc = weather?.result?.realtime?.skycon?.let { WeatherUtils.getWeatherInfo(it).description } ?: "--"
                    Text(
                        text = weatherDesc,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
fun CitySearchResultRow(
    name: String,
    district: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = IosTextPrimary
            )
            if (district.isNotBlank()) {
                Text(
                    text = "，",
                    style = MaterialTheme.typography.bodyLarge,
                    color = IosTextSecondary
                )
                Text(
                    text = district,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IosTextSecondary
                )
            }
        }
    }
}

private fun getCityCardGradient(skycon: String?, isDay: Boolean): List<Color> {
    return when {
        skycon == null || skycon.contains("CLEAR") -> listOf(Color(0xFF4A90D9), Color(0xFF87CEEB))
        skycon.contains("PARTLY_CLOUDY") -> listOf(Color(0xFF5B9BD5), Color(0xFF8BB8E0))
        skycon.contains("CLOUDY") -> if (isDay) listOf(Color(0xFF6B7F8F), Color(0xFF8A9EAF)) else listOf(Color(0xFF8A9EB5), Color(0xFFA8B8C8))
        skycon.contains("RAIN") || skycon.contains("STORM") -> listOf(Color(0xFF556070), Color(0xFF7A8FA0))
        skycon.contains("SNOW") -> listOf(Color(0xFF7A94AA), Color(0xFF8EA5B8))
        skycon.contains("HAZE") || skycon == "FOG" -> listOf(Color(0xFF8A7B6E), Color(0xFFAA9B90))
        skycon == "WIND" -> listOf(Color(0xFF3E7F77), Color(0xFF4A8F87))
        else -> listOf(Color(0xFF4A90D9), Color(0xFF87CEEB))
    }
}

private fun Modifier.fadingEdge(): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.08f to Color.Black,
                    0.92f to Color.Black,
                    1f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }
