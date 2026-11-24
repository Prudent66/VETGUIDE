package com.prudent66.vetguide.data.report

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.prudent66.vetguide.data.ai.ImageEncoder
import com.prudent66.vetguide.domain.model.SoapPayload

internal const val PAGE_W = 595
internal const val PAGE_H = 842
internal const val EDGE = 32f
internal const val WIDTH = PAGE_W - 2 * EDGE

internal val FOREST = Color.rgb(0x1B, 0x43, 0x32)
internal val ACCENT = Color.rgb(0x2D, 0x6A, 0x4F)
internal val TINT = Color.rgb(0xF1, 0xF8, 0xF5)
internal val BORDER = Color.rgb(0xB7, 0xE4, 0xC7)
internal val INK = Color.rgb(0x1F, 0x29, 0x25)
internal val MUTED = Color.rgb(0x5A, 0x6B, 0x63)

/** Extended report data: optional sections keep older call sites source-compatible. */
data class HospitalReportData(
    val patientLine: String,
    val ownerLine: String? = null,
    val caseId: String,
    val complaint: String,
    val status: String,
    val generatedAt: String,
    val soap: SoapPayload,
    val narrative: String? = null,
    val imageBase64: String? = null,
    val clinicName: String? = null,
    val vetName: String? = null,
    val clinicContact: String? = null,
    val animalCategory: String = "Domestic",
    val differentials: List<Pair<String, Double>> = emptyList(),
    val prescriptions: List<String> = emptyList(),
    val supportiveCare: List<String> = emptyList(),
)

/** Builds a hospital-style A4 clinical report with branded header and footers. */
object PdfReportExporter {

    fun build(data: HospitalReportData): PdfDocument {
        // First pass counts pages so "Page X of Y" is accurate; the real pass
        // finishes every page before starting the next one.
        val total = countPages(data)
        val document = PdfDocument()
        val cursor = PdfCursor(document, total)
        render(data, cursor)
        cursor.finish()
        return document
    }

    private fun countPages(data: HospitalReportData): Int {
        val probe = PdfDocument()
        return try {
            val cursor = PdfCursor(probe)
            render(data, cursor)
            cursor.finish()
            cursor.pageCount
        } finally {
            probe.close()
        }
    }

    private fun render(data: HospitalReportData, c: PdfCursor) {
        drawHeader(c, data)
        drawMetadata(c, data)
        drawSoap(c, data)
        if (data.differentials.isNotEmpty()) drawDifferentials(c, data.differentials)
        drawPrescriptions(c, data)
        drawImage(c, data.imageBase64)
    }

    private fun drawHeader(c: PdfCursor, d: HospitalReportData) {
        val h = 118f
        c.rect(RectF(0f, 0f, PAGE_W.toFloat(), h), FOREST)
        val title = paint(Color.WHITE, 26f, Typeface.BOLD)
        val sub = paint(Color.WHITE, 11f)
        c.text("VETGUIDE", EDGE, 48f, title)
        c.text("VETERINARY CLINICAL CASE REPORT", EDGE, 70f, sub)
        c.text("Case ${d.caseId.take(8)}", PAGE_W - EDGE - 120f, 48f, sub)
        c.text(d.generatedAt, PAGE_W - EDGE - 150f, 70f, sub)
        c.y = h + 20f
    }

    private fun drawMetadata(c: PdfCursor, d: HospitalReportData) {
        val labelPaint = paint(MUTED, 9f, Typeface.BOLD)
        val valuePaint = paint(INK, 11f)
        val valueX = EDGE + 150f
        val valueWidth = (EDGE + WIDTH - 10f - valueX).coerceAtLeast(60f)
        val rows = listOf(
            "Patient" to d.patientLine,
            "Category" to d.animalCategory,
            "Owner" to (d.ownerLine ?: "—"),
            "Case status" to d.status,
            "Presenting complaint" to d.complaint,
            "Clinic / veterinarian" to listOfNotNull(d.clinicName, d.vetName)
                .joinToString(" · ").ifBlank { "—" },
        ).map { (label, value) -> label to wrap(value, valuePaint, valueWidth) }
        val rowHeights = rows.map { (_, lines) -> maxOf(20f, lines.size * 15f) + 10f }
        val boxH = rowHeights.sum() + 10f
        c.ensure(boxH + 16f)
        val top = c.y
        c.rect(RectF(EDGE, top, EDGE + WIDTH, top + boxH), TINT, BORDER)
        var baseline = top + 22f
        rows.forEachIndexed { index, (label, lines) ->
            c.text(label, EDGE + 10f, baseline, labelPaint)
            lines.forEachIndexed { lineIndex, line ->
                c.text(line, valueX, baseline + lineIndex * 15f, valuePaint)
            }
            baseline += rowHeights[index]
        }
        c.y = top + boxH + 18f
    }

