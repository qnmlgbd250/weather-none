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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.ActivationResult
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.ui.components.MembershipDialog
import com.skypulse.weather.ui.components.VipBadge
import com.skypulse.weather.ui.components.VipStatusCard
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.viewmodel.UpdateCheckResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateState: UpdateCheckResult?,
    onClearUpdateState: () -> Unit,
    settings: WeatherSettings,
    onRainAlertChange: (Boolean) -> Unit,
    onWarningAlertChange: (Boolean) -> Unit,
    onTempChangeAlertChange: (Boolean) -> Unit,
    onWindAlertChange: (Boolean) -> Unit,
    onTyphoonAlertChange: (Boolean) -> Unit,
    onShowHourlyAqiChange: (Boolean) -> Unit,
    onShowHourlyUvChange: (Boolean) -> Unit,
    onShowHourlyWindChange: (Boolean) -> Unit,
    onShowHourlyWindGustChange: (Boolean) -> Unit,
    onShowCardDetailChange: (Boolean) -> Unit,
    onShowCardSunriseSunsetChange: (Boolean) -> Unit,
    onShowCardMinutelyChange: (Boolean) -> Unit,
    isPremium: Boolean = false,
    activatedAt: Long = 0L,
    deviceId: String = "",
    onActivateCode: (String) -> ActivationResult = { _ -> ActivationResult.INVALID_CODE }
) {
    val context = LocalContext.current
    var showMembershipDialog by remember { mutableStateOf(false) }

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
    if (showMembershipDialog) {
        MembershipDialog(
            onDismiss = { showMembershipDialog = false },
            onActivate = onActivateCode,
            deviceId = deviceId
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyPulseDesignSystem.Colors.settingsBackground)
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
                    containerColor = SkyPulseDesignSystem.Colors.settingsBackground
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // VIP / 会员激活
                if (isPremium) {
                    Box(modifier = Modifier.padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)) {
                        VipStatusCard(activatedAt = activatedAt)
                    }
                } else {
                    SectionHeader("会员")
                    IosCard {
                        SimpleItem(
                            title = "激活会员",
                            subtitle = buildAnnotatedString {
                                append("¥19.9成为")
                                withStyle(SpanStyle(color = Color(0xFFFFC125), fontWeight = FontWeight.Bold)) {
                                    append("永久会员")
                                }
                                append("解锁所有高级功能")
                            },
                            titleColor = IosAccentBlue,
                            onClick = { showMembershipDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Notification settings
                SectionHeader("通知设置")
                IosCard {
                    ToggleItem(
                        title = "短临降水提醒",
                        checked = settings.rainAlert,
                        onCheckedChange = onRainAlertChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "气象预警",
                        checked = settings.warningAlert,
                        onCheckedChange = onWarningAlertChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem("变温提醒", settings.tempChangeAlert, onTempChangeAlertChange)
                    IosDivider()
                    ToggleItem("大风提醒", settings.windAlert, onWindAlertChange)
                    IosDivider()
                    ToggleItem(
                        title = "极端天气",
                        checked = settings.typhoonAlert,
                        onCheckedChange = onTyphoonAlertChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hourly display settings
                SectionHeader("逐小时显示")
                IosCard {
                    ToggleItem(
                        title = "空气质量",
                        checked = settings.showHourlyAqi,
                        onCheckedChange = onShowHourlyAqiChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "紫外线",
                        checked = settings.showHourlyUv,
                        onCheckedChange = onShowHourlyUvChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "风力",
                        checked = settings.showHourlyWind,
                        onCheckedChange = onShowHourlyWindChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "阵风",
                        checked = settings.showHourlyWindGust,
                        onCheckedChange = onShowHourlyWindGustChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Card display settings
                SectionHeader("卡片显示")
                IosCard {
                    ToggleItem(
                        title = "分钟级降水",
                        checked = settings.showCardMinutely,
                        onCheckedChange = onShowCardMinutelyChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "气象详情",
                        checked = settings.showCardDetail,
                        onCheckedChange = onShowCardDetailChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "日出日落",
                        checked = settings.showCardSunriseSunset,
                        onCheckedChange = onShowCardSunriseSunsetChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    if (!isPremium) {
                        IosDivider()
                        SimpleItem(
                            title = "多城市天气",
                            titleColor = IosTextSecondary,
                            trailing = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "会员功能",
                                    modifier = Modifier.size(18.dp),
                                    tint = IosTextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            onClick = { showMembershipDialog = true }
                        )
                        IosDivider()
                        SimpleItem(
                            title = "街道/小区级定位",
                            titleColor = IosTextSecondary,
                            trailing = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "会员功能",
                                    modifier = Modifier.size(18.dp),
                                    tint = IosTextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            onClick = { showMembershipDialog = true }
                        )
                        IosDivider()
                        SimpleItem(
                            title = "AI天气校准",
                            titleColor = IosTextSecondary,
                            trailing = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "会员功能",
                                    modifier = Modifier.size(18.dp),
                                    tint = IosTextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            onClick = { showMembershipDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // About section
                SectionHeader("关于")
                IosCard {
                    SimpleItem("检查更新") {
                        if (!isChecking) onCheckUpdate()
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
                    text = "QQ群：758426293   邮箱：1096005725@qq.com",
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
            .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            .clip(RoundedCornerShape(SkyPulseDesignSystem.Radius.settingsCard))
            .background(SkyPulseDesignSystem.Colors.settingsSurface),
        content = content
    )
}

 
@Composable
private fun IosDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = SkyPulseDesignSystem.Border.hairline,
        color = SkyPulseDesignSystem.Colors.settingsDivider
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
    onCheckedChange: (Boolean) -> Unit,
    locked: Boolean = false,
    onLockedClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable {
                if (locked) onLockedClick?.invoke()
                else onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (locked) IosTextSecondary else IosTextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (locked) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "会员功能",
                modifier = Modifier.size(18.dp),
                tint = IosTextSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Switch(
            checked = if (locked) false else checked,
            onCheckedChange = if (locked) null else onCheckedChange,
            enabled = !locked,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosAccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = IosSwitchOff,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = IosSwitchOff,
                disabledUncheckedThumbColor = Color.White,
                disabledUncheckedTrackColor = IosSwitchOff
            )
        )
    }
}


@Composable
private fun SimpleItem(
    title: String,
    subtitle: AnnotatedString? = null,
    titleColor: Color = IosTextPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IosTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}
