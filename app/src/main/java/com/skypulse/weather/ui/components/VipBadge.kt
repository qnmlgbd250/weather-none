package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// VIP 金色渐变色
private val VipGoldStart = Color(0xFFFFD700)
private val VipGoldEnd = Color(0xFFFFA500)
private val VipGoldMid = Color(0xFFFFC125)
private val VipTextDark = Color(0xFF7A5A00)

/**
 * VIP 永久会员勋章 — 金色渐变胶囊
 */
@Composable
fun VipBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(VipGoldStart, VipGoldMid, VipGoldEnd)
                )
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = VipTextDark
        )
        Text(
            text = "永久会员",
            style = MaterialTheme.typography.labelMedium,
            color = VipTextDark,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

/**
 * VIP 设置卡片 — 展示会员状态和激活信息
 */
@Composable
fun VipStatusCard(
    activatedAt: Long,
    modifier: Modifier = Modifier
) {
    val dateStr = rememberFormattedDate(activatedAt)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        VipGoldStart.copy(alpha = 0.15f),
                        VipGoldMid.copy(alpha = 0.10f),
                        VipGoldEnd.copy(alpha = 0.15f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = VipGoldMid
            )
            Column {
                Text(
                    text = "SkyPulse 永久会员",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF7A5A00),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "激活于 $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A7A20),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun rememberFormattedDate(timestamp: Long): String {
    return if (timestamp > 0) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        "$y 年 $m 月 $d 日"
    } else {
        "未知"
    }
}
