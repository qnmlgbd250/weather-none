package com.skypulse.weather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils

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

    // 3x2 matrix:
    // 紫外线   体感温度   湿度
    // 风力     气压       能见度
    val items = listOf(
        Triple("紫外线", uvDesc, "U"),
        Triple("体感温度", feelsLike, "T"),
        Triple("湿度", humidity, "H"),
        Triple("风力", wind, "W"),
        Triple("气压", pressure, "P"),
        Triple("能见度", visibility, "V")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(3).forEach { (label, value, _) ->
                DetailSquareCard(label = label, value = value, modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.drop(3).forEach { (label, value, _) ->
                DetailSquareCard(label = label, value = value, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailSquareCard(
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
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