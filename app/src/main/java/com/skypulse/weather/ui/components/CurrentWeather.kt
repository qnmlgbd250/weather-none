package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.ui.screen.LocalSkipCardAnimation
import com.skypulse.weather.util.WeatherUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationHeader(
    locationName: String,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onListClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isActive = refreshPhase != RefreshPhase.Idle
    val textOffsetY by animateDpAsState(
        targetValue = if (isActive) (-10).dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "textOffsetY"
    )

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                var overflows by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(36.dp)
                ) {
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = textOffsetY)
                            .then(if (overflows) Modifier.fadingEdge() else Modifier)
                            .basicMarquee(),
                        onTextLayout = { overflows = it.didOverflowWidth }
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isActive,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            initialOffsetY = { it / 2 }
                        ),
                        exit = fadeOut(tween(200)) + slideOutVertically(
                            animationSpec = tween(200),
                            targetOffsetY = { it / 3 }
                        ),
                        modifier = Modifier.align(Alignment.BottomStart)
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
                        SpacedListIcon(
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
                        SpacedMoreVertIcon(
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun SpacedListIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics { contentDescription = "城市列表" }
    ) {
        val strokeWidth = 1.75.dp.toPx()
        val halfWidth = 8.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        drawLine(
            color = tint,
            start = Offset(centerX - halfWidth, centerY),
            end = Offset(centerX + halfWidth, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, centerY - halfWidth),
            end = Offset(centerX, centerY + halfWidth),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SpacedMoreVertIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics { contentDescription = "\u8bbe\u7f6e" }
    ) {
        val radius = 1.75.dp.toPx()
        val spacing = 8.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        listOf(centerY - spacing, centerY, centerY + spacing).forEach { y ->
            drawCircle(
                color = tint,
                radius = radius,
                center = Offset(centerX, y)
            )
        }
    }
}

@Composable
fun CurrentWeather(
    realtime: RealtimeWeather?,
    todayHigh: Double?,
    todayLow: Double?,
    modifier: Modifier = Modifier
) {
    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(SkyPulseDesignSystem.Motion.heroEnterMillis),
        label = "fade_in"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible || skipAnimation) 0.dp else 30.dp,
        animationSpec = if (skipAnimation) tween(0) else tween(SkyPulseDesignSystem.Motion.heroEnterMillis, easing = EaseOut),
        label = "slide_in"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

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
            .alpha(cardAlpha)
            .offset(y = offsetY)
            .padding(horizontal = SkyPulseDesignSystem.Spacing.homeHorizontal)
    ) {
        val tempValue = WeatherUtils.formatTemperature(realtime?.temperature).replace("°", "")
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier,
                contentAlignment = Alignment.TopEnd
            ) {
                HeroTemperatureText(tempValue = tempValue)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

    }
}

@Composable
private fun HeroTemperatureText(tempValue: String) {
    val numberStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = SkyPulseDesignSystem.TypographyScale.temperature,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )
    val degreeStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = SkyPulseDesignSystem.TypographyScale.temperatureDegree,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    )
    val degreeOffsetX = 4.dp
    val degreeOffsetY = 12.dp

    Layout(
        content = {
            Text(text = tempValue, style = numberStyle, color = TextPrimary)
            Text(text = "°", style = degreeStyle, color = TextPrimary)
        }
    ) { measurables, constraints ->
        val numberPlaceable = measurables[0].measure(constraints)
        val degreePlaceable = measurables[1].measure(constraints)
        val degreeX = numberPlaceable.width + degreeOffsetX.roundToPx()
        val degreeY = degreeOffsetY.roundToPx()

        layout(numberPlaceable.width, maxOf(numberPlaceable.height, degreeY + degreePlaceable.height)) {
            numberPlaceable.placeRelative(0, 0)
            degreePlaceable.placeRelative(degreeX, degreeY)
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
