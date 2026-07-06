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
                views.setTextViewText(R.id.widget_detail, "\u5b9a\u4f4d\u4e2d...")
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }
                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, null, w, h)
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
            val skycon = realtime?.skycon
            val isDay = WeatherUtils.isCurrentlyDay(daily)
            val info = WeatherUtils.getWeatherInfo(skycon)
            val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
            val todayTemp = WeatherUtils.todayTemperature(daily)
            val maxTemp = todayTemp?.max?.let { WeatherUtils.formatTemperature(it) } ?: "--"
            val minTemp = todayTemp?.min?.let { WeatherUtils.formatTemperature(it) } ?: "--"
            val cityText = shortenLocation(cityName ?: "--")
            val detailText = "${info.description}  $minTemp / $maxTemp"
            FileLogger.i(TAG, "updateAll: \u6e32\u67d3\u6570\u636e \u2014 city=$cityText, temp=$tempText, " +
                "detail=$detailText, skycon=$skycon, isDay=$isDay, icon=${info.icon}, " +
                "widgetCount=${ids.size}")
            val precipitationColor = WeatherUtils.getPrecipitationIconColor(skycon, isDay).toArgb()
            val iconBitmap = renderIcon(context, info.icon, precipitationColor)
            if (iconBitmap == null) {
                FileLogger.w(TAG, "updateAll: \u56fe\u6807\u6e32\u67d3\u5931\u8d25 skycon=$skycon, icon=${info.icon}")
            }
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
            FileLogger.i(TAG, "updateAll: \u2713 \u6e32\u67d3\u5b8c\u6210, \u66f4\u65b0\u4e86 ${ids.size} \u4e2a widget")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateAll: \u6e32\u67d3\u5f02\u5e38\uff0c\u5c1d\u8bd5\u663e\u793a\u9ed8\u8ba4\u72b6\u6001", e)
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
                FileLogger.i(TAG, "updateAll: \u9ed8\u8ba4\u72b6\u6001\u6e32\u67d3\u5b8c\u6210")
            } catch (e2: Exception) {
                FileLogger.e(TAG, "updateAll: \u9ed8\u8ba4\u72b6\u6001\u6e32\u67d3\u4e5f\u5931\u8d25", e2)
            }
        }
    }

    private fun shortenLocation(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "--"
        if (value == "定位中...") return value

        val districtMatch = Regex("([^\u7701\u5e02\u533a\u53bf]+[\u533a\u53bf])").find(value)
        if (districtMatch != null) return districtMatch.groupValues[1]

        val cityMatch = Regex("([^\u7701\u5e02]+[\u5e02])").find(value)
        if (cityMatch != null) return cityMatch.groupValues[1]

        val segment = value.split(Regex("[\\s,\uff0c\u3001\u3002]")).firstOrNull { it.length >= 2 } ?: value
        return if (segment.length > 4) segment.substring(0, 4) else segment
    }

    private fun renderIcon(context: Context, icon: String, precipitationColor: Int? = null): Bitmap? {
        val cacheKey = if (precipitationColor == null) icon else "$icon:$precipitationColor"
        iconCache.get(cacheKey)?.let { return it }
        val bitmap = when (icon) {
            "clear-night" -> renderMoonBitmap(context)
            else -> renderSvgIcon(context, icon, precipitationColor)
        }
        if (bitmap != null) iconCache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun renderSvgIcon(context: Context, icon: String, precipitationColor: Int?): Bitmap? {
        val size = (96 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return WeatherSvgRenderer.renderBitmap(context, icon, size, precipitationColor)
    }

    /**
     * Hand-drawn moon icon matching the Compose MoonIcon in WeatherIcon.kt.
     * Uses the same preserved Meteocons moon bezier path with warm golden gradient.
     */
    private fun renderMoonBitmap(context: Context): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val size = (96 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = size / 128f

            // Preserved Meteocons moon path (128x128 canvas)
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

    /** Build an opaque, flat weather gradient sized to the actual widget dimensions. */
    private fun buildGradientBitmap(context: Context, skycon: String?, width: Int, height: Int, isDay: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val radius = 18f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val isRain = isRainSkycon(skycon)
        val gradientColors = weatherWidgetGradient(skycon, isDay)

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), gradientColors, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.5f,
                intArrayOf(Color.argb(if (isDay) 32 else 18, 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.58f, 0f, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(if (isDay) 34 else 48, 0, 0, 0)),
                null,
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
