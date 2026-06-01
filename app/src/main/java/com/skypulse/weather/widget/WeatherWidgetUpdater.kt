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
import androidx.compose.ui.graphics.toArgb
import com.skypulse.weather.util.WeatherUtils

object WeatherWidgetUpdater {

    fun updateAll(context: Context) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val cities = CityManager(context, com.squareup.moshi.Moshi.Builder().build()).getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
            val weather = city?.let { WeatherCache(context, com.squareup.moshi.Moshi.Builder().build()).load(it.id) }

            val realtime = weather?.result?.realtime
            val daily = weather?.result?.daily
            val skycon = realtime?.skycon
            val info = WeatherUtils.getWeatherInfo(skycon)
            val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
            val maxTemp = daily?.temperature?.firstOrNull()?.max?.let { WeatherUtils.formatTemperature(it) } ?: "--"
            val minTemp = daily?.temperature?.firstOrNull()?.min?.let { WeatherUtils.formatTemperature(it) } ?: "--"
            val cityText = shortenLocation(city?.name ?: "--")
            val detailText = "${info.description}  $minTemp / $maxTemp"
            val iconBitmap = renderIcon(context, info.icon)
            val bgBitmap = buildGradientBitmap(skycon)
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_small)
                views.setTextViewText(R.id.widget_city, cityText)
                views.setTextViewText(R.id.widget_temp, tempText)
                views.setTextViewText(R.id.widget_detail, detailText)
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }
                views.setImageViewBitmap(R.id.widget_bg, bgBitmap)
                views.setImageViewResource(R.id.widget_pin, R.drawable.ic_widget_location)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(widgetId, views)
            }
        } catch (_: Exception) {
            // Widget will retry on next update cycle
        }
    }

    private fun shortenLocation(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "--"

        // Try to extract district/county name: "XX区", "XX县", "XX市" (county-level)
        val districtMatch = Regex("([^省市区县]+[区县])").find(value)
        if (districtMatch != null) return districtMatch.groupValues[1]

        // Try to extract city name: "XX市"
        val cityMatch = Regex("([^省市]+[市])").find(value)
        if (cityMatch != null) return cityMatch.groupValues[1]

        // Fallback: take first meaningful segment (before space or punctuation)
        val segment = value.split(Regex("[\\s,，、·]")).firstOrNull { it.length >= 2 } ?: value
        return if (segment.length > 4) segment.substring(0, 4) else segment
    }



    private fun renderIcon(context: Context, icon: String): Bitmap? {
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

    private fun buildGradientBitmap(skycon: String?): Bitmap {
        val width = 2
        val height = 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = WeatherUtils.getWeatherGradient(skycon).map { it.toArgb() }.toIntArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}
