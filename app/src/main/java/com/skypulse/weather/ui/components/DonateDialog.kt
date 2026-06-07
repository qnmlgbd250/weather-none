package com.skypulse.weather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.R
import com.skypulse.weather.ui.theme.DialogInnerPanel
import com.skypulse.weather.ui.theme.DialogPanel
import com.skypulse.weather.ui.theme.DialogPanelBorder
import com.skypulse.weather.ui.theme.DialogTextPrimary
import com.skypulse.weather.ui.theme.DialogTextSecondary
import com.skypulse.weather.ui.theme.SecondaryAccent

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DonateDialog(
    onDismiss: () -> Unit
) {
    val donors = listOf(
        "芳华" to "66",
        "我有点柿" to "20",
        "三十九の度" to "18",
        "鸡神" to "8.8",
        "BIN0678" to "8.8",
        "微言" to "8.8",
        "*风" to "3"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, DialogPanelBorder, RoundedCornerShape(22.dp)),
        containerColor = DialogPanel,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "请作者喝杯咖啡",
                style = MaterialTheme.typography.titleSmall,
                color = DialogTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            .padding(10.dp)
                            .size(160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = "微信扫一扫",
                    style = MaterialTheme.typography.labelMedium,
                    color = DialogTextSecondary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DialogInnerPanel,
                    border = BorderStroke(1.dp, DialogPanelBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "打赏鸣谢",
                            style = MaterialTheme.typography.titleSmall,
                            color = DialogTextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            donors.forEachIndexed { index, (name, amount) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when (index) {
                                            0 -> Color(0xFFFFD700)
                                            1 -> Color(0xFFC0C0C0)
                                            2 -> Color(0xFFCD7F32)
                                            else -> DialogTextSecondary
                                        },
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = DialogTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "¥$amount",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = SecondaryAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = SecondaryAccent)
            }
        }
    )
}