package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.AlertContent
import com.skypulse.weather.ui.theme.NightFallbackGradient
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary

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
            .background(Brush.verticalGradient(colors = NightFallbackGradient))
    ) {
        TopAppBar(
            title = { Text("预警详情", color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextSecondary
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
                Text(text = "暂无预警信息", color = TextSecondary)
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
                    val levelColor = alertColor(alert.level)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (!title.isNullOrBlank()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = levelColor
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val metaParts = listOfNotNull(
                                alert.level?.let { "级别：$it" },
                                alert.type?.let { "类型：$it" },
                                alert.status?.let { "状态：$it" }
                            )
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    text = metaParts.joinToString(separator = "  |  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            val regionParts = listOfNotNull(
                                alert.province?.takeIf { it.isNotBlank() },
                                alert.city?.takeIf { it.isNotBlank() },
                                alert.county?.takeIf { it.isNotBlank() }
                            )
                            if (regionParts.isNotEmpty()) {
                                Text(
                                    text = regionParts.joinToString(separator = " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!alert.description.isNullOrBlank()) {
                                Text(
                                    text = alert.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
