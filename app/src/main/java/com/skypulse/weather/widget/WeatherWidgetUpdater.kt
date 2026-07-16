package com.skypulse.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.skypulse.weather.MainActivity
import com.skypulse.weather.R
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.ui.components.WeatherSvgRenderer
import com.skypulse.weather.util.FileLogger
import androidx.compose.ui.graphics.toArgb
import com.skypulse.weather.util.WeatherUtils
import java.text.SimpleDateFormat
import java.util.*

object WeatherWidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private val iconCache = android.util.LruCache<String, Bitmap>(14)

    fun updateLoading(context: Context, cityName: String? = null) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "updateLoading: \u65e0\u6d3b\u8dc3 widget\uff0c\u8df3\u8fc7\u6e32\u67d3")
                return
            }

            val cityText = shortenLocation(cityName ?: "\u5b9a\u4f4d\u4e2d...")
            val iconBitmap = renderIcon(context, "partly-cloudy-day")
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_small)
                views.setTextViewText(R.id.widget_city, cityText)
                views.setTextViewText(R.id.widget_temp, "--")
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }
                // Set placeholder for forecast items
                views.setTextViewText(R.id.widget_time_now, "\u73b0\u5728")
                views.setTextViewText(R.id.widget_time_1h, "--")
                views.setTextViewText(R.id.widget_time_2h, "--")
                views.setTextViewText(R.id.widget_temp_now, "--")
                views.setTextViewText(R.id.widget_temp_1h, "--")
                views.setTextViewText(R.id.widget_temp_2h, "--")

                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, null, w, h)
                views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                                views.setBoolean(R.id.widget_root, "setClipToOutline", true)
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_rounded_bg)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(widgetId, views)
            }
            FileLogger.i(TAG, "updateLoading: \u6e32\u67d3\u5b9a\u4f4d\u5360\u4f4d\u6001\u5b8c\u6210, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateLoading: \u6e32\u67d3\u5f02\u5e38", e)
        }
    }

    fun updateAll(context: Context, weather: WeatherResponse?, cityName: String?) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "updateAll: \u65e0\u6d3b\u8dc3 widget\uff0c\u8df3\u8fc7\u6e32\u67d3")
                return
            }

            val realtime = weather?.result?.realtime
            val daily = weather?.result?.daily
            val hourly = weather?.result?.hourly
            val skycon = realtime?.skycon
            val isDay = WeatherUtils.isCurrentlyDay(daily)
            val info = WeatherUtils.getWeatherInfo(skycon)
            val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
            val cityText = shortenLocation(cityName ?: "--")

            // Get hourly forecast for now, +1h, +2h
            val hourlyTemps = hourly?.temperature
            val hourlySkycons = hourly?.skycon
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)

            // Find current hour index in hourly data
            val nowIndex = findHourlyIndex(hourlyTemps, currentHour)
            val h1Index = if (nowIndex >= 0) nowIndex + 1 else -1
            val h2Index = if (nowIndex >= 0) nowIndex + 2 else -1

            // Get temperatures
            val tempNow = realtime?.temperature
            val temp1h = getHourlyValue(hourlyTemps, h1Index)
            val temp2h = getHourlyValue(hourlyTemps, h2Index)

            // Get skycons
            val skycon1h = getHourlySkycon(hourlySkycons, h1Index)
            val skycon2h = getHourlySkycon(hourlySkycons, h2Index)

            // Get weather info for each hour
            val info1h = WeatherUtils.getWeatherInfo(skycon1h)
            val info2h = WeatherUtils.getWeatherInfo(skycon2h)

            // Format times
            val timeNow = "\u73b0\u5728"
            val time1h = formatHour(currentHour + 1)
            val time2h = formatHour(currentHour + 2)

            FileLogger.i(TAG, "updateAll: \u6e32\u67d3\u6570\u636e \u2014 city=$cityText, temp=$tempText, skycon=$skycon, isDay=$isDay")
            FileLogger.d(TAG, "updateAll: \u5c0f\u65f6\u9884\u62a5 now=$tempNow/${info.icon}, 1h=$temp1h/${info1h.icon}, 2h=$temp2h/${info2h.icon}")

            val precipitationColor = WeatherUtils.getPrecipitationIconColor(skycon, isDay).toArgb()
            val iconBitmap = renderIcon(context, info.icon, precipitationColor)
            val icon1hBitmap = renderIcon(context, info1h.icon, precipitationColor)
            val icon2hBitmap = renderIcon(context, info2h.icon, precipitationColor)

            if (iconBitmap == null) {
                FileLogger.w(TAG, "updateAll: \u56fe\u6807\u6e32\u67d3\u5931\u8d25 skycon=$skycon, icon=${info.icon}")
            }

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_small)
                // Main temperature and city
                views.setTextViewText(R.id.widget_temp, tempText)
                views.setTextViewText(R.id.widget_city, cityText)

                // Main icon
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }

                // Forecast row - Now
                views.setTextViewText(R.id.widget_time_now, timeNow)
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon_now, iconBitmap)
                }
                views.setTextViewText(R.id.widget_temp_now, WeatherUtils.formatTemperature(tempNow))

                // Forecast row - +1h
                views.setTextViewText(R.id.widget_time_1h, time1h)
                if (icon1hBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon_1h, icon1hBitmap)
                }
                views.setTextViewText(R.id.widget_temp_1h, WeatherUtils.formatTemperature(temp1h))

                // Forecast row - +2h
                views.setTextViewText(R.id.widget_time_2h, time2h)
                if (icon2hBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon_2h, icon2hBitmap)
                }
                views.setTextViewText(R.id.widget_temp_2h, WeatherUtils.formatTemperature(temp2h))

                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, skycon, w, h, isDay)
                views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                                views.setBoolean(R.id.widget_root, "setClipToOutline", true)
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_rounded_bg)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(widgetId, views)
            }
            FileLogger.i(TAG, "updateAll: \u6e32\u67d3\u5b8c\u6210, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateAll: \u6e32\u67d3\u5f02\u5e38", e)
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                ids.forEach { widgetId ->
                    try {
                        val views = RemoteViews(context.packageName, R.layout.widget_small)
                        views.setTextViewText(R.id.widget_city, "--")
                        views.setTextViewText(R.id.widget_temp, "--")
                        views.setTextViewText(R.id.widget_time_now, "\u73b0\u5728")
                        views.setTextViewText(R.id.widget_time_1h, "--")
                        views.setTextViewText(R.id.widget_time_2h, "--")
                        views.setTextViewText(R.id.widget_temp_now, "--")
                        views.setTextViewText(R.id.widget_temp_1h, "--")
                        views.setTextViewText(R.id.widget_temp_2h, "--")
                        val (w, h) = getWidgetSizePx(context, widgetId)
                        val sizedBg = buildGradientBitmap(context, null, w, h)
                        views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                                                views.setBoolean(R.id.widget_root, "setClipToOutline", true)
                        views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_rounded_bg)
                        val intent = Intent(context, MainActivity::class.java)
                        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widget_root, pending)
                        manager.updateAppWidget(widgetId, views)
                    } catch (_: Exception) {}
                }
                FileLogger.i(TAG, "updateAll: \u9ed8\u8ba4\u72b6\u6001\u6e32\u67d3\u5b8c\u6210")
            } catch (e2: Exception) {
                FileLogger.e(TAG, "updateAll: \u9ed8\u8ba4\u72b6\u6001\u6e32\u67d3\u4e5f\u5931\u8d25", e2)
            }
        }
    }

    private fun findHourlyIndex(hourlyTemps: List<com.skypulse.weather.model.HourlyValue>?, targetHour: Int): Int {
        if (hourlyTemps == null) return -1
        val targetSuffix = String.format("T%02d:", targetHour)
        return hourlyTemps.indexOfFirst { it.datetime?.contains(targetSuffix) == true }
    }

    private fun getHourlyValue(hourlyTemps: List<com.skypulse.weather.model.HourlyValue>?, index: Int): Double? {
        if (hourlyTemps == null || index < 0 || index >= hourlyTemps.size) return null
        return hourlyTemps[index].value
    }

    private fun getHourlySkycon(hourlySkycons: List<com.skypulse.weather.model.HourlySkycon>?, index: Int): String? {
        if (hourlySkycons == null || index < 0 || index >= hourlySkycons.size) return null
        return hourlySkycons[index].value
    }

    private fun formatHour(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return String.format("%02d:00", h)
    }

    private fun shortenLocation(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "--"
        if (value == "\u5b9a\u4f4d\u4e2d...") return value

        val districtMatch = Regex("([\u5e02\u533a\u53bf]+[\u533a\u53bf])").find(value)
        if (districtMatch != null) return districtMatch.groupValues[1]

        val cityMatch = Regex("([\u5e02]+[\u5e02])").find(value)
        if (cityMatch != null) return cityMatch.groupValues[1]

        val segment = value.split(Regex("[\u3001\u3002\uff0c]")).firstOrNull { it.length >= 2 } ?: value
        return if (segment.length > 4) segment.substring(0, 4) else segment
    }

    private fun getWidgetSizePx(context: Context, widgetId: Int): Pair<Int, Int> {
        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt().coerceIn(200, 800)
        val height = (heightDp * density).toInt().coerceIn(100, 500)
        return width to height
    }

    private fun renderIcon(context: Context, icon: String, precipitationColor: Int? = null): Bitmap? {
        val cacheKey = if (precipitationColor == null) icon else "$icon:$precipitationColor"
        iconCache.get(cacheKey)?.let { return it }
        val bitmap = when (icon) {
            "clear-night" -> renderMoonBitmap(context)
            else -> renderSvgIcon(context, icon, precipitationColor)
        }
        if (bitmap != null) {
            iconCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun renderSvgIcon(context: Context, icon: String, precipitationColor: Int?): Bitmap? {
        return try {
            val sizePx = (48 * context.resources.displayMetrics.density).toInt()
            WeatherSvgRenderer.renderBitmap(context, icon, sizePx, precipitationColor)
        } catch (e: Exception) {
            FileLogger.e(TAG, "renderSvgIcon failed: icon=$icon", e)
            null
        }
    }

    private fun renderMoonBitmap(context: Context): Bitmap {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.36f

        // Outer glow
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 200, 220, 255)
            canvas.drawCircle(cx, cy, r * 1.3f, this)
        }

        // Moon base
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(240, 240, 250)
            canvas.drawCircle(cx, cy, r, this)
        }

        // Crescent shadow
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 15, 25, 55)
            canvas.drawCircle(cx + r * 0.4f, cy - r * 0.1f, r * 0.82f, this)
        }

        // Craters
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 180, 190, 210)
            canvas.drawCircle(cx - r * 0.3f, cy + r * 0.1f, r * 0.15f, this)
            canvas.drawCircle(cx - r * 0.15f, cy - r * 0.35f, r * 0.1f, this)
            canvas.drawCircle(cx + r * 0.1f, cy + r * 0.4f, r * 0.08f, this)
        }

        return bitmap
    }

    /**
     * Build a weather-appropriate gradient background bitmap.
     *
     * Uses 4 visual layers for natural sky simulation:
     *   1. Radial gradient base center biased upward for natural sky depth
     *   2. Top highlight focused light simulating zenith sun / moon glow
     *   3. Bottom shadow subtle ground-level darkening for contrast
     *   4. Rain streaks (optional) decorative rain lines for rainy weather
     */
    private fun buildGradientBitmap(context: Context, skycon: String?, width: Int, height: Int, isDay: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val radius = 18f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val isRain = isRainSkycon(skycon)
        val gradientColors = weatherWidgetGradient(skycon, isDay)

        // Layer 1: Radial gradient base
        val cx = width * 0.48f
        val cy = height * 0.32f
        val gradRadius = maxOf(width, height) * 0.82f
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, gradRadius, gradientColors, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Layer 2: Top highlight
        val highlightAlpha = if (isDay) 48 else 22
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.38f,
                intArrayOf(Color.argb(highlightAlpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Layer 3: Bottom shadow
        val shadowAlpha = if (isDay) 38 else 56
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.55f, 0f, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(shadowAlpha, 0, 0, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        if (isRain) {
            drawStaticRainStreaks(canvas, rect, radius, density, isDay)
        }

        return bitmap
    }

    private fun isRainSkycon(skycon: String?): Boolean {
        return skycon?.let {
            it.contains("RAIN") || it.contains("STORM") || it == "THUNDER_SHOWER"
        } == true
    }

    private fun weatherWidgetGradient(skycon: String?, isDay: Boolean): IntArray {
        return WeatherUtils.getWeatherGradient(skycon, isDay).map { it.toArgb() }.toIntArray()
    }

    private fun drawStaticRainStreaks(canvas: Canvas, rect: RectF, radius: Float, density: Float, isDay: Boolean) {
        val width = rect.width()
        val height = rect.height()
        val mask = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val fan = Path().apply {
            moveTo(width, 0f)
            arcTo(RectF(width - 142f * density, -22f * density, width + 42f * density, 162f * density), -92f, -110f, false)
            lineTo(width, height * 0.54f)
            close()
        }
        fan.op(mask, Path.Op.INTERSECT)

        canvas.save()
        canvas.clipPath(fan)
        val streaks = arrayOf(
            floatArrayOf(width - 18f * density, 8f * density, width - 48f * density, 46f * density, 1.35f * density),
            floatArrayOf(width - 42f * density, 8f * density, width - 76f * density, 54f * density, 1.1f * density),
            floatArrayOf(width - 64f * density, 18f * density, width - 94f * density, 56f * density, 1.0f * density),
            floatArrayOf(width - 30f * density, 44f * density, width - 66f * density, 88f * density, 1.15f * density)
        )
        streaks.forEachIndexed { index, streak ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = streak[4]
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(
                    streak[0], streak[1], streak[2], streak[3],
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb(if (isDay) 84 - index * 10 else 72 - index * 8, 224, 242, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawLine(streak[0], streak[1], streak[2], streak[3], this)
            }
        }
        canvas.restore()
    }
}

