package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import com.skypulse.weather.ui.theme.SecondaryPanelBorder
import com.skypulse.weather.ui.theme.SecondaryPanelStrong
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
            title = { Text("\u9884\u8b66\u8be6\u60c5", color = SecondaryTextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "\u8fd4\u56de",
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
                Text(text = "\u6682\u65e0\u9884\u8b66\u4fe1\u606f", color = SecondaryTextSecondary)
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
                        ?.replace(Regex("^.*\u53d1\u5e03"), "")
                        ?.trim()
                        ?.ifBlank { null }
                    val levelColor = detailAlertColor(alert.level)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SecondaryPanelStrong),
                        border = BorderStroke(1.dp, SecondaryPanelBorder)
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

private fun detailAlertColor(level: String?): Color {
    return when {
        level?.contains("红") == true -> Color(0xFFC73E3A)
        level?.contains("橙") == true -> Color(0xFFB75C00)
        level?.contains("黄") == true -> Color(0xFF8A6B00)
        level?.contains("蓝") == true -> Color(0xFF2D68B8)
        else -> Color(0xFFA66E3F)
    }
}
