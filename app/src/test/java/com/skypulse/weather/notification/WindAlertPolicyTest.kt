package com.skypulse.weather.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindAlertPolicyTest {
    @Test
    fun `does not alert when wind and gust are below threshold`() {
        assertNull(WindAlertPolicy.evaluate(windSpeed = 38.8, gustSpeed = 38.8))
    }

    @Test
    fun `alerts from regular wind when wind reaches threshold`() {
        val result = WindAlertPolicy.evaluate(windSpeed = 38.9, gustSpeed = null)

        assertEquals("5级", result?.level)
        assertEquals(38.9, result?.speed ?: 0.0, 0.0)
        assertFalse(result?.isGust ?: true)
    }

    @Test
    fun `alerts from gust when only gust reaches threshold`() {
        val result = WindAlertPolicy.evaluate(windSpeed = 20.0, gustSpeed = 50.0)

        assertEquals("6级", result?.level)
        assertEquals(50.0, result?.speed ?: 0.0, 0.0)
        assertTrue(result?.isGust ?: false)
    }

    @Test
    fun `uses stronger gust when both wind and gust reach threshold`() {
        val result = WindAlertPolicy.evaluate(windSpeed = 50.0, gustSpeed = 74.9)

        assertEquals("8级", result?.level)
        assertEquals(74.9, result?.speed ?: 0.0, 0.0)
        assertTrue(result?.isGust ?: false)
    }
}
