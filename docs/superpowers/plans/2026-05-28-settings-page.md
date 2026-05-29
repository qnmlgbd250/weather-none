# Settings Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a settings/about page with GitHub link, donation QR codes, update checking, and version info, accessible via a MoreVert icon in the weather detail header.

**Architecture:** Add `AppScreen.Settings` to the existing enum-based navigation. Create `SettingsScreen.kt` and `DonateDialog.kt` composables. The `LocationHeader` gets a second icon button (MoreVert) that triggers navigation. Update checking uses OkHttp to call GitHub Releases API.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, OkHttp (existing), Chrome Custom Tabs (new dependency)

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `gradle/libs.versions.toml` | Add browser dependency |
| Modify | `app/build.gradle.kts` | Add browser dependency |
| Modify | `viewmodel/WeatherViewModel.kt:56-58` | Add `Settings` to `AppScreen`, add `navigateToSettings()`, `navigateBack()`, `checkForUpdates()` |
| Modify | `ui/screen/WeatherScreen.kt:99-112` | Add `Settings` case to `when(currentScreen)` |
| Modify | `ui/screen/WeatherScreen.kt:173-195` | Pass `onSettingsClick` to `WeatherContent` → `LocationHeader` |
| Modify | `ui/components/CurrentWeather.kt:38-44` | Add `onSettingsClick` param to `LocationHeader` |
| Modify | `ui/components/CurrentWeather.kt:109-121` | Add MoreVert icon button next to Menu icon |
| Create | `ui/screen/SettingsScreen.kt` | Settings page composable |
| Create | `ui/components/DonateDialog.kt` | QR code donation dialog |

---

### Task 1: Add Browser Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add browser version and library to version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
browser = "1.7.0"
```

Add under `[libraries]`:
```toml
browser = { group = "androidx.browser", name = "browser", version.ref = "browser" }
```

- [ ] **Step 2: Add browser dependency to build.gradle.kts**

In `app/build.gradle.kts`, add after the `lottie-compose` line (line ~112):
```kotlin
implementation(libs.browser)
```

- [ ] **Step 3: Sync Gradle to verify**

Run: `cd C:/Users/phil/weather-none && ./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | head -5`
Expected: Build succeeds (no errors about missing browser dependency)

---

### Task 2: Extend AppScreen Navigation

**Files:**
- Modify: `app/src/main/java/com/skypulse/weather/viewmodel/WeatherViewModel.kt:56-58`

- [ ] **Step 1: Add Settings to AppScreen enum**

In `WeatherViewModel.kt`, change line 56-58 from:
```kotlin
enum class AppScreen {
    CityList, CityDetail
}
```
to:
```kotlin
enum class AppScreen {
    CityList, CityDetail, Settings
}
```

- [ ] **Step 2: Add navigation and update-check methods to WeatherViewModel**

In `WeatherViewModel.kt`, add after the `navigateToCityDetail` method (after line 184):
```kotlin
fun navigateToSettings() {
    _currentScreen.value = AppScreen.Settings
}

fun navigateBack() {
    // Go back from Settings to CityDetail
    if (_currentScreen.value == AppScreen.Settings) {
        _currentScreen.value = AppScreen.CityDetail
    }
}

private val _updateState = MutableStateFlow<UpdateCheckResult?>(null)
val updateState: StateFlow<UpdateCheckResult?> = _updateState.asStateFlow()

