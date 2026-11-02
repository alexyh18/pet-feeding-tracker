package com.example.petfeeding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * A single calendar day cell. When one or more pets were fed that day, the cell is
 * split into N equal vertical stripes — one per pet, in each pet's color. The day
 * number is drawn centered on top, and today's cell gets an outline.
 */
class DayCellView(context: Context) : View(context) {

    var day: Int = 0
    var colors: List<Int> = emptyList()   // pet colors fed that day (empty = not fed)
    var isToday: Boolean = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xFFFF87A3.toInt() // pink_dark
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val emptyBg = 0xFFFFFDF9.toInt() // cream

    private val rect = RectF()
    private val radius get() = dp(10f)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        val r = radius

        if (colors.isEmpty()) {
            fill.color = emptyBg
            canvas.drawRoundRect(rect, r, r, fill)
        } else {
            // Draw N equal stripes, clipped to the rounded-rect shape.
            val save = canvas.save()
            val clip = android.graphics.Path().apply { addRoundRect(rect, r, r, android.graphics.Path.Direction.CW) }
            canvas.clipPath(clip)
            val n = colors.size
            val stripeW = w / n
            for (i in 0 until n) {
                fill.color = colors[i]
                canvas.drawRect(i * stripeW, 0f, (i + 1) * stripeW, h, fill)
            }
            canvas.restoreToCount(save)
        }

        if (isToday) {
            val inset = stroke.strokeWidth / 2f
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, r, r, stroke)
        }

        if (day > 0) {
            text.textSize = h * 0.42f
            // Dark text on empty cells, white text when there are colored stripes.
            text.color = if (colors.isEmpty()) 0xFF5A4A52.toInt() else Color.WHITE
            text.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val cx = width / 2f
            val cy = height / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(day.toString(), cx, cy, text)
        }
    }
}
