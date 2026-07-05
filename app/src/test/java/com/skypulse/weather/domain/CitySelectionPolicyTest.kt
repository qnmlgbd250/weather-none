package com.skypulse.weather.domain

import com.skypulse.weather.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CitySelectionPolicyTest {

    private val gps = City("current_location", "当前位置", 116.4, 39.9, isCurrentLocation = true)
    private val shanghai = City("shanghai", "上海", 121.4, 31.2)
    private val guangzhou = City("guangzhou", "广州", 113.2, 23.1)

    @Test
    fun `default city prefers current location`() {
        assertEquals(gps, CitySelectionPolicy.defaultCity(listOf(shanghai, gps)))
    }

    @Test
    fun `default city falls back to first city`() {
        assertEquals(shanghai, CitySelectionPolicy.defaultCity(listOf(shanghai, guangzhou)))
    }

    @Test
    fun `current index uses current location when selected city is null`() {
        assertEquals(1, CitySelectionPolicy.currentIndex(listOf(shanghai, gps, guangzhou), null))
    }

    @Test
    fun `current index falls back to zero for missing selected city`() {
        assertEquals(0, CitySelectionPolicy.currentIndex(listOf(shanghai, guangzhou), "missing"))
    }

    @Test
    fun `next city preserves old fallback when no selected city and no current location`() {
        assertEquals(shanghai, CitySelectionPolicy.nextCity(listOf(shanghai, guangzhou), null))
    }

    @Test
    fun `previous city preserves old fallback when no selected city and no current location`() {
        assertEquals(shanghai, CitySelectionPolicy.previousCity(listOf(shanghai, guangzhou), null))
    }

    @Test
    fun `next and previous city wrap around selected city`() {
        val cities = listOf(gps, shanghai, guangzhou)

        assertEquals(guangzhou, CitySelectionPolicy.nextCity(cities, "shanghai"))
        assertEquals(gps, CitySelectionPolicy.previousCity(cities, "shanghai"))
    }

    @Test
    fun `next and previous return null for one city`() {
        assertNull(CitySelectionPolicy.nextCity(listOf(gps), gps.id))
        assertNull(CitySelectionPolicy.previousCity(listOf(gps), gps.id))
    }
}
