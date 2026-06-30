package com.skypulse.weather.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.data.GeocodingService
import com.skypulse.weather.data.CityEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CitySearchResult(
    val name: String,
    val district: String,
    val longitude: Double,
    val latitude: Double
)

@HiltViewModel
class CitySearchViewModel @Inject constructor(
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<CitySearchResult>>(emptyList())
    val searchResults: StateFlow<List<CitySearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private var searchJob: Job? = null

    fun searchCities(query: String) {
        if (query.isBlank()) {
            Log.d("SearchVM", "searchCities called with BLANK query, ignoring")
            return
        }
        Log.d("SearchVM", "searchCities called: query='$query' isSearchActive=${_isSearchActive.value} hasResults=${_searchResults.value.size}")
        _isSearchActive.value = true
        _isSearching.value = true
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            Log.d("SearchVM", "debounce done, fetching results for '$query'")
            try {
                val entries: List<CityEntry> = geocodingService.search(query)
                Log.d("SearchVM", "search returned ${entries.size} results for '$query'")
                _searchResults.value = entries.map { entry ->
                    CitySearchResult(
                        name = entry.name,
                        district = entry.province,
                        longitude = entry.lon,
                        latitude = entry.lat
                    )
                }
            } catch (e: Exception) {
                Log.w("SearchVM", "search failed for '$query'", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        Log.d("SearchVM", "clearSearchResults called (wasActive=${_isSearchActive.value} results=${_searchResults.value.size})")
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
        _isSearchActive.value = false
    }
}
