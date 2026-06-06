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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                Toast.makeText(context, "\u5df2\u662f\u6700\u65b0\u7248\u672c", Toast.LENGTH_SHORT).show()
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
                title = { Text("\u8bbe\u7f6e", color = SecondaryTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "\u8fd4\u56de",
                            tint = SecondaryTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                SectionHeader(title = "\u5929\u6c14\u63d0\u9192")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SecondaryPanel
                    ),
                    border = BorderStroke(1.dp, SecondaryPanelBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ToggleItem(
                        title = "\u77ed\u4e34\u96e8\u6c34\u63d0\u9192",
                        checked = rainAlert,
                        onCheckedChange = {
                            rainAlert = it
                            updateAlertPreference("rain_alert", it)
                        }
                    )

                    ToggleItem(
                        title = "\u9884\u8b66\u4fe1\u606f\u63d0\u9192",
                        checked = warningAlert,
                        onCheckedChange = {
                            warningAlert = it
                            updateAlertPreference("warning_alert", it)
                        }
                    )

                    ToggleItem(
                        title = "\u53d8\u6e29\u63d0\u9192",
                        checked = tempChangeAlert,
                        onCheckedChange = {
                            tempChangeAlert = it
                            updateAlertPreference("temp_change_alert", it)
                        }
                    )

                    ToggleItem(
                        title = "\u5927\u98ce\u63d0\u9192",
                        checked = windAlert,
                        onCheckedChange = {
                            windAlert = it
                            updateAlertPreference("wind_alert", it)
                        }
                    )

                    ToggleItem(
                        title = "\u53f0\u98ce\u4fe1\u606f\u63d0\u9192",
                        checked = typhoonAlert,
                        onCheckedChange = {
                            typhoonAlert = it
                            updateAlertPreference("typhoon_alert", it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "\u5173\u4e8e")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SecondaryPanel
                    ),
                    border = BorderStroke(1.dp, SecondaryPanelBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    SimpleItem(
                        title = "\u5f00\u6e90\u4ee3\u7801",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())
                            context.startActivity(intent)
                        }
                    )

                    SimpleItem(
                        title = "\u6350\u8d60\u652f\u6301",
                        onClick = { showDonateDialog = true }
                    )

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
                            text = "\u68c0\u67e5\u66f4\u65b0",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SecondaryTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.Autorenew,
                            contentDescription = "\u68c0\u67e5\u66f4\u65b0",
                            tint = SecondaryTextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotation)
                        )
                    }
                }

                if (updateState is UpdateCheckResult.UpdateAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, updateState.url.toUri())
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SecondaryPanelStrong,
                            contentColor = SecondaryTextPrimary
                        )
                    ) {
                        Text("\u524d\u5f80\u4e0b\u8f7d v${updateState.version}")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "QQ\u7fa4\uff1a758426293",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextSecondary.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = "SkyPulse v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextSecondary.copy(alpha = 0.65f),
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
