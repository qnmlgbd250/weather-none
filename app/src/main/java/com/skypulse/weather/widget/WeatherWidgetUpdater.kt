package com.skypulse.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.skypulse.weather.MainActivity
import com.skypulse.weather.R
import com.skypulse.weather.model.WeatherResponse
import androidx.compose.ui.graphics.toArgb
import com.skypulse.weather.util.WeatherUtils

object WeatherWidgetUpdater {

    private val iconCache = android.util.LruCache<String, Bitmap>(14)

    fun updateAll(context: Context, weather: WeatherResponse?, cityName: String?) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val realtime = weather?.result?.realtime
            val daily = weather?.result?.daily
            val skycon = realtime?.skycon
            val isDay = skycon?.contains("NIGHT") != true
            val info = WeatherUtils.getWeatherInfo(skycon)
            val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
            val todayTemp = WeatherUtils.todayTemperature(daily)
            val maxTemp = todayTemp?.max?.let { WeatherUtils.formatTemperature(it) } ?: "--"
            val minTemp = todayTemp?.min?.let { WeatherUtils.formatTemperature(it) } ?: "--"
            val cityText = shortenLocation(cityName ?: "--")
            val detailText = "${info.description}  $minTemp / $maxTemp"
            val iconBitmap = renderIcon(context, info.icon)
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_small)
                views.setTextViewText(R.id.widget_city, cityText)
                views.setTextViewText(R.id.widget_temp, tempText)
                views.setTextViewText(R.id.widget_detail, detailText)
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }
                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, skycon, w, h, isDay)
                views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                views.setImageViewResource(R.id.widget_pin, R.drawable.ic_widget_location)
                views.setBoolean(R.id.widget_root, "setClipToOutline", true)
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_rounded_bg)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(widgetId, views)
            }
        } catch (_: Exception) {
            // Show default gradient even on error
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                ids.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_small)
                    val (w, h) = getWidgetSizePx(context, widgetId)
                    val sizedBg = buildGradientBitmap(context, null, w, h)
                    views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                    views.setBoolean(R.id.widget_root, "setClipToOutline", true)
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_rounded_bg)
                    views.setTextViewText(R.id.widget_city, "--")
                    views.setTextViewText(R.id.widget_temp, "--")
                    views.setTextViewText(R.id.widget_detail, "\u52a0\u8f7d\u4e2d...")
                    val intent = Intent(context, MainActivity::class.java)
                    val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root, pending)
                    manager.updateAppWidget(widgetId, views)
                }
            } catch (_: Exception) {}
        }
    }

    private fun shortenLocation(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "--"

        val districtMatch = Regex("([^\u7701\u5e02\u533a\u53bf]+[\u533a\u53bf])").find(value)
        if (districtMatch != null) return districtMatch.groupValues[1]

        val cityMatch = Regex("([^\u7701\u5e02]+[\u5e02])").find(value)
        if (cityMatch != null) return cityMatch.groupValues[1]

        val segment = value.split(Regex("[\\s,\uff0c\u3001\u3002]")).firstOrNull { it.length >= 2 } ?: value
        return if (segment.length > 4) segment.substring(0, 4) else segment
    }

    private fun renderIcon(context: Context, icon: String): Bitmap? {
        iconCache.get(icon)?.let { return it }
        val bitmap = if (icon == "clear-night") {
            renderMoonBitmap(context)
        } else {
            renderLottieIcon(context, icon)
        }
        if (bitmap != null) iconCache.put(icon, bitmap)
        return bitmap
    }

    private fun renderLottieIcon(context: Context, icon: String): Bitmap? {
        return try {
            val composition = LottieCompositionFactory.fromAssetSync(context, "meteocons/fill/${icon}.json").value
                ?: return null
            val drawable = LottieDrawable()
            drawable.composition = composition
            drawable.progress = 0f
            val size = (96 * context.resources.displayMetrics.density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Hand-drawn moon icon matching the Compose MoonIcon in WeatherIcon.kt.
     * Uses the same Lottie bezier path from clear-night.json with warm golden gradient.
     */
    private fun renderMoonBitmap(context: Context): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val size = (96 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = size / 128f

            // Original Lottie path from clear-night.json (128x128 canvas)
            val v = arrayOf(
                floatArrayOf(60.3018f, 32.582f),
                floatArrayOf(95.3252f, 72.5146f),
                floatArrayOf(64.5361f, 95.5f),
                floatArrayOf(32.5f, 63.8984f),
                floatArrayOf(60.3018f, 32.582f)
            )
            val o = arrayOf(
                floatArrayOf(-5.0201f, 21.1179f),
                floatArrayOf(-3.8059f, 13.2556f),
                floatArrayOf(-17.6986f, 0f),
                floatArrayOf(0f, -16.0296f),
                floatArrayOf(0f, 0f)
            )
            val inn = arrayOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(-21.7251f, 1.8331f),
                floatArrayOf(14.6625f, -0.0002f),
                floatArrayOf(0.0001f, 17.446f),
                floatArrayOf(-15.6952f, 2.0458f)
            )

            val path = Path().apply {
                moveTo(v[0][0] * scale, v[0][1] * scale)
                for (i in 0 until 4) {
                    val p0 = v[i]
                    val p1 = v[i + 1]
                    cubicTo(
                        (p0[0] + o[i][0]) * scale,
                        (p0[1] + o[i][1]) * scale,
                        (p1[0] + inn[i + 1][0]) * scale,
                        (p1[1] + inn[i + 1][1]) * scale,
                        p1[0] * scale,
                        p1[1] * scale
                    )
                }
                close()
            }

            // Gradient fill: warm yellow matching WeatherIcon MoonIcon
            val gradient = LinearGradient(
                0f, 32f * scale,
                0f, 96f * scale,
                intArrayOf(Color.parseColor("#FFFFD54F"), Color.parseColor("#FFFFCA28")),
                null,
                Shader.TileMode.CLAMP
            )
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = gradient
            }
            canvas.drawPath(path, fillPaint)

            // Gold stroke matching original
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#FFF9AF03")
                strokeWidth = 1f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(path, strokePaint)

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get the actual pixel size of a widget instance from system options.
     * Falls back to a sensible default if options are not yet available.
     */
    private fun getWidgetSizePx(context: Context, widgetId: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        return try {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            val w = (wDp * density).toInt().coerceAtLeast((110 * density).toInt())
            val h = (hDp * density).toInt().coerceAtLeast((110 * density).toInt())
            Pair(w, h)
        } catch (_: Exception) {
            val fallback = (180 * density).toInt()
            Pair(fallback, fallback)
        }
    }

    /**
     * Build gradient bitmap sized to the actual widget dimensions with rounded corners.
     * Uses drawRoundRect for maximum compatibility across all devices.
     */
    private fun buildGradientBitmap(context: Context, skycon: String?, width: Int, height: Int, isDay: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val radius = 18f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // 1. 底层渐变
        val baseColors = WeatherUtils.getWeatherGradient(skycon, isDay).map { it.toArgb() }.toIntArray()
        val baseAlpha = if (isDay) 0.85f else 1.0f
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255 * baseAlpha).toInt()
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), baseColors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, radius, radius, basePaint)

        // 2. 毛玻璃蒙版（上浅下深，模拟真实光影）
        val frostColors = if (isDay) {
            intArrayOf(Color.parseColor("#1CFFFFFF"), Color.parseColor("#08FFFFFF"))
        } else {
            intArrayOf(Color.parseColor("#20B8D4E8"), Color.parseColor("#08B8D4E8"))
        }
        val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), frostColors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, radius, radius, frostPaint)

        // 3. 玻璃边缘高光
        val borderColor = if (isDay) Color.parseColor("#33FFFFFF") else Color.parseColor("#22FFFFFF")
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 1f * density
        }
        val borderRect = RectF(0.5f * density, 0.5f * density, width - 0.5f * density, height - 0.5f * density)
        canvas.drawRoundRect(borderRect, radius, radius, borderPaint)

        return bitmap
    }
}