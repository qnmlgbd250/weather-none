package com.skypulse.weather.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.ui.components.DonateDialog
import com.skypulse.weather.ui.theme.NightFallbackGradient
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
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

    val isChecking = updateState is UpdateCheckResult.Checking
    val rotation by animateFloatAsState(
        targetValue = if (isChecking) 360f else 0f,
        animationSpec = if (isChecking) {
            infiniteRepeatable(tween(1000, easing = LinearEasing))
        } else {
            tween(300)
        },
        label = "refresh_rotation"
    )

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
            .background(Brush.verticalGradient(colors = NightFallbackGradient))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("关于", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = TextSecondary
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

                SimpleItem(
                    title = "开源代码",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = TextSecondary.copy(alpha = 0.1f)
                )

                SimpleItem(
                    title = "捐赠支持",
                    onClick = { showDonateDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = TextSecondary.copy(alpha = 0.1f)
                )

                // 检查更新
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
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Autorenew,
                        contentDescription = "检查更新",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotation)
                    )
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
                            .padding(horizontal = 20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextSecondary.copy(alpha = 0.15f),
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("前往下载 v${updateState.version}")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "QQ群：758426293",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = "SkyPulse v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
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
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    )
}
