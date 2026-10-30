package com.spacedoku.app.content.cube

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders the digit glyph in WHITE on a transparent bitmap, cached by value. The actual digit COLOR
 * is applied by tinting the overlay material's baseColor — so color changes (selected/conflict/given)
 * never need a new texture; only a new VALUE does.
 */
class DigitTextures(private val glyphPx: Int = 128) {

    private val bitmaps = HashMap<Int, Bitmap>()

    fun glyphBitmap(value: Int): Bitmap =
        bitmaps.getOrPut(value) {
            val bmp = Bitmap.createBitmap(glyphPx, glyphPx, Bitmap.Config.ARGB_8888) // transparent
            if (value > 0) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt() // white; tinted per-cell via material baseColor
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = glyphPx * (if (value >= 10) 0.42f else 0.56f)
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = glyphPx * 0.012f
                }
                val cx = glyphPx / 2f
                val baseline = glyphPx / 2f - (paint.descent() + paint.ascent()) / 2f
                Canvas(bmp).drawText(value.toString(), cx, baseline, paint)
            }
            bmp
        }
}
