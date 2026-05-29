# Weather Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three Android home screen widgets (small 2x2, medium 4x2, large 4x4) displaying real-time weather with iOS-style gradient backgrounds.

**Architecture:** AppWidgetProvider + RemoteViews with XML layouts. WorkManager for periodic 30-min updates. Gradient backgrounds and Lottie icons rendered to Bitmaps since RemoteViews doesn't support Compose or custom Views.

**Tech Stack:** AppWidgetProvider, RemoteViews, WorkManager, Lottie (bitmap rendering), Canvas/LinearGradient for gradient bitmaps

---

## File Structure

### New Files

| File | Responsibility |
|------|----------------|
| `res/layout/widget_small.xml` | Small widget layout (city, temp, icon, description) |
| `res/layout/widget_medium.xml` | Medium widget layout (current weather + 6h hourly) |
| `res/layout/widget_large.xml` | Large widget layout (current + hourly + 7-day) |
| `res/xml/weather_widget_info.xml` | Widget metadata (sizes, update period, preview) |
| `widget/WeatherWidgetProvider.kt` | AppWidgetProvider lifecycle callbacks |
| `widget/WeatherWidgetWorker.kt` | WorkManager periodic data refresh |
| `widget/WeatherWidgetUpdater.kt` | Build RemoteViews, populate data for all sizes |
| `widget/WidgetStyleHelper.kt` | Gradient bitmap generation + icon rendering |

### Modified Files

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Register widget provider, receiver, WorkManager initialization |
| `SkyPulseApp.kt` | Schedule WorkManager periodic worker on app start |
| `build.gradle.kts` | Add work-runtime dependency |

---

### Task 1: Add WorkManager Dependency

**Files:**
- Modify: `app/build.gradle.kts:86-115`

- [ ] **Step 1: Add work-runtime dependency**

In `build.gradle.kts`, add to the dependencies block after `implementation(libs.lottie.compose)`:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

- [ ] **Step 2: Sync and verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add work-runtime dependency for widget background updates"
```

---

### Task 2: Create Widget Layout XMLs

**Files:**
- Create: `app/src/main/res/layout/widget_small.xml`
- Create: `app/src/main/res/layout/widget_medium.xml`
- Create: `app/src/main/res/layout/widget_large.xml`
- Create: `app/src/main/res/xml/weather_widget_info.xml`

- [ ] **Step 1: Create widget_small.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:id="@+id/widget_root"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center_horizontal">

        <TextView
            android:id="@+id/widget_city"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@android:color/white"
            android:textSize="13sp"
            android:fontFamily="sans-serif-light"
            android:maxLines="1"
            android:ellipsize="end" />

        <ImageView
            android:id="@+id/widget_icon"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_marginTop="4dp"
            android:scaleType="fitCenter" />

        <TextView
            android:id="@+id/widget_temp"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@android:color/white"
            android:textSize="36sp"
            android:fontFamily="sans-serif-thin"
            android:layout_marginTop="-4dp" />

        <TextView
            android:id="@+id/widget_desc"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#B3FFFFFF"
            android:textSize="12sp"
            android:fontFamily="sans-serif-light"
            android:layout_marginTop="2dp"
            android:maxLines="1" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 2: Create widget_medium.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:id="@+id/widget_root"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <!-- Left: current weather -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical"
            android:gravity="center_horizontal">

            <TextView
                android:id="@+id/widget_city"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@android:color/white"
                android:textSize="13sp"
                android:fontFamily="sans-serif-light"
                android:maxLines="1"
                android:ellipsize="end" />

            <TextView
                android:id="@+id/widget_temp"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@android:color/white"
                android:textSize="48sp"
                android:fontFamily="sans-serif-thin"
                android:layout_marginTop="-4dp" />

            <TextView
                android:id="@+id/widget_desc"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#B3FFFFFF"
                android:textSize="12sp"
                android:fontFamily="sans-serif-light" />
        </LinearLayout>

        <!-- Right: hourly forecast (6 items) -->
        <LinearLayout
            android:id="@+id/widget_hourly_container"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1.5"
            android:orientation="horizontal"
            android:gravity="center_vertical|center_horizontal"
            android:layout_marginStart="8dp" />

    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 3: Create widget_large.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:id="@+id/widget_root"
    android:padding="14dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <!-- Top: city + temp + desc -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/widget_city"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@android:color/white"
                    android:textSize="14sp"
                    android:fontFamily="sans-serif-medium"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/widget_desc"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="#B3FFFFFF"
                    android:textSize="12sp"
                    android:fontFamily="sans-serif-light"
                    android:layout_marginTop="2dp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="end">

                <TextView
                    android:id="@+id/widget_temp"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@android:color/white"
                    android:textSize="40sp"
                    android:fontFamily="sans-serif-thin" />

                <TextView
                    android:id="@+id/widget_highlow"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="#B3FFFFFF"
                    android:textSize="12sp"
                    android:fontFamily="sans-serif-light" />
            </LinearLayout>
        </LinearLayout>

        <!-- Divider -->
        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#1AFFFFFF"
            android:layout_marginVertical="8dp" />

        <!-- Middle: hourly forecast -->
        <LinearLayout
            android:id="@+id/widget_hourly_container"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical" />

        <!-- Divider -->
        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#1AFFFFFF"
            android:layout_marginVertical="8dp" />

        <!-- Bottom: daily forecast -->
        <LinearLayout
            android:id="@+id/widget_daily_container"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:orientation="vertical" />

    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 4: Create weather_widget_info.xml**

Create directory `app/src/main/res/xml/` if it doesn't exist, then:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:minResizeWidth="80dp"
    android:minResizeHeight="80dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:maxResizeWidth="250dp"
    android:maxResizeHeight="250dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_small"
    android:previewLayout="@layout/widget_small"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description" />
```

