package com.skypulse.weather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.R
import com.skypulse.weather.ui.theme.IosCardBg
import com.skypulse.weather.ui.theme.IosTextPrimary
import com.skypulse.weather.ui.theme.IosTextSecondary
import com.skypulse.weather.ui.theme.IosDividerColor
import com.skypulse.weather.ui.theme.IosAccentBlue

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DonateDialog(
    onDismiss: () -> Unit
) {
    val donorScrollState = rememberScrollState()
    val donors = listOf(
        "芳华" to "66",
        "维涅斯" to "50",
        "我有点柿" to "20",
        "*酮" to "20",
        "小熊尼克队长" to "18.88",
        "三十九の度" to "18",
        "叁拾而已" to "15",
        "西瓜catcat" to "12.8",
        "丸子面" to "10",
        "鸡神" to "8.8",
        "BIN0678" to "8.8",
        "微言" to "8.8",
        "c*l" to "6.66",
        "*岭" to "4",
        "*年" to "3.88",
        "*风" to "3"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier,
        containerColor = IosCardBg,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                text = "请作者喝杯咖啡",
                style = MaterialTheme.typography.titleSmall,
                color = IosTextPrimary
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
                    color = IosTextSecondary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF2F2F7),
                    border = BorderStroke(0.5.dp, IosDividerColor)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "打赏鸣谢",
                            style = MaterialTheme.typography.titleSmall,
                            color = IosTextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 10.dp)
                                    .verticalScroll(donorScrollState)
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
                                                else -> IosTextSecondary
                                            },
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                            color = IosTextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "¥$amount",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                            color = IosAccentBlue
                                        )
                                    }
                                }
                            }

                            DonorScrollIndicator(
                                scrollState = donorScrollState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(3.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = IosAccentBlue)
            }
        }
    )
}

@Composable
private fun DonorScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    if (scrollState.maxValue <= 0) return

    Canvas(modifier = modifier) {
        val trackWidth = size.width.coerceAtMost(3.dp.toPx())
        val trackLeft = (size.width - trackWidth) / 2f
        val viewportHeight = size.height
        val contentHeight = viewportHeight + scrollState.maxValue
        val thumbHeight = (viewportHeight * viewportHeight / contentHeight)
            .coerceIn(24.dp.toPx(), viewportHeight)
        val thumbTop = if (scrollState.maxValue > 0) {
            (scrollState.value.toFloat() / scrollState.maxValue) * (viewportHeight - thumbHeight)
        } else {
            0f
        }

        drawRoundRect(
            color = IosTextSecondary.copy(alpha = 0.16f),
            topLeft = Offset(trackLeft, 0f),
            size = Size(trackWidth, viewportHeight),
            cornerRadius = CornerRadius(trackWidth / 2f)
        )
        drawRoundRect(
            color = IosAccentBlue.copy(alpha = 0.62f),
            topLeft = Offset(trackLeft, thumbTop),
            size = Size(trackWidth, thumbHeight),
            cornerRadius = CornerRadius(trackWidth / 2f)
        )
    }
}
