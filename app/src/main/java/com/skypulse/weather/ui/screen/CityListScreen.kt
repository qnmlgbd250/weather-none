package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.defaultMinSize
import com.skypulse.weather.model.City
import com.skypulse.weather.ui.components.CitySearchResultRow
import com.skypulse.weather.ui.components.SwipeableCityListRow
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.viewmodel.CitySearchResult
import com.skypulse.weather.viewmodel.CityWeatherData
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityListScreen(
    cities: List<City>,
    cityWeatherMap: Map<String, CityWeatherData>,
    searchResults: List<CitySearchResult>,
    isSearching: Boolean,
    isSearchActive: Boolean,
    onCityClick: (String) -> Unit,
    onAddCity: (CitySearchResult) -> Unit,
    onRemoveCity: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SkyPulseDesignSystem.Colors.settingsBackground)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top nav bar
            if (onBack != null) {
                TopAppBar(
                    title = { Text("城市管理", color = IosTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                tint = IosBackArrow
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SkyPulseDesignSystem.Colors.settingsBackground
                    )
                )
            }

            // Search bar
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank()) onSearch(query)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SkyPulseDesignSystem.TouchTarget.default)
                        .clip(RoundedCornerShape(SkyPulseDesignSystem.Radius.pill))
                        .background(SkyPulseDesignSystem.Colors.settingsSurface),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = IosTextPrimary, fontSize = 18.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(IosAccentBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) onSearch(searchQuery)
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = IosTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索位置",
                                        color = IosTextSecondary,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp)
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        onClearSearch()
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "清除",
                                        tint = IosTextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Content
            if (isSearchActive) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = IosTextSecondary
                                )
                            }
                        }
                    } else if (searchResults.isEmpty()) {
                        item {
                            Text(
                                text = "未找到匹配的城市",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IosTextSecondary,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
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
                                    onClearSearch()
                                    focusManager.clearFocus()
                                }
                            )
                            if (result != searchResults.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = IosDividerColor
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal,
                        vertical = SkyPulseDesignSystem.Spacing.sectionGap
                    ),
                    verticalArrangement = Arrangement.spacedBy(SkyPulseDesignSystem.Spacing.contentGap)
                ) {
                    items(items = cities, key = { it.id }) { city ->
                        val weatherData = cityWeatherMap[city.id]
                        SwipeableCityListRow(
                            city = city,
                            weather = weatherData?.weather,
                            isCurrentLocation = city.isCurrentLocation,
                            onClick = { onCityClick(city.id) },
                            onDelete = { onRemoveCity(city.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}
