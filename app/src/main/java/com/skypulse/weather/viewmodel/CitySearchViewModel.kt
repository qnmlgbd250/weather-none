package com.skypulse.weather.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.data.CityDatabase
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
    private val cityDatabase: CityDatabase
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<CitySearchResult>>(emptyList())
    val searchResults: StateFlow<List<CitySearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun searchCities(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            try {
                val entries: List<CityEntry> = cityDatabase.search(query)
                _searchResults.value = entries.map { entry ->
                    CitySearchResult(
                        name = entry.name,
                        district = entry.province,
                        longitude = entry.lon,
                        latitude = entry.lat
                    )
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }
}
