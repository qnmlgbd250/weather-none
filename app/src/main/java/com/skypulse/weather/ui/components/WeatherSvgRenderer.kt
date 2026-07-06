package com.skypulse.weather.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream

object WeatherSvgRenderer {
    private const val DefaultRaindropStroke = "#0A5AD4"

    fun renderBitmap(
        context: Context,
        icon: String,
        sizePx: Int,
        precipitationColor: Int? = null
    ): Bitmap? {
        return try {
            val assetPath = "meteocons/fill/$icon.svg"
            val svg = context.assets.open(assetPath).use { input ->
                val svgText = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val tunedSvgText = precipitationColor?.let { color ->
                    svgText.replace(DefaultRaindropStroke, color.toSvgRgb())
                } ?: svgText
                SVG.getFromInputStream(ByteArrayInputStream(tunedSvgText.toByteArray(Charsets.UTF_8)))
            }
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

    private fun Int.toSvgRgb(): String {
        val red = this shr 16 and 0xFF
        val green = this shr 8 and 0xFF
        val blue = this and 0xFF
        return "#%02X%02X%02X".format(red, green, blue)
    }
}
