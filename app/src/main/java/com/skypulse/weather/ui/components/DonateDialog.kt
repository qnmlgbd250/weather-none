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
        "芳华" to "66",
        "鸡祖" to "8.8"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "捐赠支持",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "如果这个应用对你有帮助，可以请作者喝杯咖啡",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Image(
                    painter = painterResource(id = R.drawable.qr_wechat),
                    contentDescription = "微信收款码",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "微信扫一扫",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "打赏鸣谢",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        donors.take(10).forEach { (name, amount) ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(text = "$name：￥$amount") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}
