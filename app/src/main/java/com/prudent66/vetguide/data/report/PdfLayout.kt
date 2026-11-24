package com.prudent66.vetguide.data.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

internal fun paint(color: Int, size: Float, style: Int = Typeface.NORMAL): Paint =
    Paint().apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, style)
        isAntiAlias = true
    }

internal fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> = buildList {
    text.split("\n").forEach { raw ->
        var current = ""
        raw.split(" ").forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) current = candidate
            else {
                if (current.isNotEmpty()) add(current)
                current = word
            }
        }
        add(current)
    }
}

/**
 * Draws onto one active PdfDocument page at a time. The previous page is
 * always finished (footer drawn, finishPage called) before a new one starts.
 */
internal class PdfCursor(
    private val document: PdfDocument,
    private val totalPages: Int = 1,
) {
    private val lifecycle = PdfPageLifecycle()
    private var active: PdfDocument.Page? = null

    val canvas: Canvas get() = checkNotNull(active) { "No active PDF page" }.canvas
    val pageCount: Int get() = lifecycle.pageCount
    var y = EDGE + 20f

    fun text(value: String, x: Float, baseline: Float, p: Paint) {
        canvas.drawText(value, x, baseline, p)
    }

    fun rect(bounds: RectF, fill: Int, border: Int? = null) {
        canvas.drawRect(bounds, Paint().apply {
            color = fill
            style = Paint.Style.FILL
        })
        border?.let { stroke ->
            canvas.drawRect(bounds, Paint().apply {
                color = stroke
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
        }
    }

    fun image(bitmap: Bitmap, bounds: RectF) {
        canvas.drawBitmap(bitmap, null, bounds, paint(INK, 11f))
    }

    fun box(title: String, lines: List<String>, accent: Int, fill: Int) {
        val h = maxOf(46f, lines.size * 15f + 30f)
        val top = y
        rect(RectF(EDGE, top, EDGE + WIDTH, top + h), fill, BORDER)
        rect(RectF(EDGE, top, EDGE + 6f, top + h), accent)
        text(title, EDGE + 18f, top + 20f, paint(accent, 12f, Typeface.BOLD))
        var baseline = top + 38f
        lines.take(12).forEach { line ->
            text(line, EDGE + 18f, baseline, paint(INK, 10f))
            baseline += 15f
        }
        y = top + h + 12f
    }

    fun ensure(required: Float) {
        if (y + required > PAGE_H - 56f) startPage()
    }

    /** Must be called exactly once, before writeTo/close, to finish the last page. */
    fun finish() {
        finishActive()
        lifecycle.finishAll()
    }

    private fun startPage() {
        finishActive()
        val number = lifecycle.start()
        active = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create(),
        )
        y = EDGE + 20f
    }

    /** Draws the branded footer then finishes the current page. */
    private fun finishActive() {
        val page = active ?: return
        val number = lifecycle.activePage ?: 1
        val footer = paint(MUTED, 9f)
        page.canvas.drawText(
            "VETGUIDE Clinical Decision Support — Not a Substitute for Veterinary Examination • © 2026 Veloxdigitalforge",
            EDGE,
            PAGE_H - 24f,
            footer,
        )
        page.canvas.drawText(
            "Page $number of $totalPages",
            PAGE_W - EDGE - 80f,
            PAGE_H - 24f,
            footer,
        )
        document.finishPage(page)
        active = null
        lifecycle.finish()
    }

    init {
        startPage()
    }
}
