package com.skypulse.weather.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun WeatherIcon(
    iconType: String,
    size: Dp = 80.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("meteocons/fill/$iconType.json")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (animated) LottieConstants.IterateForever else 1
    )
    LottieAnimation(
        composition = composition,
        progress = { if (animated) progress else 0.5f },
        modifier = modifier.size(size)
    )
}