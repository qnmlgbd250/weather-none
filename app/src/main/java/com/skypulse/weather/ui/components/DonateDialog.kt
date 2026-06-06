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
import com.skypulse.weather.ui.theme.SecondaryAccent
import com.skypulse.weather.ui.theme.SecondaryTextPrimary
import com.skypulse.weather.ui.theme.SecondaryTextSecondary

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DonateDialog(
    onDismiss: () -> Unit
) {
    val donors = listOf(
        "\u82b3\u534e" to "66",
        "\u6211\u6709\u70b9\u67ff" to "20",
        "\u4e09\u5341\u4e5d\u306e\u5ea6" to "18",
        "\u9e21\u795e" to "8.8",
        "BIN0678" to "8.8",
        "*风" to "3"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, DialogPanelBorder, RoundedCornerShape(22.dp)),
        containerColor = DialogPanel,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "\u8bf7\u4f5c\u8005\u559d\u676f\u5496\u5561",
                style = MaterialTheme.typography.titleSmall,
                color = SecondaryTextPrimary
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
                        contentDescription = "\u5fae\u4fe1\u6536\u6b3e\u7801",
                        modifier = Modifier
                            .padding(10.dp)
                            .size(160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = "\u5fae\u4fe1\u626b\u4e00\u626b",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryTextSecondary
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
                            text = "\u6253\u8d4f\u9e23\u8c22",
                            style = MaterialTheme.typography.titleSmall,
                            color = SecondaryTextPrimary,
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
                                            else -> SecondaryTextSecondary
                                        },
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = SecondaryTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "\u00a5$amount",
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
                Text("\u5173\u95ed", color = SecondaryAccent)
            }
        }
    )
}
