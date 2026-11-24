package com.prudent66.vetguide.data.report

import android.graphics.Canvas
import android.graphics.Paint
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

/** Collects pages so footers can carry an accurate "Page X of Y". */
internal class PdfCursor(private val document: PdfDocument) {

    val canvas: Canvas get() = pages.last().canvas
    var y = EDGE + 20f

    private val pages = mutableListOf<PdfDocument.Page>()

    fun text(value: String, x: Float, baseline: Float, p: Paint) {
        canvas.drawText(value, x, baseline, p)
    }

    fun rect(left: Float, top: Float, right: Float, bottom: Float, fill: Int, border: Int? = null) {
        canvas.drawRect(left, top, right, bottom, Paint().apply {
            color = fill
            style = Paint.Style.FILL
        })
        border?.let { stroke ->
            canvas.drawRect(left, top, right, bottom, Paint().apply {
                color = stroke
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
        }
    }

    fun box(title: String, lines: List<String>, accent: Int, fill: Int) {
        val h = maxOf(46f, lines.size * 15f + 30f)
        val top = y
        rect(EDGE, top, EDGE + WIDTH, top + h, fill, BORDER)
        rect(EDGE, top, EDGE + 6f, top + h, accent)
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

    fun line(value: String, p: Paint) {
        ensure(p.textSize + 6f)
        text(value, EDGE, y, p)
        y += p.textSize + 6f
    }

    fun finish() {
        val total = pages.size
        pages.forEachIndexed { index, page ->
            val footer = paint(MUTED, 9f)
            page.canvas.drawText(
                "VETGUIDE Clinical Decision Support — Not a Substitute for Veterinary Examination • © 2026 Veloxdigitalforge",
                EDGE,
                PAGE_H - 24f,
                footer,
            )
            page.canvas.drawText(
                "Page ${index + 1} of $total",
                PAGE_W - EDGE - 80f,
                PAGE_H - 24f,
                footer,
            )
            document.finishPage(page)
        }
        pages.clear()
    }

    private fun startPage() {
        pages.add(
            document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pages.size + 1).create(),
            ),
        )
        y = EDGE + 20f
    }

    init {
        startPage()
    }
}
