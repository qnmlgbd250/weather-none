package com.skypulse.weather.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.R
import com.skypulse.weather.data.ActivationResult
import com.skypulse.weather.ui.theme.IosAccentBlue
import com.skypulse.weather.ui.theme.IosCardBg
import com.skypulse.weather.ui.theme.IosDividerColor
import com.skypulse.weather.ui.theme.IosTextPrimary
import com.skypulse.weather.ui.theme.IosTextSecondary

/**
 * 激活码输入弹窗
 *
 * 显示设备 ID，用户将设备 ID 发给开发者获取专属激活码
 * 激活码格式：XXXX-XXXX（8位，绑定设备）
 */
@Composable
fun MembershipDialog(
    onDismiss: () -> Unit,
    onActivate: (code: String) -> ActivationResult,
    deviceId: String = ""
) {
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isActivating by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (!isActivating) onDismiss()
        },
        containerColor = IosCardBg,
        shape = RoundedCornerShape(14.dp),
        title = {
            if (showSuccess) {
                Text(
                    text = "激活成功",
                    style = MaterialTheme.typography.titleSmall,
                    color = IosTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "激活会员",
                    style = MaterialTheme.typography.titleSmall,
                    color = IosTextPrimary
                )
            }
        },
        text = {
            if (showSuccess) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VipBadge()
                    Text(
                        text = "恭喜，你已成为 SkyPulse 永久会员！\n所有高级功能已解锁。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 付款二维码
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.qr_wechat),
                                contentDescription = "微信收款码",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(142.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Text(
                            text = "微信扫一扫",
                            style = MaterialTheme.typography.labelMedium,
                            color = IosTextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Text(
                        text = "扫码付款后，将设备 ID 发给开发者获取专属激活码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosTextSecondary,
                        lineHeight = 20.sp
                    )

                    // 设备 ID 展示区
                    if (deviceId.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF2F2F7),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp, IosDividerColor
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "设备 ID",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IosTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = deviceId,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = IosTextPrimary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(deviceId))
                                        Toast.makeText(context, "已复制设备 ID", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "复制",
                                        modifier = Modifier.size(18.dp),
                                        tint = IosAccentBlue
                                    )
                                }
                            }
                        }
                    }

                    // 激活码输入
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { raw ->
                            val cleaned = raw.replace("-", "").uppercase().take(8)
                            codeInput = formatCode(cleaned)
                            errorMessage = null
                        },
                        label = { Text("输入激活码") },
                        placeholder = { Text("XXXX-XXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            letterSpacing = 2.sp,
                            color = IosTextPrimary,
                            fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IosAccentBlue,
                            cursorColor = IosAccentBlue,
                            focusedLabelColor = IosAccentBlue,
                            unfocusedLabelColor = IosTextSecondary,
                            focusedTextColor = IosTextPrimary,
                            unfocusedTextColor = IosTextPrimary,
                            focusedPlaceholderColor = IosTextSecondary,
                            unfocusedPlaceholderColor = IosTextSecondary
                        )
                    )

                    // 错误信息
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE53935),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showSuccess) {
                TextButton(onClick = onDismiss) {
                    Text("完成", color = IosAccentBlue, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isActivating) {
                        Text("取消", color = IosTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val code = codeInput.trim()

                            if (code.replace("-", "").length != 8) {
                                errorMessage = "请输入完整的 8 位激活码"
                                return@Button
                            }

                            isActivating = true
                            errorMessage = null

                            val result = onActivate(code)
                            isActivating = false

                            when (result) {
                                ActivationResult.SUCCESS -> showSuccess = true
                                ActivationResult.INVALID_CODE ->
                                    errorMessage = "激活码无效，请确认是否为本设备专属码"
                                ActivationResult.WRONG_DEVICE ->
                                    errorMessage = "此激活码非本设备专属码，请联系开发者"
                                ActivationResult.ALREADY_ACTIVATED ->
                                    showSuccess = true
                            }
                        },
                        enabled = !isActivating,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosAccentBlue
                        )
                    ) {
                        if (isActivating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("激活", color = Color.White)
                        }
                    }
                }
            }
        },
        dismissButton = {}
    )
}

/**
 * 将连续字符格式化为 XXXX-XXXX
 */
private fun formatCode(raw: String): String {
    if (raw.length <= 4) return raw
    return raw.take(4) + "-" + raw.substring(4)
}