- [ ] **Step 5: Add widget description string**

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="widget_description">显示实时天气信息</string>
```

- [ ] **Step 6: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/widget_small.xml app/src/main/res/layout/widget_medium.xml app/src/main/res/layout/widget_large.xml app/src/main/res/xml/weather_widget_info.xml app/src/main/res/values/strings.xml
git commit -m "feat: add widget layout XMLs and metadata for small/medium/large sizes"
```

---

### Task 3: Create WidgetStyleHelper (Gradient Bitmaps + Icon Rendering)

**Files:**
- Create: `app/src/main/java/com/skypulse/weather/widget/WidgetStyleHelper.kt`

- [ ] **Step 1: Create WidgetStyleHelper.kt**

```kotlin
package com.skypulse.weather.widget

import android.content.Context
import android.graphics.*
import android.util.Log
import android.util.TypedValue
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.skypulse.weather.util.WeatherUtils
import kotlin.math.roundToInt

object WidgetStyleHelper {

    private const val TAG = "WidgetStyle"
    private val gradientCache = mutableMapOf<String, Bitmap>()
    private val iconCache = mutableMapOf<String, Bitmap>()

    fun getGradientBitmap(
        context: Context,
        skycon: String?,
        isDay: Boolean,
        width: Int,
        height: Int
    ): Bitmap {
        val key = "${skycon}_${isDay}_${width}x${height}"
        gradientCache[key]?.let { return it }

        val colors = WeatherUtils.getWeatherGradient(skycon, isDay)
        val colorInts = colors.map { it.hashCode() }.toIntArray()
        val positions = when (colors.size) {
            5 -> floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            4 -> floatArrayOf(0f, 0.33f, 0.66f, 1f)
            3 -> floatArrayOf(0f, 0.5f, 1f)
            else -> floatArrayOf(0f, 1f)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                colorInts, positions, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        gradientCache[key] = bitmap
        return bitmap
    }

    fun getWeatherIconBitmap(
        context: Context,
        iconType: String,
        sizeDp: Int = 40
    ): Bitmap? {
        iconCache[iconType]?.let { return it }

        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()

        return try {
            val fileName = "meteocons/fill/$iconType.json"
            val result = LottieCompositionFactory.fromAssetSync(context, fileName)
            val composition = result.value ?: return null

            val drawable = LottieDrawable().apply {
                this.composition = composition
                progress = 0f
            }

            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)

            iconCache[iconType] = bitmap
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to render icon: $iconType", e)
            null
        }
    }

    fun clearCaches() {
        gradientCache.values.forEach { it.recycle() }
        gradientCache.clear()
        iconCache.values.forEach { it.recycle() }
        iconCache.clear()
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/skypulse/weather/widget/WidgetStyleHelper.kt
git commit -m "feat: widget style helper for gradient bitmaps and Lottie icon rendering"
```

