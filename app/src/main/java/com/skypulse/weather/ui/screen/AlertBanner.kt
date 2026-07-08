package com.skypulse.weather.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
internal fun AlertBannerSlot(
    alerts: List<AlertItem>,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        if (alerts.isNotEmpty()) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                AlertBanner(alerts = alerts, onClick = onClick)
            }
        }
    }
}

@Composable
internal fun AlertBanner(alerts: List<AlertItem>, onClick: (Int) -> Unit = {}) {
    if (alerts.isEmpty()) return

    var currentAlertIndex by remember { mutableIntStateOf(0) }
    val safeAlertIndex = currentAlertIndex.coerceIn(alerts.indices)
    val currentAlert = alerts[safeAlertIndex]
    val itemHeightDp = 20.dp

    LaunchedEffect(alerts.size) {
        currentAlertIndex = safeAlertIndex
    }

    Surface(
        onClick = { onClick(if (alerts.size == 1) 0 else safeAlertIndex) },
        modifier = Modifier.padding(start = 20.dp).offset(y = (-4).dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier.size(itemHeightDp),
                contentAlignment = Alignment.Center
            ) {
                RoundedWarningIcon(
                    color = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            if (alerts.size == 1) {
                Text(
                    text = alerts[0].title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onClick(0) }
                )
            } else {
                LaunchedEffect(alerts) {
                    while (true) {
                        delay(3500)
                        currentAlertIndex = if (currentAlertIndex < alerts.size - 1) currentAlertIndex + 1 else 0
                    }
                }

                AnimatedContent(
                    targetState = safeAlertIndex,
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) { height -> height } + fadeIn(
                            animationSpec = tween(300)
                        ) togetherWith slideOutVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) { height -> -height } + fadeOut(
                            animationSpec = tween(250)
                        )
                    },
                    contentKey = { it },
                    modifier = Modifier.height(itemHeightDp).clipToBounds()
                ) { index ->
                    val alert = alerts.getOrNull(index) ?: currentAlert
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundedWarningIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = 1.65.dp.toPx()
        val triangle = Path().apply {
            moveTo(width * 0.50f, height * 0.12f)
            lineTo(width * 0.90f, height * 0.84f)
            lineTo(width * 0.10f, height * 0.84f)
            close()
        }

        drawPath(
            path = triangle,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawLine(
            color = color,
            start = Offset(width * 0.50f, height * 0.36f),
            end = Offset(width * 0.50f, height * 0.58f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = strokeWidth * 0.55f,
            center = Offset(width * 0.50f, height * 0.70f)
        )
    }
}
