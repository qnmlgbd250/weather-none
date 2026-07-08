package com.skypulse.weather.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
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

    val rawPainter = rememberVectorPainter(Icons.Outlined.Notifications)
    val iconSizeDp = 14.dp
    val density = LocalDensity.current
    val iconSizePx = with(density) { iconSizeDp.toPx() }
    val croppedPainter = remember(rawPainter, iconSizePx) {
        object : Painter() {
            override val intrinsicSize = Size(iconSizePx, iconSizePx)
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                val scale = iconSizePx / 20f
                val offsetPx = -2f * scale
                translate(left = offsetPx, top = offsetPx) {
                    with(rawPainter) { draw(Size(24f * scale, 24f * scale)) }
                }
            }
        }
    }

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
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 3.dp)
        ) {
            Image(
                painter = croppedPainter,
                contentDescription = "预警",
                modifier = Modifier.size(iconSizeDp).offset(y = (-1).dp),
                colorFilter = ColorFilter.tint(TextSecondary)
            )
            Spacer(modifier = Modifier.width(4.dp))

            if (alerts.size == 1) {
                Text(
                    text = alerts[0].title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.offset(y = (-1).dp).clickable { onClick(0) }
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
                        modifier = Modifier.offset(y = 1.dp)
                    )
                }
            }
        }
    }
}
