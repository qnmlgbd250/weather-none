package com.skypulse.weather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils

private data class DetailItem(
    val label: String,
    val value: String,
    val icon: ImageVector
)

@Composable
fun WeatherDetailCards(
    realtime: RealtimeWeather?,
    modifier: Modifier = Modifier
) {
    val feelsLike = WeatherUtils.formatTemperature(realtime?.apparent_temperature)
    val windSpeed = WeatherUtils.formatWindSpeed(realtime?.wind?.speed)
    val windDir = WeatherUtils.formatWindDirection(realtime?.wind?.direction)
    val wind = "$windDir$windSpeed"
    val humidity = WeatherUtils.formatHumidity(realtime?.humidity)
    val uvDesc = realtime?.life_index?.ultraviolet?.desc ?: "--"
    val pressure = WeatherUtils.formatPressure(realtime?.pressure)
    val visibility = WeatherUtils.formatVisibility(realtime?.visibility)

    val items = listOf(
        DetailItem("紫外线", uvDesc, Icons.Outlined.WbSunny),
        DetailItem("体感温度", feelsLike, Icons.Outlined.Thermostat),
        DetailItem("湿度", humidity, Icons.Outlined.WaterDrop),
        DetailItem("风力", wind, Icons.Outlined.Air),
        DetailItem("气压", pressure, Icons.Outlined.Speed),
        DetailItem("能见度", visibility, Icons.Outlined.Visibility)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(3).forEach { item ->
                DetailSquareCard(
                    icon = item.icon,
                    label = item.label,
                    value = item.value,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.drop(3).forEach { item ->
                DetailSquareCard(
                    icon = item.icon,
                    label = item.label,
                    value = item.value,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DetailSquareCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalWeatherTheme.current

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(theme.cardTintColor)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        theme.cardFrostColor.copy(alpha = theme.cardTopAlpha),
                        theme.cardFrostColor.copy(alpha = theme.cardBottomAlpha)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, theme.cardBorderColor),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = TextPrimary
            )
        }
    }
}
