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
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils

private data class DetailItem(
    val label: String,
    val value: String,
    val unit: String,
    val icon: ImageVector
)

@Composable
fun WeatherDetailCards(
    realtime: RealtimeWeather?,
    modifier: Modifier = Modifier
) {
    val feelsLike = WeatherUtils.formatTemperature(realtime?.apparent_temperature)
    val windSpeedLevel = WeatherUtils.formatWindSpeed(realtime?.wind?.speed)
    val windDir = WeatherUtils.formatWindDirection(realtime?.wind?.direction)
    val humidity = WeatherUtils.formatHumidity(realtime?.humidity)

    val uvIndex = realtime?.life_index?.ultraviolet?.index ?: "--"
    val uvDesc = realtime?.life_index?.ultraviolet?.desc ?: ""

    val pressureRaw = realtime?.pressure
    val pressureValue = if (pressureRaw != null) "${(pressureRaw / 100).toInt()}" else "--"
    val pressureUnit = "\u767e\u5e15"

    val visRaw = realtime?.visibility
    val visValue = if (visRaw != null) {
        if (visRaw >= 1000) "${"%.1f".format(visRaw / 1000)}" else "${visRaw.toInt()}"
    } else "--"
    val visUnit = "\u5343\u7c73"

    val windLevelNum = windSpeedLevel.replace("\u7ea7", "")

    val items = listOf(
        DetailItem("\u7d2b\u5916\u7ebf", uvIndex, uvDesc, Icons.Outlined.WbSunny),
        DetailItem("\u4f53\u611f\u6e29\u5ea6", feelsLike, "", Icons.Outlined.Thermostat),
        DetailItem("\u6e7f\u5ea6", humidity, "", Icons.Outlined.WaterDrop),
        DetailItem(windDir, windLevelNum, "\u7ea7", Icons.Outlined.Air),
        DetailItem("\u6c14\u538b", pressureValue, pressureUnit, Icons.Outlined.Speed),
        DetailItem("\u80fd\u89c1\u5ea6", visValue, visUnit, Icons.Outlined.Visibility)
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
                    unit = item.unit,
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
                    unit = item.unit,
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
    unit: String = "",
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
            if (unit.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextPrimary,
                        modifier = Modifier.alignByBaseline()
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp
                        ),
                        color = TextSecondary,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            } else {
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
}