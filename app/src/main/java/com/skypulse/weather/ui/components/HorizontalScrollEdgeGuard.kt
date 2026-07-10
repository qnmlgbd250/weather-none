package com.skypulse.weather.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

val LocalCityPagerScrollEnabled = compositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
internal fun rememberHorizontalScrollEdgeGuard(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(x = available.x, y = 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity(x = available.x, y = 0f)
            }
        }
    }
}

@Composable
internal fun Modifier.disableCityPagerWhilePressed(): Modifier {
    val setCityPagerScrollEnabled = LocalCityPagerScrollEnabled.current

    DisposableEffect(setCityPagerScrollEnabled) {
        onDispose { setCityPagerScrollEnabled(true) }
    }

    return pointerInput(setCityPagerScrollEnabled) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            setCityPagerScrollEnabled(false)
            try {
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
            } finally {
                setCityPagerScrollEnabled(true)
            }
        }
    }
}
