package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.util.WeatherUtils

@Composable
fun CurrentWeather(
    realtime: RealtimeWeather?,
    locationName: String,
    todayHigh: Double?,
    todayLow: Double?,
    isLocating: Boolean = false,
    showRefreshSuccess: Boolean = false,
    onLocationClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800),
        label = "fade_in"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(800, easing = EaseOut),
        label = "slide_in"
    )
    val paddingDp = offsetY.dp

    LaunchedEffect(Unit) { visible = true }

    val feelsLike = WeatherUtils.formatTemperature(realtime?.apparent_temperature)
    val windSpeed = WeatherUtils.formatWindSpeed(realtime?.wind?.speed)
    val windDir = WeatherUtils.formatWindDirection(realtime?.wind?.direction)
    val humidity = WeatherUtils.formatHumidity(realtime?.humidity)
    val aqiDesc = realtime?.air_quality?.description?.chn ?: realtime?.air_quality?.aqi?.chn?.toInt()?.let {
        when {
            it <= 50 -> "优"
            it <= 100 -> "良"
            it <= 150 -> "轻度"
            it <= 200 -> "中度"
            it <= 300 -> "重度"
            else -> "严重"
        }
    } ?: "--"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(top = paddingDp)
            .padding(horizontal = 20.dp)
    ) {
        // Location row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLocationClick != null)
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLocationClick
                        )
                    else Modifier
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLocating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = TextSecondary
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "校正位置",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = locationName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Box(modifier = Modifier.height(18.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showRefreshSuccess,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "更新成功",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Temperature centered - number is the visual anchor, degree overlaid
        val tempValue = realtime?.temperature?.toInt()?.toString() ?: "--"
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Inner box wraps content so TopEnd is relative to the number, not screen
            Box(contentAlignment = Alignment.TopEnd) {
                Text(
                    text = tempValue,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Thin,
                        letterSpacing = (-2).sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "°",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Thin
                    ),
                    color = TextPrimary,
                    modifier = Modifier.offset(x = 20.dp, y = 8.dp)
                )
            }
        }

        // Low / High below temperature
        if (todayLow != null || todayHigh != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val lowText = WeatherUtils.formatTemperature(todayLow)
                val highText = WeatherUtils.formatTemperature(todayHigh)
                Text(
                    text = "$lowText / $highText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Info blocks row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoBlock(label = "体感", value = feelsLike)
            InfoBlock(label = "风向", value = "$windDir$windSpeed")
            InfoBlock(label = "湿度", value = humidity)
            InfoBlock(label = "空气质量", value = aqiDesc)
        }
    }
}

@Composable
private fun InfoBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
