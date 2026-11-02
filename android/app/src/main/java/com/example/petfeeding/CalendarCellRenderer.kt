package com.example.petfeeding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Renders a single calendar day cell to a Bitmap so the home-screen widget (which can
 * only use RemoteViews / ImageViews, not custom Views) can show N-way color splits.
 */
object CalendarCellRenderer {

    private const val SIZE = 96 // px; square cell bitmap, scaled by the ImageView

    fun render(day: Int?, colors: List<Int>, isToday: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = SIZE.toFloat()
        val h = SIZE.toFloat()
        val r = 14f
        val rect = RectF(2f, 2f, w - 2f, h - 2f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        if (day == null) {
            return bmp // transparent blank
        }

        if (colors.isEmpty()) {
            fill.color = 0xFFFFFDF9.toInt() // cream
            canvas.drawRoundRect(rect, r, r, fill)
        } else {
            val save = canvas.save()
            val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
            canvas.clipPath(clip)
            val n = colors.size
            val stripeW = (rect.width()) / n
            for (i in 0 until n) {
                fill.color = colors[i]
                canvas.drawRect(
                    rect.left + i * stripeW, rect.top,
                    rect.left + (i + 1) * stripeW, rect.bottom, fill
                )
            }
            canvas.restoreToCount(save)
        }

        if (isToday) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = 0xFFFF87A3.toInt()
            }
            canvas.drawRoundRect(rect, r, r, stroke)
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = h * 0.4f
            color = if (colors.isEmpty()) 0xFF5A4A52.toInt() else Color.WHITE
            isFakeBoldText = isToday
        }
        val cx = w / 2f
        val cy = h / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(day.toString(), cx, cy, text)

        return bmp
    }
}
