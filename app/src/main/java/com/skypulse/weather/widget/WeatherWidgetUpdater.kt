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
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.util.WeatherUtils

object WeatherWidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val cities = CityManager(context).getCities()
        val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
        val weather = city?.let { WeatherCache(context).load(it.id) }

        val realtime = weather?.result?.realtime
        val daily = weather?.result?.daily
        val skycon = realtime?.skycon
        val info = WeatherUtils.getWeatherInfo(skycon)
        val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
        val maxTemp = daily?.temperature?.firstOrNull()?.max?.let { WeatherUtils.formatTemperature(it) } ?: "--"
        val minTemp = daily?.temperature?.firstOrNull()?.min?.let { WeatherUtils.formatTemperature(it) } ?: "--"
        val cityText = shortenLocation(city?.name ?: "--")
        val detailText = "${info.description} $maxTemp/$minTemp"
        val iconBitmap = renderIcon(context, info.icon)
        val bgBitmap = buildGradientBitmap(context, skycon)

        ids.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_small)
            views.setTextViewText(R.id.widget_city, cityText)
            views.setTextViewText(R.id.widget_temp, tempText)
            views.setTextViewText(R.id.widget_detail, detailText)
            if (iconBitmap != null) {
                views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
            }
            views.setImageViewBitmap(R.id.widget_root, bgBitmap)

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun shortenLocation(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "--"
        var text = value
            .replace("街道办事处", "")
            .replace("街道", "")
            .replace("镇", "")
            .replace("乡", "")
            .replace("区", "")
            .replace("市", "")
            .replace("县", "")
            .replace("路", "")
            .replace("社区", "")
            .replace("村", "")
        if (text.length > 3) {
            val candidate = text
                .replace("道", "")
                .replace("大街", "")
                .replace("路", "")
            if (candidate.isNotBlank()) text = candidate
        }
        if (text.length > 3) text = text.substring(0, 3)
        return text
    }

    private fun renderIcon(context: Context, icon: String): Bitmap? {
        return try {
            val composition = LottieCompositionFactory.fromAssetSync(context, "meteocons/fill/$icon.json").value
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

    private fun buildGradientBitmap(context: Context, skycon: String?): Bitmap {
        val width = (220 * context.resources.displayMetrics.density).toInt()
        val height = (160 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = WeatherUtils.getWeatherGradient(skycon).map { it.hashCode() }.toIntArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val r = 18 * context.resources.displayMetrics.density
        canvas.drawRoundRect(rect, r, r, paint)
        return bitmap
    }
}
