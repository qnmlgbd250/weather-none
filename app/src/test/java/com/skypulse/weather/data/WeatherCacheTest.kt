package com.skypulse.weather.data

import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WeatherCacheTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder().build()
    }

    @Test
    fun `moshi adapter can serialize and deserialize WeatherResponse`() {
        val adapter = moshi.adapter(WeatherResponse::class.java)
        val original = WeatherResponse(status = "ok")
        val json = adapter.toJson(original)
        val deserialized = adapter.fromJson(json)

        assertNotNull(deserialized)
        assertEquals("ok", deserialized?.status)
    }

    @Test
    fun `WeatherResponse handles null result gracefully`() {
        val adapter = moshi.adapter(WeatherResponse::class.java)
        val json = """{"status":"ok"}"""
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals("ok", response?.status)
        assertNull(response?.result)
    }
}
