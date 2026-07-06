package com.skypulse.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationJumpGuardTest {
    @Test
    fun `holds low confidence long jump for confirmation`() {
        val hold = LocationJumpGuard.shouldHoldForConfirmation(
            distanceFromCachedMeters = 3_000f,
            newAccuracyMeters = 600f,
            cacheAgeMillis = 10 * 60 * 1000L,
            highAccuracy = false
        )

        assertTrue(hold)
    }

    @Test
    fun `does not hold high accuracy manual relocation`() {
        val hold = LocationJumpGuard.shouldHoldForConfirmation(
            distanceFromCachedMeters = 3_000f,
            newAccuracyMeters = 600f,
            cacheAgeMillis = 10 * 60 * 1000L,
            highAccuracy = true
        )

        assertFalse(hold)
    }

    @Test
    fun `does not hold accurate long distance update`() {
        val hold = LocationJumpGuard.shouldHoldForConfirmation(
            distanceFromCachedMeters = 3_000f,
            newAccuracyMeters = 60f,
            cacheAgeMillis = 10 * 60 * 1000L,
            highAccuracy = false
        )

        assertFalse(hold)
    }

    @Test
    fun `does not hold small walking movement`() {
        val hold = LocationJumpGuard.shouldHoldForConfirmation(
            distanceFromCachedMeters = 800f,
            newAccuracyMeters = 600f,
            cacheAgeMillis = 10 * 60 * 1000L,
            highAccuracy = false
        )

        assertFalse(hold)
    }

    @Test
    fun `does not protect stale cache forever`() {
        val hold = LocationJumpGuard.shouldHoldForConfirmation(
            distanceFromCachedMeters = 3_000f,
            newAccuracyMeters = 600f,
            cacheAgeMillis = 31 * 60 * 1000L,
            highAccuracy = false
        )

        assertFalse(hold)
    }

    @Test
    fun `confirms pending candidate when next point is nearby`() {
        assertTrue(
            LocationJumpGuard.isConfirmedByPending(
                distanceFromPendingMeters = 300f,
                pendingAgeMillis = 5 * 60 * 1000L
            )
        )
    }

    @Test
    fun `does not confirm expired pending candidate`() {
        assertFalse(
            LocationJumpGuard.isConfirmedByPending(
                distanceFromPendingMeters = 300f,
                pendingAgeMillis = LocationJumpGuard.PENDING_TTL_MS + 1L
            )
        )
    }
}
