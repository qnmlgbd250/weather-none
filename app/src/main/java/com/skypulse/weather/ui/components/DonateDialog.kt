package com.skypulse.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.skypulse.weather.R
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DonateDialog(
    onDismiss: () -> Unit
) {
    val donors = listOf(
        "\u82b3\u534e" to "66",
        "\u6211\u6709\u70b9\u67ff" to "20",
        "\u4e09\u5341\u4e5d\u306e\u5ea6" to "18",
        "\u9e21\u795e" to "8.8"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "\u6350\u8d60\u652f\u6301",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "\u5982\u679c\u8fd9\u4e2a\u5e94\u7528\u5bf9\u4f60\u6709\u5e2e\u52a9\uff0c\u53ef\u4ee5\u8bf7\u4f5c\u8005\u559d\u676f\u5496\u5561",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Image(
                    painter = painterResource(id = R.drawable.qr_wechat),
                    contentDescription = "\u5fae\u4fe1\u6536\u6b3e\u7801",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "\u5fae\u4fe1\u626b\u4e00\u626b",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "\u6253\u8d4f\u9e23\u8c22",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        donors.forEach { (name, amount) ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(text = "$name\uff1a\u00a5$amount") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("\u5173\u95ed", color = TextSecondary)
            }
        }
    )
}