---

### Task 4: Create WeatherWidgetUpdater (Build RemoteViews)

**Files:**
- Create: `app/src/main/java/com/skypulse/weather/widget/WeatherWidgetUpdater.kt`

- [ ] **Step 1: Create WeatherWidgetUpdater.kt**

```kotlin
package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.skypulse.weather.R
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.util.WeatherUtils
import kotlin.math.roundToInt

object WeatherWidgetUpdater {

    fun updateAll(context: Context, weather: WeatherResponse?, cityId: String) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            val views = when {
                widthDp <= 115 || heightDp <= 115 -> buildSmall(context, weather)
                widthDp <= 230 -> buildMedium(context, weather)
                else -> buildLarge(context, weather)
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun buildSmall(context: Context, weather: WeatherResponse?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_small)
        applyBackground(views, context, weather, 300, 300)
        applyClickPendingIntent(views, context)

        if (weather == null) {
            views.setTextViewText(R.id.widget_city, "—")
            views.setTextViewText(R.id.widget_temp, "—")
            views.setTextViewText(R.id.widget_desc, "")
            views.setViewVisibility(R.id.widget_icon, View.GONE)
            return views
        }

        val realtime = weather.result?.realtime
        val skycon = realtime?.skycon
        val info = WeatherUtils.getWeatherInfo(skycon)
        val temp = WeatherUtils.formatTemperature(realtime?.temperature)
        val daily = weather.result?.daily
        val todayTemp = daily?.temperature?.firstOrNull()
        val low = WeatherUtils.formatTemperature(todayTemp?.min)
        val high = WeatherUtils.formatTemperature(todayTemp?.max)

        views.setTextViewText(R.id.widget_city, getLocationName(weather))
        views.setTextViewText(R.id.widget_temp, temp)
        views.setTextViewText(R.id.widget_desc, "${info.description} $low / $high")

        val iconBitmap = WidgetStyleHelper.getWeatherIconBitmap(context, info.icon, 40)
        if (iconBitmap != null) {
            views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_icon, View.GONE)
        }

        return views
    }

    private fun buildMedium(context: Context, weather: WeatherResponse?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_medium)
        applyBackground(views, context, weather, 600, 300)
        applyClickPendingIntent(views, context)

        if (weather == null) {
            views.setTextViewText(R.id.widget_city, "—")
            views.setTextViewText(R.id.widget_temp, "—")
            views.setTextViewText(R.id.widget_desc, "")
            views.removeAllViews(R.id.widget_hourly_container)
            return views
        }

        val realtime = weather.result?.realtime
        val skycon = realtime?.skycon
        val info = WeatherUtils.getWeatherInfo(skycon)
        val temp = WeatherUtils.formatTemperature(realtime?.temperature)

        views.setTextViewText(R.id.widget_city, getLocationName(weather))
        views.setTextViewText(R.id.widget_temp, temp)
        views.setTextViewText(R.id.widget_desc, info.description)

        // Hourly forecast — next 6 hours
        views.removeAllViews(R.id.widget_hourly_container)
        val hourly = weather.result?.hourly
        if (hourly != null) {
            addHourlyItems(context, views, R.id.widget_hourly_container, hourly, 6)
        }

        return views
    }

    private fun buildLarge(context: Context, weather: WeatherResponse?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_large)
        applyBackground(views, context, weather, 600, 600)
        applyClickPendingIntent(views, context)

        if (weather == null) {
            views.setTextViewText(R.id.widget_city, "—")
            views.setTextViewText(R.id.widget_temp, "—")
            views.setTextViewText(R.id.widget_desc, "")
            views.setTextViewText(R.id.widget_highlow, "")
            views.removeAllViews(R.id.widget_hourly_container)
            views.removeAllViews(R.id.widget_daily_container)
            return views
        }

        val realtime = weather.result?.realtime
        val skycon = realtime?.skycon
        val info = WeatherUtils.getWeatherInfo(skycon)
        val temp = WeatherUtils.formatTemperature(realtime?.temperature)
        val daily = weather.result?.daily
        val todayTemp = daily?.temperature?.firstOrNull()
        val low = WeatherUtils.formatTemperature(todayTemp?.min)
        val high = WeatherUtils.formatTemperature(todayTemp?.max)

        views.setTextViewText(R.id.widget_city, getLocationName(weather))
        views.setTextViewText(R.id.widget_temp, temp)
        views.setTextViewText(R.id.widget_desc, info.description)
        views.setTextViewText(R.id.widget_highlow, "$low / $high")

        // Hourly — next 8 hours
        views.removeAllViews(R.id.widget_hourly_container)
        val hourly = weather.result?.hourly
        if (hourly != null) {
            addHourlyItems(context, views, R.id.widget_hourly_container, hourly, 8)
        }

        // Daily — 7 days
        views.removeAllViews(R.id.widget_daily_container)
        if (daily != null) {
            addDailyItems(context, views, R.id.widget_daily_container, daily)
        }

        return views
    }

    private fun addHourlyItems(
        context: Context,
        views: RemoteViews,
        containerId: Int,
        hourly: com.skypulse.weather.model.HourlyForecast,
        count: Int
    ) {
        val temps = hourly.temperature?.take(count) ?: return
        val skycons = hourly.skycon?.take(count)

        for (i in temps.indices) {
            val item = RemoteViews(context.packageName, R.layout.widget_hourly_item)
            val hour = WeatherUtils.formatHourShort(temps[i].datetime)
            val tempVal = WeatherUtils.formatTemperature(temps[i].value)
            val skycon = skycons?.getOrNull(i)?.value
            val weatherInfo = WeatherUtils.getWeatherInfo(skycon)

            item.setTextViewText(R.id.hourly_time, if (i == 0) "现在" else hour)
            item.setTextViewText(R.id.hourly_temp, tempVal)

            val iconBitmap = WidgetStyleHelper.getWeatherIconBitmap(context, weatherInfo.icon, 24)
            if (iconBitmap != null) {
                item.setImageViewBitmap(R.id.hourly_icon, iconBitmap)
            }

            views.addView(containerId, item)
        }
    }

    private fun addDailyItems(
        context: Context,
        views: RemoteViews,
        containerId: Int,
        daily: com.skypulse.weather.model.DailyForecast
    ) {
        val temps = daily.temperature?.take(7) ?: return
        val skycons = daily.skycon?.take(7)

        for (i in temps.indices) {
            val item = RemoteViews(context.packageName, R.layout.widget_daily_item)
            val dayLabel = when (i) {
                0 -> "今天"
                1 -> "明天"
                2 -> "后天"
                else -> WeatherUtils.formatWeekday(temps[i].date)
            }
            val skycon = skycons?.getOrNull(i)?.value
            val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
            val low = WeatherUtils.formatTemperature(temps[i].min)
            val high = WeatherUtils.formatTemperature(temps[i].max)

            item.setTextViewText(R.id.daily_day, dayLabel)
            item.setTextViewText(R.id.daily_temp, "$low / $high")

            val iconBitmap = WidgetStyleHelper.getWeatherIconBitmap(context, weatherInfo.icon, 20)
            if (iconBitmap != null) {
                item.setImageViewBitmap(R.id.daily_icon, iconBitmap)
            }

            views.addView(containerId, item)
        }
    }

    private fun applyBackground(
        views: RemoteViews,
        context: Context,
        weather: WeatherResponse?,
        widthDp: Int,
        heightDp: Int
    ) {
        val skycon = weather?.result?.realtime?.skycon
        val isDay = WeatherUtils.isCurrentlyDay()
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        val bitmap = WidgetStyleHelper.getGradientBitmap(context, skycon, isDay, widthPx, heightPx)
        views.setImageViewBitmap(R.id.widget_background, bitmap)
    }

    private fun applyClickPendingIntent(views: RemoteViews, context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }

    private fun getLocationName(weather: WeatherResponse): String {
        // Use a default; real location name comes from ViewModel
        return "我的位置"
    }

    fun triggerUpdate(context: Context) {
        val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (will have unresolved references to layouts not yet created in Task 5)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/skypulse/weather/widget/WeatherWidgetUpdater.kt
git commit -m "feat: widget updater builds RemoteViews for small/medium/large sizes"
```

