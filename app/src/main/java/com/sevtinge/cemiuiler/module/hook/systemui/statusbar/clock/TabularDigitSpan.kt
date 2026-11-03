package com.sevtinge.cemiuiler.module.hook.systemui.statusbar.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.max

/**
 * 让一个数字字符占用固定宽度（等于当前字体中最宽的数字），
 * 这样秒、日期等位置不会因为 "1" 比 "0" 窄而左右抖动，不依赖字体是否支持等宽数字。
 */
class TabularDigitSpan : ReplacementSpan() {

    private class WidthCache(
        val textSize: Float,
        val typeface: Any?,
        val fakeBold: Boolean,
        val letterSpacing: Float,
        val width: Int
    )

    // 单项缓存：同一个字号 / 字体下最宽数字的宽度不变，避免每个数字每次测量 / 绘制都量 10 个字形
    @Volatile
    private var cache: WidthCache? = null

    private fun cellWidth(paint: Paint): Int {
        val cached = cache
        if (cached != null
            && cached.textSize == paint.textSize
            && cached.typeface === paint.typeface
            && cached.fakeBold == paint.isFakeBoldText
            && cached.letterSpacing == paint.letterSpacing
        ) {
            return cached.width
        }
        var widest = 0f
        for (c in '0'..'9') {
            widest = max(widest, paint.measureText(c.toString()))
        }
        val width = ceil(widest.toDouble()).toInt()
        cache = WidthCache(paint.textSize, paint.typeface, paint.isFakeBoldText, paint.letterSpacing, width)
        return width
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
            fm.top = metrics.top
            fm.bottom = metrics.bottom
        }
        return cellWidth(paint)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        if (text == null) return
        val digit = text.subSequence(start, end).toString()
        val offset = (cellWidth(paint) - paint.measureText(digit)) / 2f
        canvas.drawText(digit, x + offset, y.toFloat(), paint)
    }
}
