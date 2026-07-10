package com.skypulse.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTrustPolicyTest {
    @Test
    fun `rejects coarse fused first fix`() {
        assertFalse(LocationTrustPolicy.isStrongFirstFix("fused", 116.1f))
    }

    @Test
    fun `accepts accurate fused first fix`() {
        assertTrue(LocationTrustPolicy.isStrongFirstFix("fused", 45f))
    }

    @Test
    fun `allows gps first fix with slightly wider accuracy`() {
        assertTrue(LocationTrustPolicy.isStrongFirstFix("gps", 100f))
    }

    @Test
    fun `rejects cached accuracy above trusted fast path threshold`() {
        assertFalse(LocationTrustPolicy.isTrustedCachedAccuracy(116.1f))
    }

    @Test
    fun `does not trust missing accuracy as strong first fix`() {
        assertFalse(LocationTrustPolicy.isStrongFirstFix("fused", 0f))
    }
}
