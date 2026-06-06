package com.skypulse.weather.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.notification.WeatherNotificationScheduler
import com.skypulse.weather.ui.components.DonateDialog
import com.skypulse.weather.ui.theme.SecondaryAccent
import com.skypulse.weather.ui.theme.SecondaryPanel
import com.skypulse.weather.ui.theme.SecondaryPanelBorder
import com.skypulse.weather.ui.theme.SecondaryPanelStrong
import com.skypulse.weather.ui.theme.SecondaryScreenGradient
import com.skypulse.weather.ui.theme.SecondaryTextPrimary
import com.skypulse.weather.ui.theme.SecondaryTextSecondary
import com.skypulse.weather.viewmodel.UpdateCheckResult

private const val GITHUB_URL = "https://github.com/qnmlgbd250/weather-none"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateState: UpdateCheckResult?,
    onClearUpdateState: () -> Unit
) {
    val context = LocalContext.current
    var showDonateDialog by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences(WeatherNotificationScheduler.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    var rainAlert by remember { mutableStateOf(prefs.getBoolean("rain_alert", true)) }
    var warningAlert by remember { mutableStateOf(prefs.getBoolean("warning_alert", true)) }
    var tempChangeAlert by remember { mutableStateOf(prefs.getBoolean("temp_change_alert", false)) }
    var windAlert by remember { mutableStateOf(prefs.getBoolean("wind_alert", false)) }
    var typhoonAlert by remember { mutableStateOf(prefs.getBoolean("typhoon_alert", true)) }
    fun updateAlertPreference(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        WeatherNotificationScheduler.scheduleIfNeeded(context.applicationContext)
    }

    val isChecking = updateState is UpdateCheckResult.Checking
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "refresh_rotation"
    )
    var frozenRotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isChecking) {
        if (isChecking) frozenRotation = 0f
    }
    val rotation = if (isChecking) animatedRotation else frozenRotation

    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateCheckResult.UpToDate -> {
                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                onClearUpdateState()
            }
            is UpdateCheckResult.Error -> {
                Toast.makeText(context, updateState.message, Toast.LENGTH_SHORT).show()
                onClearUpdateState()
            }
            else -> {}
        }
    }

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = SecondaryScreenGradient))
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("设置", color = SecondaryTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = SecondaryTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ====== Notification Settings Section ======
                GlassSection {
                    SectionHeader(title = "通知提醒")

                    ToggleItem(
                        title = "降雨提醒",
                        checked = rainAlert,
                        onCheckedChange = { enabled ->
                            rainAlert = enabled
                            updateAlertPreference("rain_alert", enabled)
                        }
                    )
                    GlassDivider()
                    ToggleItem(
                        title = "气象预警",
                        checked = warningAlert,
                        onCheckedChange = { enabled ->
                            warningAlert = enabled
                            updateAlertPreference("warning_alert", enabled)
                        }
                    )
                    GlassDivider()
                    ToggleItem(
                        title = "气温骤变",
                        checked = tempChangeAlert,
                        onCheckedChange = { enabled ->
                            tempChangeAlert = enabled
                            updateAlertPreference("temp_change_alert", enabled)
                        }
                    )
                    GlassDivider()
                    ToggleItem(
                        title = "大风提醒",
                        checked = windAlert,
                        onCheckedChange = { enabled ->
                            windAlert = enabled
                            updateAlertPreference("wind_alert", enabled)
                        }
                    )
                    GlassDivider()
                    ToggleItem(
                        title = "台风路径",
                        checked = typhoonAlert,
                        onCheckedChange = { enabled ->
                            typhoonAlert = enabled
                            updateAlertPreference("typhoon_alert", enabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ====== About Section ======
                GlassSection {
                    SectionHeader(title = "关于")
                    SimpleItem(
                        title = "GitHub 开源地址",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())
                            context.startActivity(intent)
                        }
                    )
                    GlassDivider()
                    SimpleItem(
                        title = "捐赠支持",
                        onClick = { showDonateDialog = true }
                    )
                    GlassDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isChecking) onCheckUpdate()
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "检查更新",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SecondaryTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.Autorenew,
                            contentDescription = "检查更新",
                            tint = SecondaryTextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotation)
                        )
                    }
                }

                if (updateState is UpdateCheckResult.UpdateAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Glass-style download button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, updateState.url.toUri())
                                context.startActivity(intent)
                            }
                    ) {
                        Text(
                            text = "前往下载 v${updateState.version}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SecondaryTextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "QQ群：758426293",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextSecondary.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = "SkyPulse v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextSecondary.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Glass-style section container — translucent frost + soft border
 */
@Composable
private fun GlassSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
            ),
        content = content
    )
}

/**
 * Glass-style divider inside sections
 */
@Composable
private fun GlassDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.08f)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = SecondaryTextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = SecondaryTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(32.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SecondaryAccent.copy(alpha = 0.9f),
                uncheckedThumbColor = SecondaryTextSecondary.copy(alpha = 0.7f),
                uncheckedTrackColor = SecondaryPanelStrong
            )
        )
    }
}

@Composable
private fun SimpleItem(
    title: String,
    onClick: () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = SecondaryTextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    )
}