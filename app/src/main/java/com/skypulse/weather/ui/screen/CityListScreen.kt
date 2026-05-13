package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.City
import com.skypulse.weather.ui.components.CityListRow
import com.skypulse.weather.ui.components.CitySearchResultRow
import com.skypulse.weather.ui.components.SwipeableCityListRow
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.viewmodel.CitySearchResult
import com.skypulse.weather.viewmodel.CityWeatherData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityListScreen(
    cities: List<City>,
    cityWeatherMap: Map<String, CityWeatherData>,
    searchResults: List<CitySearchResult>,
    isSearching: Boolean,
    onCityClick: (String) -> Unit,
    onAddCity: (CitySearchResult) -> Unit,
    onRemoveCity: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val isSearchActive = searchQuery.isNotBlank()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B2838)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar — always visible
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank()) {
                            onSearch(query)
                        } else {
                            onClearSearch()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    placeholder = {
                        Text(
                            text = "搜索并添加城市",
                            color = TextSecondary.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    onClearSearch()
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "清除",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                onSearch(searchQuery)
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            // Content: either search results or city list
            if (isSearchActive) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else if (searchResults.isEmpty()) {
                        item {
                            Text(
                                text = "未找到匹配的城市",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(searchResults) { result ->
                            CitySearchResultRow(
                                name = result.name,
                                district = result.district,
                                onClick = {
                                    onAddCity(result)
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                }
                            )
                            if (result != searchResults.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = Color.White.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = cities,
                        key = { it.id }
                    ) { city ->
                        val weatherData = cityWeatherMap[city.id]
                        SwipeableCityListRow(
                            city = city,
                            weather = weatherData?.weather,
                            isLoading = weatherData?.isLoading == true,
                            isCurrentLocation = city.isCurrentLocation,
                            onClick = { onCityClick(city.id) },
                            onDelete = { onRemoveCity(city.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