---

### Task 5: Create Widget Sub-Layouts (Hourly + Daily Items)

**Files:**
- Create: `app/src/main/res/layout/widget_hourly_item.xml`
- Create: `app/src/main/res/layout/widget_daily_item.xml`

- [ ] **Step 1: Create widget_hourly_item.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:paddingHorizontal="2dp">

    <TextView
        android:id="@+id/hourly_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#B3FFFFFF"
        android:textSize="10sp"
        android:fontFamily="sans-serif-light" />

    <ImageView
        android:id="@+id/hourly_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_marginVertical="2dp"
        android:scaleType="fitCenter" />

    <TextView
        android:id="@+id/hourly_temp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@android:color/white"
        android:textSize="12sp"
        android:fontFamily="sans-serif-light" />
</LinearLayout>
```

- [ ] **Step 2: Create widget_daily_item.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingVertical="2dp">

    <TextView
        android:id="@+id/daily_day"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="@android:color/white"
        android:textSize="12sp"
        android:fontFamily="sans-serif-light" />

    <ImageView
        android:id="@+id/daily_icon"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:layout_marginHorizontal="8dp"
        android:scaleType="fitCenter" />

    <TextView
        android:id="@+id/daily_temp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#B3FFFFFF"
        android:textSize="12sp"
        android:fontFamily="sans-serif-light" />
</LinearLayout>
```

