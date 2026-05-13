package com.skypulse.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils
import kotlin.math.roundToInt

@Composable
fun SwipeableCityListRow(
    city: City,
    weather: WeatherResponse?,
    isLoading: Boolean,
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

        // Layer 2: Opaque dark base — moves WITH the card, only covers the card area
        // This blocks the red from showing through the transparent card
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D1B2A))
        )

        // Layer 3: City card — on top, slides left to reveal red delete button
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
                isLoading = isLoading,
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

@Composable
fun CityListRow(
    city: City,
    weather: WeatherResponse?,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val realtime = weather?.result?.realtime
    val todayTemp = weather?.result?.daily?.temperature?.firstOrNull()
    val skycon = realtime?.skycon
    val isDay = WeatherUtils.isCurrentlyDay()
    val gradientColors = WeatherUtils.getWeatherGradient(skycon, isDay)
    val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
    val temperature = WeatherUtils.formatTemperature(realtime?.temperature).replace("°", "")
    val high = WeatherUtils.formatTemperature(todayTemp?.max)
    val low = WeatherUtils.formatTemperature(todayTemp?.min)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        gradientColors[0].copy(alpha = 0.7f),
                        gradientColors[1].copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (city.isCurrentLocation) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = city.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = weatherInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLoading && weather != null) {
                    WeatherIcon(
                        iconType = weatherInfo.icon,
                        size = 36.dp,
                        animated = false
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = TextSecondary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = temperature,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Thin
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "°",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Thin
                                ),
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$low / $high",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            if (district.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = district,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