fun checkForUpdates() {
    viewModelScope.launch {
        _updateState.value = UpdateCheckResult.Checking
        try {
            val result = withContext(Dispatchers.IO) {
                val url = java.net.URL("https://api.github.com/repos/user/skypulse/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val body = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                body
            }
            val json = org.json.JSONObject(result)
            val tagName = json.getString("tag_name").removePrefix("v")
            val current = BuildConfig.VERSION_NAME
            if (isNewerVersion(tagName, current)) {
                val htmlUrl = json.getString("html_url")
                _updateState.value = UpdateCheckResult.UpdateAvailable(tagName, htmlUrl)
            } else {
                _updateState.value = UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            _updateState.value = UpdateCheckResult.Error("检查更新失败，请稍后重试")
        }
    }
}

fun clearUpdateState() {
    _updateState.value = null
}

private fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

sealed class UpdateCheckResult {
    data object Checking : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val version: String, val url: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
```

Also add `org.json.JSONObject` import at the top of the file (it's part of Android SDK, no extra dependency needed).

- [ ] **Step 3: Verify compilation**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 3: Add MoreVert Icon to LocationHeader

**Files:**
- Modify: `app/src/main/java/com/skypulse/weather/ui/components/CurrentWeather.kt:38-44,109-121`

- [ ] **Step 1: Add onSettingsClick parameter to LocationHeader**

In `CurrentWeather.kt`, change the `LocationHeader` signature (lines 38-44) from:
```kotlin
fun LocationHeader(
    locationName: String,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: (() -> Unit)? = null,
    onListClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```
to:
```kotlin
fun LocationHeader(
    locationName: String,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: (() -> Unit)? = null,
    onListClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

- [ ] **Step 2: Add MoreVert import**

In `CurrentWeather.kt`, add this import after the existing material icons imports (around line 15):
```kotlin
import androidx.compose.material.icons.outlined.MoreVert
```

- [ ] **Step 3: Add MoreVert icon button next to Menu icon**

In `CurrentWeather.kt`, change lines 109-121 from:
```kotlin
            if (onListClick != null) {
                IconButton(
                    onClick = onListClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "城市列表",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
```
to:
```kotlin
            Row {
                if (onListClick != null) {
                    IconButton(
                        onClick = onListClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "城市列表",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (onSettingsClick != null) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "设置",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
```

- [ ] **Step 4: Verify compilation**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 4: Wire Navigation in WeatherScreen

**Files:**
- Modify: `app/src/main/java/com/skypulse/weather/ui/screen/WeatherScreen.kt`

- [ ] **Step 1: Add Settings case to the when block**

In `WeatherScreen.kt`, after the `AppScreen.CityDetail` block (after line 168, before the closing `}` of the `when`), add:
```kotlin
            AppScreen.Settings -> {
                SettingsScreen(
                    onBack = { viewModel.navigateBack() },
                    onCheckUpdate = { viewModel.checkForUpdates() },
                    updateState = updateState,
                    onClearUpdateState = { viewModel.clearUpdateState() }
                )
            }
```

- [ ] **Step 2: Collect updateState in WeatherScreen**

In `WeatherScreen.kt`, add after line 41 (`val isSearching by viewModel.isSearching.collectAsState()`):
```kotlin
    val updateState by viewModel.updateState.collectAsState()
```

- [ ] **Step 3: Add SettingsScreen import**

In `WeatherScreen.kt`, add this import (it will be created in Task 5):
```kotlin
import com.skypulse.weather.ui.screen.SettingsScreen
```
Note: This import will resolve after Task 5 creates the file.

- [ ] **Step 4: Pass onSettingsClick to WeatherContent**

In `WeatherScreen.kt`, change the `WeatherContent` call (lines 127-133) from:
```kotlin
WeatherContent(
    state = state,
    isLocating = isLocating,
    refreshPhase = refreshPhase,
    onLocationClick = { viewModel.relocateAndRefresh() },
    onRefresh = { viewModel.refresh() },
    onListClick = { viewModel.navigateToCityList() }
)
```
to:
```kotlin
WeatherContent(
    state = state,
    isLocating = isLocating,
    refreshPhase = refreshPhase,
    onLocationClick = { viewModel.relocateAndRefresh() },
    onRefresh = { viewModel.refresh() },
    onListClick = { viewModel.navigateToCityList() },
    onSettingsClick = { viewModel.navigateToSettings() }
)
```

- [ ] **Step 5: Add onSettingsClick parameter to WeatherContent**

In `WeatherScreen.kt`, change the `WeatherContent` signature (lines 174-181) from:
```kotlin
private fun WeatherContent(
    state: WeatherUiState.Success,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onListClick: () -> Unit = {}
)
```
to:
```kotlin
private fun WeatherContent(
    state: WeatherUiState.Success,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
)
```

- [ ] **Step 6: Pass onSettingsClick to LocationHeader**

In `WeatherScreen.kt`, change the `LocationHeader` call (lines 189-195) from:
```kotlin
LocationHeader(
    locationName = state.locationName,
    isLocating = isLocating,
    refreshPhase = refreshPhase,
    onLocationClick = onLocationClick,
    onListClick = onListClick
)
```
to:
```kotlin
LocationHeader(
    locationName = state.locationName,
    isLocating = isLocating,
    refreshPhase = refreshPhase,
    onLocationClick = onLocationClick,
    onListClick = onListClick,
    onSettingsClick = onSettingsClick
)
```

- [ ] **Step 7: Verify compilation (will fail until Task 5)**

Skip — this will compile after Task 5 creates `SettingsScreen.kt`.

---

### Task 5: Create SettingsScreen

**Files:**
- Create: `app/src/main/java/com/skypulse/weather/ui/screen/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsScreen.kt**

```kotlin
package com.skypulse.weather.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.ui.components.DonateDialog
import com.skypulse.weather.ui.theme.NightFallbackGradient
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.viewmodel.UpdateCheckResult

private const val GITHUB_URL = "https://github.com/user/skypulse"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateState: UpdateCheckResult?,
    onClearUpdateState: () -> Unit
) {
    val context = LocalContext.current
    var showDonateDialog by remember { mutableStateOf(false) }

    // Handle update check results
    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateCheckResult.UpToDate -> {
                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                onClearUpdateState()
            }
            is UpdateCheckResult.Error -> {
                Toast.makeText(context, updateState.message, Toast.LENGTH_SHORT).show()
                onClearUpdateState()
            }
            else -> {}
        }
    }

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = NightFallbackGradient))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = { Text("设置", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // GitHub 仓库
                SettingsItem(
                    icon = Icons.Outlined.OpenInBrowser,
                    title = "GitHub 仓库",
                    subtitle = "查看项目源码",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, GITHUB_URL.toUri())
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = TextSecondary.copy(alpha = 0.1f)
                )

                // 打赏作者
                SettingsItem(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "打赏作者",
                    subtitle = "请作者喝杯咖啡",
                    onClick = { showDonateDialog = true }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = TextSecondary.copy(alpha = 0.1f)
                )

                // 检查更新
                SettingsItem(
                    icon = Icons.Outlined.Refresh,
                    title = "检查更新",
                    subtitle = when (updateState) {
                        is UpdateCheckResult.Checking -> "正在检查..."
                        is UpdateCheckResult.UpdateAvailable -> "发现新版本 v${updateState.version}"
                        else -> "当前版本 v${BuildConfig.VERSION_NAME}"
                    },
                    onClick = {
                        if (updateState !is UpdateCheckResult.Checking) {
                            onCheckUpdate()
                        }
                    }
                )

                // Update available action
                if (updateState is UpdateCheckResult.UpdateAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, updateState.url.toUri())
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("前往下载 v${updateState.version}")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Version footer
                Text(
                    text = "SkyPulse v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}
```

- [ ] **Step 2: Verify compilation (will fail until Task 6)**

Skip — this will compile after Task 6 creates `DonateDialog.kt`.

---

### Task 6: Create DonateDialog

**Files:**
- Create: `app/src/main/java/com/skypulse/weather/ui/components/DonateDialog.kt`

- [ ] **Step 1: Create DonateDialog.kt**

```kotlin
package com.skypulse.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.skypulse.weather.R
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary

@Composable
fun DonateDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "打赏作者",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "如果这个应用对你有帮助，可以请作者喝杯咖啡 ☕",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.qr_alipay),
                            contentDescription = "支付宝收款码",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "支付宝",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.qr_wechat),
                            contentDescription = "微信收款码",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "微信",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}
