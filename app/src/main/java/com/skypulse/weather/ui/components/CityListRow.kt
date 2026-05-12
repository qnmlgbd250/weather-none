package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils

@Composable
fun CityListRow(
    city: City,
    weather: WeatherResponse?,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val realtime = weather?.result?.realtime
    val todayTemp = weather?.result?.daily?.temperature?.firstOrNull()
    val skycon = realtime?.skycon
    val isDay = WeatherUtils.isCurrentlyDay()
    val gradientColors = WeatherUtils.getWeatherGradient(skycon, isDay)
    val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
    val temperature = WeatherUtils.formatTemperature(realtime?.temperature).replace("°", "")
    val high = WeatherUtils.formatTemperature(todayTemp?.max)
    val low = WeatherUtils.formatTemperature(todayTemp?.min)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        gradientColors[0].copy(alpha = 0.7f),
                        gradientColors[1].copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: city name + weather description
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (city.isCurrentLocation) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = city.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = weatherInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Right side: weather icon + temp + hi/lo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLoading && weather != null) {
                    WeatherIcon(
                        iconType = weatherInfo.icon,
                        size = 36.dp,
                        animated = false
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = TextSecondary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = temperature,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Thin
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "°",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Thin
                                ),
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$low / $high",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun CitySearchResultRow(
    name: String,
    district: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            if (district.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = district,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
