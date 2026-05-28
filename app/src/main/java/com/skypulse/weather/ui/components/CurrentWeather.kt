package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import com.skypulse.weather.viewmodel.RefreshPhase
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.util.WeatherUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationHeader(
    locationName: String,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: (() -> Unit)? = null,
    onListClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isLocating,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TextSecondary
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isLocating,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = "校正位置",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                var overflows by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(if (overflows) Modifier.fadingEdge() else Modifier)
                ) {
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                        onTextLayout = { overflows = it.didOverflowWidth }
                    )
                }
            }

            Row {
                if (onListClick != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onListClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "城市列表",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (onSettingsClick != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSettingsClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "设置",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.height(20.dp).padding(start = 3.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = refreshPhase != RefreshPhase.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (refreshPhase == RefreshPhase.Refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = TextSecondary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (refreshPhase == RefreshPhase.Refreshing) "正在更新数据" else "更新成功",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentWeather(
    realtime: RealtimeWeather?,
    todayHigh: Double?,
    todayLow: Double?,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
    val uvDesc = realtime?.life_index?.ultraviolet?.desc ?: "--"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(top = paddingDp)
            .padding(horizontal = 20.dp)
    ) {
        // Temperature centered — tap to refresh
        val tempValue = WeatherUtils.formatTemperature(realtime?.temperature).replace("°", "")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onRefresh != null)
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onRefresh()
                            }
                        )
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
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

        if (todayLow != null || todayHigh != null) {
            val weatherInfo = WeatherUtils.getWeatherInfo(realtime?.skycon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weatherInfo.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                val lowText = WeatherUtils.formatTemperature(todayLow)
                val highText = WeatherUtils.formatTemperature(todayHigh)
                Text(
                    text = "$lowText / $highText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "空气 $aqiDesc",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoBlock(label = "体感", value = feelsLike)
            InfoBlock(label = "风向", value = "$windDir$windSpeed")
            InfoBlock(label = "湿度", value = humidity)
            InfoBlock(label = "紫外线", value = uvDesc)
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