```

- [ ] **Step 2: Create placeholder QR code drawable resources**

Create `app/src/main/res/drawable/qr_alipay.xml` (placeholder vector):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="200dp"
    android:height="200dp"
    android:viewportWidth="200"
    android:viewportHeight="200">
    <path
        android:fillColor="#CCCCCC"
        android:pathData="M0,0h200v200H0z"/>
    <path
        android:fillColor="#999999"
        android:pathData="M60,80h80v40H60z"/>
</vector>
```

Create `app/src/main/res/drawable/qr_wechat.xml` (placeholder vector):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="200dp"
    android:height="200dp"
    android:viewportWidth="200"
    android:viewportHeight="200">
    <path
        android:fillColor="#CCCCCC"
        android:pathData="M0,0h200v200H0z"/>
    <path
        android:fillColor="#999999"
        android:pathData="M60,80h80v40H60z"/>
</vector>
```

Note: These are placeholders. Replace with actual QR code images later.

- [ ] **Step 3: Verify full compilation**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

---

### Task 7: Final Build Verification

- [ ] **Step 1: Run full debug build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit all changes**

```bash
cd C:/Users/phil/weather-none
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/skypulse/weather/viewmodel/WeatherViewModel.kt \
  app/src/main/java/com/skypulse/weather/ui/screen/WeatherScreen.kt \
  app/src/main/java/com/skypulse/weather/ui/components/CurrentWeather.kt \
  app/src/main/java/com/skypulse/weather/ui/screen/SettingsScreen.kt \
  app/src/main/java/com/skypulse/weather/ui/components/DonateDialog.kt \
  app/src/main/res/drawable/qr_alipay.xml \
  app/src/main/res/drawable/qr_wechat.xml
git commit -m "feat: 添加设置页 — GitHub链接、打赏作者、检查更新、版本信息"
```
