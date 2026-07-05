package com.skypulse.weather.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherNavigationStateTest {

    @Test
    fun `initial state opens city detail without selected city`() {
        val state = WeatherNavigationState()

        assertEquals(AppScreen.CityDetail, state.currentScreen.value)
        assertNull(state.selectedCityId.value)
        assertEquals(0, state.selectedAlertIndex.value)
        assertEquals(1, state.swipeDirection.value)
    }

    @Test
    fun `show city detail can update selected city`() {
        val state = WeatherNavigationState()

        state.showCityDetail("beijing")

        assertEquals(AppScreen.CityDetail, state.currentScreen.value)
        assertEquals("beijing", state.selectedCityId.value)
    }

    @Test
    fun `show city detail keeps existing city when no city is provided`() {
        val state = WeatherNavigationState()

        state.selectCity("beijing")
        state.showSettings()
        state.showCityDetail()

        assertEquals(AppScreen.CityDetail, state.currentScreen.value)
        assertEquals("beijing", state.selectedCityId.value)
    }

    @Test
    fun `show alert detail stores selected alert index`() {
        val state = WeatherNavigationState()

        state.showAlertDetail(2)

        assertEquals(AppScreen.AlertDetail, state.currentScreen.value)
        assertEquals(2, state.selectedAlertIndex.value)
    }

    @Test
    fun `swipe markers preserve previous direction contract`() {
        val state = WeatherNavigationState()

        state.markPreviousSwipe()
        assertEquals(-1, state.swipeDirection.value)

        state.markNextSwipe()
        assertEquals(1, state.swipeDirection.value)
    }
}
