package com.skypulse.weather.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.notification.WeatherNotificationScheduler
import com.skypulse.weather.ui.components.DonateDialog
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.ui.theme.SetLightStatusBarEffect
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
    var showHourlyAqi by remember { mutableStateOf(prefs.getBoolean("show_hourly_aqi", true)) }
    var showHourlyUv by remember { mutableStateOf(prefs.getBoolean("show_hourly_uv", true)) }
    var showHourlyWind by remember { mutableStateOf(prefs.getBoolean("show_hourly_wind", true)) }
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
    SetLightStatusBarEffect(lightStatusBar = true)


    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSettingsBg)
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("设置", color = IosTextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = IosBackArrow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = IosSettingsBg
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Notification settings
                SectionHeader("通知设置")
                IosCard {
                    ToggleItem("短临降水提醒", rainAlert) {
                        rainAlert = it; updateAlertPreference("rain_alert", it)
                    }
                    IosDivider()
                    ToggleItem("气象预警", warningAlert) {
                        warningAlert = it; updateAlertPreference("warning_alert", it)
                    }
                    IosDivider()
                    ToggleItem("变温提醒", tempChangeAlert) {
                        tempChangeAlert = it; updateAlertPreference("temp_change_alert", it)
                    }
                    IosDivider()
                    ToggleItem("大风提醒", windAlert) {
                        windAlert = it; updateAlertPreference("wind_alert", it)
                    }
                    IosDivider()
                    ToggleItem("极端天气", typhoonAlert) {
                        typhoonAlert = it; updateAlertPreference("typhoon_alert", it)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hourly display settings
                SectionHeader("逐小时显示")
                IosCard {
                    ToggleItem("空气质量", showHourlyAqi) {
                        showHourlyAqi = it; prefs.edit().putBoolean("show_hourly_aqi", it).apply()
                    }
                    IosDivider()
                    ToggleItem("紫外线", showHourlyUv) {
                        showHourlyUv = it; prefs.edit().putBoolean("show_hourly_uv", it).apply()
                    }
                    IosDivider()
                    ToggleItem("风力", showHourlyWind) {
                        showHourlyWind = it; prefs.edit().putBoolean("show_hourly_wind", it).apply()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // About section
                SectionHeader("关于")
                IosCard {
                    SimpleItem("检查更新") {
                        if (!isChecking) onCheckUpdate()
                    }
                    IosDivider()
                    SimpleItem("捐赠") { showDonateDialog = true }
                    IosDivider()
                    SimpleItem("GitHub") {
                        val intent = Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())
                        context.startActivity(intent)
                    }
                }

                // Update result
                if (updateState is UpdateCheckResult.UpdateAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    IosCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, updateState.url.toUri())
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "前往下载 v${updateState.version}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = IosAccentBlue
                            )
                        }
                    }
                }

                if (isChecking) {
                    Spacer(modifier = Modifier.height(8.dp))
                    IosCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Autorenew,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation),
                                tint = IosTextSecondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "正在检查更新...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = IosTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "QQ群：758426293",
                    style = MaterialTheme.typography.bodySmall,
                    color = IosTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = "SkyPulse v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IosTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun IosCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(IosCardRadius.dp))
            .background(IosCardBg),
        content = content
    )
}

 
@Composable
private fun IosDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = IosDividerColor
    )
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = IosTextSecondary,
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
            .height(52.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = IosTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosAccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = IosSwitchOff,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}


@Composable
private fun SimpleItem(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = IosTextPrimary
        )
    }
}
