package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomPullRefreshIndicator(
    refreshing: Boolean,
    state: PullRefreshState,
    modifier: Modifier = Modifier
) {
    var isCompleting by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }

    // Spinner fade out
    val spinnerAlpha = remember { Animatable(1f) }
    // Checkmark scale in
    val checkmarkScale = remember { Animatable(0f) }
    // Checkmark path draw progress
    val checkmarkDraw = remember { Animatable(0f) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            isCompleting = false
            spinnerAlpha.snapTo(1f)
            checkmarkScale.snapTo(0f)
            checkmarkDraw.snapTo(0f)
        }
        if (!refreshing && wasRefreshing) {
            isCompleting = true
            // Spinner fades out
            spinnerAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(250, easing = EaseIn)
            )
            // Checkmark scales up
            checkmarkScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(350, easing = EaseOut)
            )
            // Draw the checkmark path
            checkmarkDraw.animateTo(
                targetValue = 1f,
                animationSpec = tween(350, easing = EaseOut)
            )
            // Hold visible
            kotlinx.coroutines.delay(800)
            isCompleting = false
        }
        wasRefreshing = refreshing
    }

    // Infinite rotation
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val pullProgress = state.progress.coerceIn(0f, 1f)
    val isVisible = pullProgress > 0.01f || refreshing || isCompleting

    if (isVisible) {
        Box(
            modifier = modifier
                .statusBarsPadding()
                .padding(top = 12.dp)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // Spinner — thin iOS-style arc, no background
            if (!isCompleting || spinnerAlpha.value > 0f) {
                Canvas(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(4.dp)
                        .alpha(
                            when {
                                refreshing -> spinnerAlpha.value
                                isCompleting -> spinnerAlpha.value
                                else -> pullProgress
                            }
                        )
                        .scale(
                            when {
                                refreshing -> 1f
                                isCompleting -> spinnerAlpha.value
                                else -> pullProgress.coerceAtLeast(0.3f)
                            }
                        )
                ) {
                    val strokeWidth = 2.dp.toPx()
                    val arcSize = size.width - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSizeDp = androidx.compose.ui.geometry.Size(arcSize, arcSize)

                    if (refreshing) {
                        // iOS-style: thin rotating arc
                        drawArc(
                            color = Color.White,
                            startAngle = rotation,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSizeDp,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    } else {
                        // Pulling: thin arc grows from top
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = pullProgress * 300f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSizeDp,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Checkmark — thin line, same position as spinner
            if (isCompleting && checkmarkScale.value > 0f) {
                Canvas(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp)
                        .scale(checkmarkScale.value)
                ) {
                    val strokeWidth = 2.dp.toPx()
                    val w = size.width
                    val h = size.height
                    val p = checkmarkDraw.value

                    // Short arm
                    val p1 = (p * 3f).coerceAtMost(1f)
                    if (p1 > 0f) {
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.15f, h * 0.52f),
                            end = Offset(
                                w * 0.15f + (w * 0.22f) * p1,
                                h * 0.52f + (h * 0.22f) * p1
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    // Long arm
                    val p2 = ((p - 0.33f) * 1.5f).coerceIn(0f, 1f)
                    if (p2 > 0f) {
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.37f, h * 0.74f),
                            end = Offset(
                                w * 0.37f + (w * 0.48f) * p2,
                                h * 0.74f - (h * 0.52f) * p2
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
