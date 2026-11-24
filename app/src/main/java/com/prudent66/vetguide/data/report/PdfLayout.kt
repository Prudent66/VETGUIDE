package com.prudent66.vetguide.data.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

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

    /** Draws a titled card, wrapping every body line inside the card border. */
    fun box(
        title: String,
        lines: List<String>,
        accent: Int,
        fill: Int,
        bodySize: Float = 10f,
    ) {
        val body = paint(INK, bodySize)
        val wrapped = lines.flatMap { line -> wrap(line, body, innerContentWidth(WIDTH)) }
        if (y + 60f > PAGE_H - 56f) startPage()
        paginate(wrapped).forEachIndexed { index, chunk ->
            if (index > 0) startPage()
            drawBoxChunk(title, chunk, accent, fill, body, continuation = index > 0)
        }
    }

    private fun drawBoxChunk(
        title: String,
        lines: List<String>,
        accent: Int,
        fill: Int,
        body: Paint,
        continuation: Boolean,
    ) {
        val h = maxOf(46f, lines.size * 15f + 30f)
        val top = y
        rect(RectF(EDGE, top, EDGE + WIDTH, top + h), fill, BORDER)
        rect(RectF(EDGE, top, EDGE + 6f, top + h), accent)
        val heading = if (continuation) "$title (cont.)" else title
        text(heading, EDGE + BOX_PADDING, top + 20f, paint(accent, 12f, Typeface.BOLD))
        var baseline = top + 38f
        lines.forEach { line ->
            text(line, EDGE + BOX_PADDING, baseline, body)
            baseline += 15f
        }
        y = top + h + 12f
    }

    /** Splits wrapped lines into page-sized chunks (current page first). */
    private fun paginate(lines: List<String>): List<List<String>> {
        if (lines.isEmpty()) return listOf(emptyList())
        val pageBottom = PAGE_H - 56f
        val firstCapacity = ((pageBottom - y - 42f) / 15f).toInt().coerceAtLeast(1)
        val restCapacity = ((pageBottom - (EDGE + 20f) - 42f) / 15f).toInt().coerceAtLeast(1)
        val chunks = mutableListOf<List<String>>()
        var index = 0
        var capacity = firstCapacity
        while (index < lines.size) {
            val end = minOf(index + capacity, lines.size)
            chunks += lines.subList(index, end)
            index = end
            capacity = restCapacity
        }
        return chunks
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