- [ ] **Step 3: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/widget_hourly_item.xml app/src/main/res/layout/widget_daily_item.xml
git commit -m "feat: widget sub-layouts for hourly and daily forecast items"
```

---

### Task 6: Create WeatherWidgetProvider

**Files:**
- Create: `app/src/main/java/com/skypulse/weather/widget/WeatherWidgetProvider.kt`

- [ ] **Step 1: Create WeatherWidgetProvider.kt**

```kotlin
package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.repository.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WidgetProvider"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${appWidgetIds.size} widgets")
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            fetchAndUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled: first widget placed")
        WeatherWidgetWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled: last widget removed")
        WeatherWidgetWorker.cancel(context)
        WidgetStyleHelper.clearCaches()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                fetchAndUpdate(context)
            }
        }
    }

    private suspend fun fetchAndUpdate(context: Context) {
        val cityManager = CityManager(context)
        val weatherCache = WeatherCache(context)
        val repository = WeatherRepository()

        val currentCity = cityManager.getCities().find { it.isCurrentLocation }

        // Try cache first
        val cachedWeather = currentCity?.let { weatherCache.load(it.id) }
        if (cachedWeather != null) {
            WeatherWidgetUpdater.updateAll(context, cachedWeather, currentCity.id)
        }

        // Fetch fresh data
        if (currentCity != null) {
            val result = repository.getWeather(currentCity.longitude, currentCity.latitude)
            result.onSuccess { weather ->
                weatherCache.save(currentCity.id, weather)
                WeatherWidgetUpdater.updateAll(context, weather, currentCity.id)
            }
            result.onFailure { e ->
                Log.w(TAG, "Widget fetch failed", e)
                // If we had no cached data, show empty state
                if (cachedWeather == null) {
                    WeatherWidgetUpdater.updateAll(context, null, currentCity.id)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/skypulse/weather/widget/WeatherWidgetProvider.kt
git commit -m "feat: AppWidgetProvider with cache-first update and background fetch"
```

---

### Task 7: Create WeatherWidgetWorker (Periodic Background Updates)

**Files:**
- Create: `app/src/main/java/com/skypulse/weather/widget/WeatherWidgetWorker.kt`

- [ ] **Step 1: Create WeatherWidgetWorker.kt**

```kotlin
package com.skypulse.weather.widget

import android.content.Context
import android.util.Log
import androidx.work.*
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.repository.WeatherRepository
import java.util.concurrent.TimeUnit

class WeatherWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetWorker"
        private const val WORK_NAME = "weather_widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(
                30, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled periodic widget refresh")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic widget refresh")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: refreshing widget data")
        val cityManager = CityManager(applicationContext)
        val weatherCache = WeatherCache(applicationContext)
        val repository = WeatherRepository()

        val currentCity = cityManager.getCities().find { it.isCurrentLocation }
            ?: return Result.failure()

        val result = repository.getWeather(currentCity.longitude, currentCity.latitude)
        return result.fold(
            onSuccess = { weather ->
                weatherCache.save(currentCity.id, weather)
                WeatherWidgetUpdater.updateAll(applicationContext, weather, currentCity.id)
                Result.success()
            },
            onFailure = { e ->
                Log.w(TAG, "Widget refresh failed", e)
                Result.retry()
            }
        )
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/skypulse/weather/widget/WeatherWidgetWorker.kt
git commit -m "feat: WorkManager periodic worker for 30-min widget refresh"
```

---

### Task 8: Register Widget in Manifest + App Initialization

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/skypulse/weather/SkyPulseApp.kt`

- [ ] **Step 1: Add widget receiver to AndroidManifest.xml**

Add before `</application>`:

```xml
<receiver
    android:name=".widget.WeatherWidgetProvider"
    android:exported="true"
    android:label="SkyPulse 天气">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/weather_widget_info" />
</receiver>
```

- [ ] **Step 2: Update SkyPulseApp.kt to schedule WorkManager**

Replace the entire file:

```kotlin
package com.skypulse.weather

import android.app.Application
import com.skypulse.weather.widget.WeatherWidgetWorker

class SkyPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WeatherWidgetWorker.schedule(this)
    }
}
```

- [ ] **Step 3: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/skypulse/weather/SkyPulseApp.kt
git commit -m "feat: register widget provider in manifest and schedule WorkManager on app start"
```

---

### Task 9: Update Widget When Main App Fetches Weather

**Files:**
- Modify: `app/src/main/java/com/skypulse/weather/viewmodel/WeatherViewModel.kt:473-499`

- [ ] **Step 1: Add widget update trigger after weather fetch**

In `WeatherViewModel.kt`, add import at top:

```kotlin
import com.skypulse.weather.widget.WeatherWidgetUpdater
```

In `fetchWeatherForLocation()`, after `weatherCache.save(currentCity.id, response)` (line ~490), add:

```kotlin
WeatherWidgetUpdater.triggerUpdate(getApplication())
```

- [ ] **Step 2: Also trigger in loadWeatherForCity for current location**

In `loadWeatherForCity()` (line ~260), after `weatherCache.save(city.id, response)` (line ~266), add:

```kotlin
if (city.isCurrentLocation) {
    WeatherWidgetUpdater.triggerUpdate(getApplication())
}
```

- [ ] **Step 3: Verify build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/skypulse/weather/viewmodel/WeatherViewModel.kt
git commit -m "feat: trigger widget update when main app fetches fresh weather data"
```

---

### Task 10: Bump Version + Release Build

**Files:**
- Modify: `app/build.gradle.kts:29-30`

- [ ] **Step 1: Bump version**

Change:
```kotlin
versionCode = 91
versionName = "1.8.11"
```
To:
```kotlin
versionCode = 92
versionName = "1.8.12"
```

- [ ] **Step 2: Release build**

Run: `cd C:/Users/phil/weather-none && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit and push**

```bash
git add app/build.gradle.kts
git commit -m "feat: weather home screen widgets (small/medium/large) with iOS-style gradients

- Small (2x2): city, temp, icon, description, high/low
- Medium (4x2): current weather + 6h hourly forecast
- Large (4x4): current weather + hourly + 7-day daily forecast
- Dynamic gradient backgrounds matching weather conditions
- Lottie icons rendered to static bitmaps for widgets
- WorkManager 30-min periodic background refresh
- Widget updates triggered from main app weather fetch
- Cache-first display, background network fetch"

git push
```