    private fun drawSoap(c: PdfCursor, d: HospitalReportData) {
        val objective = if (d.narrative.isNullOrBlank()) {
            d.soap.objective
        } else {
            val marker = "Analysis Finding:"
            val idx = d.soap.objective.indexOf(marker, ignoreCase = true)
            if (idx >= 0) d.soap.objective.substring(0, idx).trim() else d.soap.objective
        }
        listOf(
            SUBJECTIVE_TITLE to d.soap.subjective,
            OBJECTIVE_TITLE to objective,
            ASSESSMENT_TITLE to d.soap.assessment,
        ).forEach { (title, body) ->
            if (body.isNotBlank()) c.box(title, listOf(body), ACCENT, Color.WHITE, bodySize = 11f)
        }
    }

    private fun drawDifferentials(c: PdfCursor, items: List<Pair<String, Double>>) {
        val titleH = 30f
        val pad = 12f
        val namePaint = paint(INK, 10f)
        val pctPaint = paint(MUTED, 10f)
        val pctRight = EDGE + WIDTH - 8f
        val rows = items.take(6).map { (name, confidence) ->
            val pct = (confidence * 100).toInt().coerceIn(0, 100)
            val pctText = "$pct%"
            val pctWidth = pctPaint.measureText(pctText)
            val nameWidth = (pctRight - pctWidth - 8f - (EDGE + pad)).coerceAtLeast(60f)
            Triple(wrap(name, namePaint, nameWidth), pctText, pctWidth)
        }
        val rowsHeight = rows.fold(0f) { total, row -> total + rowRowHeight(row.first.size) }
        val boxH = titleH + rowsHeight + 12f
        c.ensure(boxH + 10f)
        val top = c.y
        c.rect(RectF(EDGE, top, EDGE + WIDTH, top + boxH), Color.WHITE, BORDER)
        c.text("Ranked Suspected Conditions", EDGE + pad, top + 20f, paint(ACCENT, 12f, Typeface.BOLD))
        var y = top + titleH
        rows.forEach { (lines, pctText, pctWidth) ->
            val rowH = rowRowHeight(lines.size)
            lines.forEachIndexed { index, line -> c.text(line, EDGE + pad, y + index * 13f, namePaint) }
            c.text(pctText, pctRight - pctWidth, y, pctPaint)
            val barTop = y + rowH - 10f
            c.rect(RectF(EDGE + pad, barTop, EDGE + WIDTH - 56f, barTop + 8f), TINT)
            c.rect(
                RectF(EDGE + pad, barTop, EDGE + pad + (WIDTH - 68f) * rowPercent(pctText) / 100f, barTop + 8f),
                ACCENT,
            )
            y += rowH
        }
        c.y = top + boxH + 12f
    }

    private fun rowRowHeight(lineCount: Int): Float = maxOf(14f, lineCount * 13f) + 14f

    private fun rowPercent(pctText: String): Int = pctText.removeSuffix("%").toIntOrNull() ?: 0

    private fun drawPrescriptions(c: PdfCursor, d: HospitalReportData) {
        val rx = planPrescriptions(d)
        val care = planSupportiveCare(d)
        if (rx.isEmpty() && care.isEmpty()) return
        c.ensure(90f)
        c.text(PLAN_TITLE, EDGE, c.y, paint(ACCENT, 12f, Typeface.BOLD))
        c.y += 26f
        c.box("Prescriptions & Dosages", rx, FOREST, TINT)
        c.box("Supportive Care & Biosecurity", care, ACCENT, Color.WHITE)
    }

    private fun drawImage(c: PdfCursor, base64: String?) {
        if (base64.isNullOrBlank()) return
        val bitmap = try {
            val bytes = ImageEncoder.fromBase64(base64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("PdfReportExporter", "Case image could not be decoded", e)
            null
        } ?: return
        val scale = minOf((WIDTH - 16f) / bitmap.width, 220f / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        c.ensure(h + 60f)
        val top = c.y
        c.rect(RectF(EDGE, top, EDGE + WIDTH, top + h + 28f), Color.WHITE, BORDER)
        c.text("Clinical Case Photograph", EDGE + 12f, top + 18f, paint(ACCENT, 11f, Typeface.BOLD))
        c.image(bitmap, RectF(EDGE + 8f, top + 24f, EDGE + 8f + w, top + 24f + h))
        c.y = top + h + 40f
        bitmap.recycle()
    }


}
