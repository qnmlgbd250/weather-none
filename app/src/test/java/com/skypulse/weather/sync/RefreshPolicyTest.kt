package com.skypulse.weather.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshPolicyTest {

    @Test
    fun `global sync is rate limited inside 120 seconds`() {
        val lastSuccess = 1_000L

        assertTrue(
            RefreshPolicy.isGlobalRateLimited(
                nowMillis = lastSuccess + RefreshPolicy.GLOBAL_SYNC_INTERVAL_MS - 1,
                lastSuccessMillis = lastSuccess,
                force = false
            )
        )
    }

    @Test
    fun `global sync is allowed at 120 seconds`() {
        val lastSuccess = 1_000L

        assertFalse(
            RefreshPolicy.isGlobalRateLimited(
                nowMillis = lastSuccess + RefreshPolicy.GLOBAL_SYNC_INTERVAL_MS,
                lastSuccessMillis = lastSuccess,
                force = false
            )
        )
    }

    @Test
    fun `force bypasses global rate limit`() {
        assertFalse(
            RefreshPolicy.isGlobalRateLimited(
                nowMillis = 1_500L,
                lastSuccessMillis = 1_000L,
                force = true
            )
        )
    }

    @Test
    fun `fresh cache is skipped only when not forced`() {
        assertTrue(RefreshPolicy.shouldSkipFreshCache(isCacheStale = false, force = false))
        assertFalse(RefreshPolicy.shouldSkipFreshCache(isCacheStale = false, force = true))
        assertFalse(RefreshPolicy.shouldSkipFreshCache(isCacheStale = true, force = false))
    }

    @Test
    fun `city refresh is rate limited inside city window`() {
        val lastFetch = 2_000L

        assertTrue(
            RefreshPolicy.isCityRateLimited(
                nowMillis = lastFetch + RefreshPolicy.CITY_RATE_LIMIT_MS - 1,
                lastFetchMillis = lastFetch
            )
        )
        assertFalse(
            RefreshPolicy.isCityRateLimited(
                nowMillis = lastFetch + RefreshPolicy.CITY_RATE_LIMIT_MS,
                lastFetchMillis = lastFetch
            )
        )
        assertFalse(RefreshPolicy.isCityRateLimited(nowMillis = 2_000L, lastFetchMillis = null))
    }

    @Test
    fun `same coordinate dedupe only applies inside five seconds`() {
        val lastFetch = 3_000L

        assertTrue(
            RefreshPolicy.isSameCoordinateDedupeWindow(
                nowMillis = lastFetch + RefreshPolicy.SAME_COORDINATE_DEDUPE_MS - 1,
                lastFetchMillis = lastFetch
            )
        )
        assertFalse(
            RefreshPolicy.isSameCoordinateDedupeWindow(
                nowMillis = lastFetch + RefreshPolicy.SAME_COORDINATE_DEDUPE_MS,
                lastFetchMillis = lastFetch
            )
        )
    }

    @Test
    fun `policy constants preserve previous timings`() {
        assertEquals(120_000L, RefreshPolicy.GLOBAL_SYNC_INTERVAL_MS)
        assertEquals(5 * 60 * 1000L, RefreshPolicy.WEATHER_TTL_MS)
        assertEquals(120_000L, RefreshPolicy.CITY_RATE_LIMIT_MS)
        assertEquals(5_000L, RefreshPolicy.SAME_COORDINATE_DEDUPE_MS)
        assertEquals(25 * 60 * 1000L, RefreshPolicy.NOTIFICATION_CACHE_TTL_MS)
        assertEquals(8 * 60 * 1000L, RefreshPolicy.URGENT_NOTIFICATION_CACHE_TTL_MS)
    }
}
