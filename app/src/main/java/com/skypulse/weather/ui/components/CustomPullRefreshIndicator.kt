package com.skypulse.weather.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomPullRefreshIndicator(
    refreshing: Boolean,
    state: PullRefreshState,
    modifier: Modifier = Modifier
) {
    // Track when refresh just completed
    var showCheckmark by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (wasRefreshing && !refreshing) {
            showCheckmark = true
            kotlinx.coroutines.delay(1200)
            showCheckmark = false
        }
        wasRefreshing = refreshing
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Standard spinner (visible during pull and refresh, hidden when checkmark shows)
        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier
                .statusBarsPadding()
                .graphicsLayer { alpha = if (showCheckmark) 0f else 1f },
            contentColor = MaterialTheme.colorScheme.primary
        )

        // Checkmark overlay (appears after refresh completes)
        AnimatedVisibility(
            visible = showCheckmark,
            enter = scaleIn(initialScale = 0.5f) + fadeIn(),
            exit = scaleOut(targetScale = 0.5f) + fadeOut(),
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 4.dp)
        ) {
            CheckmarkIndicator()
        }
    }
}

@Composable
private fun CheckmarkIndicator() {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Animated draw progress for the checkmark path
    val infiniteTransition = rememberInfiniteTransition(label = "check")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Animate the checkmark drawing
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = EaseOut)
        )
    }

    Surface(
        shape = CircleShape,
        color = primaryColor.copy(alpha = 0.9f * pulseAlpha),
        modifier = Modifier.size(40.dp)
    ) {
        Canvas(modifier = Modifier.size(40.dp).padding(10.dp)) {
            val strokeWidth = 3.dp.toPx()
            val w = size.width
            val h = size.height

            // Checkmark path: short stroke then long stroke
            val p = drawProgress.value

            // First stroke: down-right (short arm)
            val p1 = (p * 3f).coerceAtMost(1f)
            if (p1 > 0f) {
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.15f, h * 0.5f),
                    end = Offset(
                        w * 0.15f + (w * 0.2f) * p1,
                        h * 0.5f + (h * 0.25f) * p1
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // Second stroke: up-right (long arm)
            val p2 = ((p - 0.33f) * 1.5f).coerceIn(0f, 1f)
            if (p2 > 0f) {
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.35f, h * 0.75f),
                    end = Offset(
                        w * 0.35f + (w * 0.5f) * p2,
                        h * 0.75f - (h * 0.55f) * p2
                    ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
