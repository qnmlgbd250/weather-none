package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.AlertContent
import com.skypulse.weather.ui.theme.SecondaryPanelBorder
import com.skypulse.weather.ui.theme.SecondaryPanelStrong
import com.skypulse.weather.ui.theme.SecondaryAlert
import com.skypulse.weather.ui.theme.SecondaryScreenGradient
import com.skypulse.weather.ui.theme.SecondaryTextPrimary
import com.skypulse.weather.ui.theme.SecondaryTextSecondary

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AlertDetailScreen(
    alerts: List<AlertContent>,
    initialSelectedIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = SecondaryScreenGradient))
    ) {
        TopAppBar(
            title = { Text("预警详情", color = SecondaryTextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = SecondaryTextSecondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无预警信息", color = SecondaryTextSecondary)
            }
        } else {
            val safeInitialIndex = remember(alerts, initialSelectedIndex) {
                initialSelectedIndex.coerceIn(alerts.indices)
            }
            LazyColumn(
                state = rememberLazyListState(
                    initialFirstVisibleItemIndex = safeInitialIndex
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(alerts) { _, alert ->
                    val title = alert.title
                        ?.replace(Regex("\\[.*?\\]"), "")
                        ?.replace(Regex("^.*发布"), "")
                        ?.trim()
                        ?.ifBlank { null }
                    val levelColor = alertLevelColor(alert.level, title ?: alert.title, SecondaryAlert)

                    // Glass-style card matching main page GlassCard
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SecondaryPanelStrong)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(
                                BorderStroke(1.dp, SecondaryPanelBorder),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (!title.isNullOrBlank()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = levelColor
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!alert.description.isNullOrBlank()) {
                                Text(
                                    text = alert.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
