package com.skypulse.weather.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshPolicyTest {

    @Test
    fun `nearby movement keeps cached weather fresh`() {
        val now = 60 * 60 * 1000L
        val lastFetch = now - 10 * 60 * 1000L

        val decision = WidgetRefreshPolicy.shouldFetchWeather(
            distanceMeters = 900f,
            lastFetchTimeMillis = lastFetch,
            nowMillis = now
        )

        assertFalse(decision)
    }

    @Test
    fun `significant movement refreshes weather immediately`() {
        val now = 60 * 60 * 1000L
        val lastFetch = now - 10 * 60 * 1000L

        val decision = WidgetRefreshPolicy.shouldFetchWeather(
            distanceMeters = 1_200f,
            lastFetchTimeMillis = lastFetch,
            nowMillis = now
        )

        assertTrue(decision)
    }

    @Test
    fun `stale same-place cache refreshes weather`() {
        val now = 60 * 60 * 1000L
        val lastFetch = now - 31 * 60 * 1000L

        val decision = WidgetRefreshPolicy.shouldFetchWeather(
            distanceMeters = 100f,
            lastFetchTimeMillis = lastFetch,
            nowMillis = now
        )

        assertTrue(decision)
    }
}
