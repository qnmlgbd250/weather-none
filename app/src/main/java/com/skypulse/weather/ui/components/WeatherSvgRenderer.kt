package com.skypulse.weather.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG

object WeatherSvgRenderer {
    fun renderBitmap(context: Context, icon: String, sizePx: Int): Bitmap? {
        return try {
            val assetPath = "meteocons/fill/$icon.svg"
            val svg = context.assets.open(assetPath).use { SVG.getFromInputStream(it) }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            svg.documentWidth = sizePx.toFloat()
            svg.documentHeight = sizePx.toFloat()
            svg.renderToCanvas(canvas)

            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
