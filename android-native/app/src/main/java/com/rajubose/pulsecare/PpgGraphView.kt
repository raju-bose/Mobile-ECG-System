package com.rajubose.pulsecare

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class PpgGraphView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val samples = ArrayDeque<Float>()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF294653.toInt(); strokeWidth = 1f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF26C6A5.toInt(); strokeWidth = 3.5f; style = Paint.Style.STROKE
    }
    private val path = Path()

    @Synchronized fun push(value: Float) {
        samples.addLast(value)
        while (samples.size > 180) samples.removeFirst()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..4) canvas.drawLine(0f, h * i / 5f, w, h * i / 5f, gridPaint)
        for (i in 1..8) canvas.drawLine(w * i / 9f, 0f, w * i / 9f, h, gridPaint)
        val values = synchronized(this) { samples.toList() }
        if (values.size < 2) return
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(0.0001f)
        path.reset()
        values.forEachIndexed { index, v ->
            val x = index * w / (values.size - 1)
            val y = h - ((v - min) / range) * (h * 0.78f) - h * 0.11f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}
