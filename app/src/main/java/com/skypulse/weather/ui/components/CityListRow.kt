package com.skypulse.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.ui.theme.SecondaryPanelBorder
import com.skypulse.weather.ui.theme.SecondaryPanelStrong
import com.skypulse.weather.ui.theme.SecondaryScreenGradient
import com.skypulse.weather.ui.theme.SecondaryTextPrimary
import com.skypulse.weather.ui.theme.SecondaryTextSecondary
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils
import kotlin.math.roundToInt

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

    Box(modifier = modifier.fillMaxWidth()) {
        // Layer 1: Red delete button — bottom layer, full width
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFF3B30)),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = {
                    onDelete()
                    offsetX = 0f
                },
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

        // Layer 2: Opaque dark base — moves WITH the card, blocks red from showing through
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(SecondaryScreenGradient.first())
        )

        // Layer 3: City card — frosted glass style, slides left to reveal red delete
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(isCurrentLocation) {
                    if (isCurrentLocation) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -deleteButtonWidthPx / 2) {
                                offsetX = -deleteButtonWidthPx
                            } else {
                                offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-deleteButtonWidthPx, 0f)
                        }
                    )
                }
        ) {
            CityListRow(
                city = city,
                weather = weather,
                onClick = {
                    if (offsetX < 0f) {
                        offsetX = 0f
                    } else {
                        onClick()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CityListRow(
    city: City,
    weather: WeatherResponse?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val realtime = weather?.result?.realtime
    val skycon = realtime?.skycon
    val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
    val temperature = WeatherUtils.formatTemperature(realtime?.temperature).replace("°", "")
    val aqiValue = realtime?.air_quality?.aqi?.chn?.toInt()
    val aqiDesc = realtime?.air_quality?.description?.chn ?: aqiValue?.let {
        when {
            it <= 50 -> "优"
            it <= 100 -> "良"
            it <= 150 -> "轻度"
            it <= 200 -> "中度"
            it <= 300 -> "重度"
            else -> "严重"
        }
    }
    val aqiText = if (aqiDesc != null && aqiValue != null) "空气$aqiDesc $aqiValue" else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SecondaryPanelStrong)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = SecondaryPanelBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Top: city name + temperature — baseline aligned
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (city.isCurrentLocation) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .alignByBaseline()
                        .fillMaxWidth(0.85f)
                ) {
                    val textMeasurer = rememberTextMeasurer()
                    val nameStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
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
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }

            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier.alignByBaseline()
            ) {
                Text(
                    text = if (weather != null) temperature else "--",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Thin,
                        fontFeatureSettings = "tnum"
                    ),
                    color = TextPrimary.copy(alpha = if (weather != null) 1f else 0.4f)
                )
                if (weather != null) {
                    Text(
                        text = "°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Thin
                        ),
                        color = TextPrimary,
                        modifier = Modifier.offset(x = 10.dp, y = 2.dp)
                    )
                }
            }
        }

        // Bottom: air quality + weather description aligned
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = aqiText ?: "--",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = if (aqiText != null) 1f else 0.4f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (weather != null) weatherInfo.description else "--",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = if (weather != null) 1f else 0.4f),
                modifier = Modifier.padding(end = 8.dp)
            )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryTextPrimary
            )
            if (district.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = district,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextSecondary
                )
            }
        }
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