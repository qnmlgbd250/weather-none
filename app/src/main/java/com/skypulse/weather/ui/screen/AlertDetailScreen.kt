package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.AlertContent
import com.skypulse.weather.model.sortedByPublishTimeDescending
import com.skypulse.weather.ui.theme.IosCardBg
import com.skypulse.weather.ui.theme.IosSettingsBg
import com.skypulse.weather.ui.theme.IosTextPrimary
import com.skypulse.weather.ui.theme.IosTextSecondary
import com.skypulse.weather.ui.theme.IosBackArrow
import com.skypulse.weather.ui.theme.IosDividerColor


@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AlertDetailScreen(
    alerts: List<AlertContent>,
    initialSelectedIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    val sortedAlerts = remember(alerts) {
        alerts.sortedByPublishTimeDescending()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSettingsBg)
    ) {
        TopAppBar(
            title = { Text("预警详情", color = IosTextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = IosBackArrow
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        if (sortedAlerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无预警信息", color = IosTextSecondary)
            }
        } else {
            val safeInitialIndex = remember(alerts, initialSelectedIndex) {
                initialSelectedIndex.coerceIn(sortedAlerts.indices)
            }
            LazyColumn(
                state = rememberLazyListState(
                    initialFirstVisibleItemIndex = safeInitialIndex
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                itemsIndexed(sortedAlerts) { _, alert ->
                    val title = alert.title
                        ?.replace(Regex("\\[.*?\\]"), "")
                        ?.replace(Regex("^.*(?:发布|变更|解除|继续|更新)"), "")
                        ?.replace(Regex("预警.*$"), "预警")
                        ?.trim()
                        ?.ifBlank { null }

                    // Glass-style card matching main page GlassCard
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(IosCardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (!title.isNullOrBlank()) {
                                val alertColor = remember(alert) {
                                    alertLevelColor(alert.level, alert.title, fallback = IosTextPrimary)
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = alertColor
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!alert.description.isNullOrBlank()) {
                                Text(
                                    text = alert.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IosTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
