# Weather Widget Design

## Overview

Add three Android home screen widgets (small 2x2, medium 4x2, large 4x4) that display real-time weather data, matching the iOS weather widget style and the app's existing iOS-inspired gradient aesthetic.

## Widget Sizes

### Small (2x2)
- **Top**: City name
- **Center**: Current temperature (large, thin font) + weather icon (Lottie-rendered to bitmap)
- **Bottom**: Weather description + high/low temperature (e.g. "晴 18° / 28°")
- **Background**: Dynamic gradient based on current weather condition + time of day (same 5-stop iOS gradients from Color.kt)

### Medium (4x2)
- **Left half**: City name, current temperature (large), weather description
- **Right half**: Next 6 hours hourly forecast — small temperature labels + mini weather icons in a row
- **Background**: Same dynamic gradient system

### Large (4x4)
- **Top section**: City name, current temperature (large), weather description, high/low
- **Middle section**: Next 8 hours hourly forecast (same as medium but wider)
- **Bottom section**: 7-day daily forecast — day name, weather icon, high/low temperature bars
- **Background**: Same dynamic gradient system

## Technical Architecture

### Components

1. **`WeatherWidgetProvider`** — AppWidgetProvider subclass, handles widget lifecycle (onUpdate, onEnabled, onDisabled)
2. **`WeatherWidgetWorker`** — WorkManager periodic worker, fetches fresh weather data and updates all active widgets
3. **`WeatherWidgetUpdater`** — Builds RemoteViews for each widget size, applies gradient backgrounds, populates data
4. **`WidgetGradientHelper`** — Generates bitmap gradient backgrounds for widgets (RemoteViews can't use Compose Brush, need Bitmap-based approach)
5. **Layout XMLs** — `widget_small.xml`, `widget_medium.xml`, `widget_large.xml`

### Data Flow

```
WorkManager (30min interval)
  → WeatherWidgetWorker.doWork()
    → WeatherRepository.fetchWeather() for current location city
    → Cache result to WeatherCache
    → WeatherWidgetUpdater.updateAllWidgets()
      → Build RemoteViews for each widget size
      → Apply gradient background bitmap
      → Populate temperature, text, icons
      → AppWidgetManager.updateAppWidget()
```

### Widget Update Triggers
- **Periodic**: WorkManager every 30 minutes
- **App launch**: When main app fetches new weather data, also update widgets
- **Boot**: Re-register WorkManager worker on device boot

### Gradient Backgrounds
- RemoteViews only supports solid colors and Bitmap backgrounds
- Generate gradient Bitmap programmatically using the same 5-stop color arrays from Color.kt
- Cache gradient bitmaps per weather condition to avoid regeneration
- Canvas + LinearGradient to paint the gradient onto a Bitmap

### Weather Icons
- Lottie animations can't render in RemoteViews
- Pre-render each Lottie icon to a static PNG bitmap at widget DPI
- Store in memory cache, regenerate on theme change
- Use the first frame of each Lottie animation as the static icon

### Click Behavior
- Tap any widget → open main app (WeatherScreen) with the current city
- PendingIntent with FLAG_IMMUTABLE

## Files to Create

| File | Purpose |
|------|---------|
| `widget/WeatherWidgetProvider.kt` | AppWidgetProvider lifecycle |
| `widget/WeatherWidgetWorker.kt` | WorkManager periodic refresh |
| `widget/WeatherWidgetUpdater.kt` | Build RemoteViews, populate data |
| `widget/WidgetGradientHelper.kt` | Generate gradient bitmaps |
| `widget/WidgetIconRenderer.kt` | Render Lottie to static bitmap |
| `res/xml/weather_widget_small.xml` | Small widget metadata |
| `res/xml/weather_widget_medium.xml` | Medium widget metadata |
| `res/xml/weather_widget_large.xml` | Large widget metadata |
| `res/layout/widget_small.xml` | Small widget layout |
| `res/layout/widget_medium.xml` | Medium widget layout |
| `res/layout/widget_large.xml` | Large widget layout |

## Files to Modify

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Register widget provider + receiver |
| `WeatherViewModel.kt` | Trigger widget update after weather fetch |
| `SkyPulseApp.kt` | Schedule WorkManager on app start |

## Constraints
- Widget layouts use RemoteViews (no Compose, no custom Views)
- Background must be Bitmap (no Brush/Gradient in RemoteViews)
- Icons must be static Bitmaps (no Lottie animation in widgets)
- Min widget size: 40dp x 40dp per cell
- Update frequency: 30 min (WorkManager minimum for periodic)
