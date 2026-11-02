package com.example.petfeeding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tiny self-contained line chart for a pet's daily weight (grams). Draws a filled
 * line with point markers, a min/max Y axis, and a few date labels on the X axis.
 * No external chart library.
 */
class WeightGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** (dayStart, grams) sorted ascending. */
    var series: List<Pair<Long, Double>> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var lineColor: Int = 0xFF7FD1A8.toInt()
        set(value) { field = value; invalidate() }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD8CDD2.toInt()
        strokeWidth = dp(1f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9B8A92.toInt()
        textSize = dp(10f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A4A52.toInt()
        textSize = dp(10f)
    }

    private val dateFmt = SimpleDateFormat("MM.dd", Locale.getDefault())

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = dp(40f)   // room for Y labels
        val padR = dp(10f)
        val padT = dp(10f)
        val padB = dp(22f)   // room for X labels

        val plotW = w - padL - padR
        val plotH = h - padT - padB

        if (series.isEmpty()) {
            textPaint.textSize = dp(12f)
            canvas.drawText(
                "No weight entries yet", padL, padT + plotH / 2f, textPaint
            )
            return
        }

        val values = series.map { it.second }
        var minV = values.min()
        var maxV = values.max()
        if (minV == maxV) { // single value / flat: pad the range so the line is visible
            minV -= 1.0
            maxV += 1.0
        }
        // Add 5% headroom.
        val range = maxV - minV
        minV -= range * 0.05
        maxV += range * 0.05
        val span = (maxV - minV).coerceAtLeast(0.0001)

        // Axes
        canvas.drawLine(padL, padT, padL, padT + plotH, axisPaint)
        canvas.drawLine(padL, padT + plotH, padL + plotW, padT + plotH, axisPaint)

        // Y labels (max at top, min at bottom)
        textPaint.textSize = dp(10f)
        canvas.drawText(fmtG(maxV), dp(4f), padT + textPaint.textSize, textPaint)
        canvas.drawText(fmtG(minV), dp(4f), padT + plotH, textPaint)

        val n = series.size
        fun xAt(i: Int): Float =
            if (n == 1) padL + plotW / 2f else padL + plotW * i / (n - 1)
        fun yAt(v: Double): Float =
            padT + plotH - ((v - minV) / span * plotH).toFloat()

        // Build the line path + a fill under it.
        val line = Path()
        val fill = Path()
        series.forEachIndexed { i, (_, v) ->
            val x = xAt(i); val y = yAt(v)
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, padT + plotH); fill.lineTo(x, y) }
            else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(xAt(n - 1), padT + plotH)
        fill.close()

        fillPaint.color = withAlpha(lineColor, 40) // translucent fill under the line
        canvas.drawPath(fill, fillPaint)

        linePaint.color = lineColor
        canvas.drawPath(line, linePaint)

        // Point markers
        dotPaint.color = lineColor
        series.forEachIndexed { i, (_, v) ->
            canvas.drawCircle(xAt(i), yAt(v), dp(3f), dotPaint)
        }

        // X labels: first, middle, last (avoid clutter)
        val idxs = if (n <= 3) series.indices.toList()
            else listOf(0, n / 2, n - 1)
        idxs.forEach { i ->
            val label = dateFmt.format(Date(series[i].first))
            val tw = labelPaint.measureText(label)
            var x = xAt(i) - tw / 2f
            x = x.coerceIn(padL, padL + plotW - tw)
            canvas.drawText(label, x, h - dp(6f), labelPaint)
        }
    }

    private fun fmtG(grams: Double): String =
        if (grams >= 1000) "%.1fkg".format(grams / 1000.0) else "%.0fg".format(grams)

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha shl 24) or (color and 0x00FFFFFF)
}
