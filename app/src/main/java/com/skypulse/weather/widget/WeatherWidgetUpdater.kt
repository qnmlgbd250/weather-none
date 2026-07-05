package com.skypulse.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
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
            val iconBitmap = renderIcon(context, info.icon)
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
     * Build a generated frosted glass bitmap sized to the actual widget dimensions with rounded corners.
     * RemoteViews cannot blur pixels behind the widget, so this avoids wallpaper reads and renders a
     * stable translucent lens surface from weather-tinted soft color fields.
     */
    private fun buildGradientBitmap(context: Context, skycon: String?, width: Int, height: Int, isDay: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val radius = 18f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        val gradientColors = WeatherUtils.getWeatherGradient(skycon, isDay).map { it.toArgb() }.toIntArray()
        val baseColors = glassBaseColors(gradientColors, isDay)

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                baseColors,
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = if (isDay) 218 else 226
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        drawBlurredAtmosphere(canvas, rect, radius, baseColors, isDay)

        // Weather tint acts like a translucent color mask over the generated blur field.
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                gradientColors,
                null,
                Shader.TileMode.CLAMP
            )
            alpha = if (isDay) 72 else 92
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Soft haze gives the generated surface the milky mask feel without turning it black.
        val frostAlpha = if (isDay) 0x6C else 0x56
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.argb(frostAlpha + 0x08, 255, 255, 255),
                    Color.argb(frostAlpha, 255, 255, 255),
                    Color.argb((frostAlpha * 0.58f).toInt(), 255, 255, 255)
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Central volume light for a clean, translucent feel.
        val coreAlpha = if (isDay) 0x34 else 0x22
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.4f,
                width * 0.6f,
                intArrayOf(Color.argb(coreAlpha, 255, 255, 255), Color.argb(coreAlpha / 3, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Soft satin highlight from the upper area, kept subtle and away from the edges.
        val satinAlpha = if (isDay) 0x24 else 0x1A
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.78f, height * 0.18f,
                width * 0.52f,
                intArrayOf(Color.argb(satinAlpha, 255, 255, 255), Color.argb(satinAlpha / 4, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Bottom-up soft shade anchors white text over bright wallpapers.
        val bottomAlpha = if (isDay) 0x24 else 0x34
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.55f, 0f, height.toFloat(),
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(bottomAlpha, 0, 0, 0)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        drawGlassGrain(canvas, rect, radius, density, isDay)

        return bitmap
    }

    private fun drawBlurredAtmosphere(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        gradientColors: IntArray,
        isDay: Boolean
    ) {
        val width = rect.width().toInt().coerceAtLeast(1)
        val height = rect.height().toInt().coerceAtLeast(1)
        val smallWidth = (width / 18).coerceAtLeast(14)
        val smallHeight = (height / 18).coerceAtLeast(14)
        val small = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        val smallCanvas = Canvas(small)
        val scaleX = smallWidth / width.toFloat()
        val scaleY = smallHeight / height.toFloat()

        fun sx(value: Float) = value * scaleX
        fun sy(value: Float) = value * scaleY

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sx(width * 0.14f), sy(height * 0.12f), smallWidth * 0.92f,
                intArrayOf(adjustAlpha(gradientColors.first(), 0xE0), adjustAlpha(gradientColors.first(), 0x00)),
                null,
                Shader.TileMode.CLAMP
            )
            smallCanvas.drawCircle(sx(width * 0.14f), sy(height * 0.12f), smallWidth * 0.92f, this)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sx(width * 0.82f), sy(height * 0.1f), smallWidth * 0.88f,
                intArrayOf(Color.argb(if (isDay) 150 else 88, 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            smallCanvas.drawCircle(sx(width * 0.82f), sy(height * 0.1f), smallWidth * 0.88f, this)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val last = gradientColors.last()
            shader = RadialGradient(
                sx(width * 0.58f), sy(height * 0.88f), smallWidth * 1.12f,
                intArrayOf(adjustAlpha(last, if (isDay) 0xCC else 0xA8), adjustAlpha(last, 0x00)),
                null,
                Shader.TileMode.CLAMP
            )
            smallCanvas.drawCircle(sx(width * 0.58f), sy(height * 0.88f), smallWidth * 1.12f, this)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val middle = gradientColors[gradientColors.size / 2]
            shader = RadialGradient(
                sx(width * 0.36f), sy(height * 0.5f), smallWidth * 1.05f,
                intArrayOf(adjustAlpha(middle, if (isDay) 0xB8 else 0x92), adjustAlpha(middle, 0x00)),
                null,
                Shader.TileMode.CLAMP
            )
            smallCanvas.drawOval(
                RectF(sx(width * -0.12f), sy(height * 0.16f), sx(width * 0.82f), sy(height * 0.94f)),
                this
            )
        }

        val blurredOnce = boxBlur(small, 8)
        val blurred = boxBlur(blurredOnce, 8)
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = if (isDay) 198 else 178
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            canvas.drawBitmap(blurred, null, rect, this)
            canvas.restore()
        }
        if (blurred !== small) small.recycle()
        if (blurred !== blurredOnce) blurredOnce.recycle()
        blurred.recycle()
    }

    private fun drawGlassGrain(canvas: Canvas, rect: RectF, radius: Float, density: Float, isDay: Boolean) {
        val width = rect.width().toInt().coerceAtLeast(1)
        val height = rect.height().toInt().coerceAtLeast(1)
        val step = (2.2f * density).coerceAtLeast(2f)
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1f
            alpha = if (isDay) 26 else 18
        }

        canvas.save()
        canvas.clipPath(path)
        var y = 0f
        var row = 0
        while (y < height) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < width) {
                val seed = ((x.toInt() * 31 + y.toInt() * 17 + row * 13) and 0xFF)
                paint.color = if (seed % 3 == 0) Color.WHITE else Color.BLACK
                paint.alpha = if (paint.color == Color.WHITE) {
                    if (isDay) 12 else 9
                } else {
                    if (isDay) 6 else 10
                }
                canvas.drawPoint(x, y, paint)
                x += step
            }
            row++
            y += step
        }
        canvas.restore()
    }

    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        val horizontal = IntArray(width * height)
        val output = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dx in -radius..radius) {
                    val px = (x + dx).coerceIn(0, width - 1)
                    val color = input[y * width + px]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                horizontal[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val py = (y + dy).coerceIn(0, height - 1)
                    val color = horizontal[py * width + x]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                output[y * width + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun glassBaseColors(colors: IntArray, isDay: Boolean): IntArray {
        val lift = if (isDay) 0.48f else 0.36f
        val coolNeutral = if (isDay) Color.rgb(236, 245, 250) else Color.rgb(126, 146, 164)
        return intArrayOf(
            mixColor(mixColor(colors.first(), coolNeutral, lift), Color.WHITE, if (isDay) 0.10f else 0.04f),
            mixColor(mixColor(colors[colors.size / 2], coolNeutral, lift + 0.08f), Color.WHITE, if (isDay) 0.12f else 0.05f),
            mixColor(mixColor(colors.last(), coolNeutral, lift + 0.12f), Color.WHITE, if (isDay) 0.16f else 0.08f)
        )
    }

    private fun mixColor(start: Int, end: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start) + (Color.red(end) - Color.red(start)) * f).toInt().coerceIn(0, 255),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * f).toInt().coerceIn(0, 255),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * f).toInt().coerceIn(0, 255)
        )
    }
