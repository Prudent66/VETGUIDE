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
        val document = PdfDocument()
        val cursor = PdfCursor(document)
        drawHeader(cursor, data)
        drawMetadata(cursor, data)
        drawSoap(cursor, data)
        if (data.differentials.isNotEmpty()) drawDifferentials(cursor, data.differentials)
        drawPrescriptions(cursor, data)
        drawImage(cursor, data.imageBase64)
        drawDisclaimer(cursor)
        cursor.finish()
        return document
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
        val rows = listOf(
            "Patient" to d.patientLine,
            "Category" to d.animalCategory,
            "Owner" to (d.ownerLine ?: "—"),
            "Case status" to d.status,
            "Presenting complaint" to d.complaint,
            "Clinic / veterinarian" to listOfNotNull(d.clinicName, d.vetName).joinToString(" · ").ifBlank { "—" },
        )
        val rowH = 30f
        val boxH = rows.size * rowH + 16f
        c.ensure(boxH + 16f)
        val top = c.y
        c.rect(RectF(EDGE, top, EDGE + WIDTH, top + boxH), TINT, BORDER)
        c.y = top + 16f
        rows.forEach { (label, value) ->
            c.text(label, EDGE + 10f, c.y, paint(MUTED, 9f, Typeface.BOLD))
            c.text(value, EDGE + 150f, c.y, paint(INK, 11f))
            c.y += rowH
        }
        c.y = top + boxH + 18f
    }

    private fun drawSoap(c: PdfCursor, d: HospitalReportData) {
        val objective = dedupeObjective(d.soap.objective, d.narrative)
        val sections = listOf(
            "S — Subjective (History / Anamnesis)" to d.soap.subjective,
            "O — Objective (Clinical Findings)" to objective,
            "A — Assessment (Ranked Differentials)" to d.soap.assessment,
            "P — Plan (Treatment & Supportive Care)" to d.soap.plan,
        )
        sections.forEach { (title, body) ->
            if (body.isBlank()) return@forEach
            val lines = wrap(body, paint(INK, 11f), WIDTH - 34f)
            val boxH = lines.size * 15f + 40f
            c.ensure(boxH + 10f)
            val top = c.y
            c.rect(RectF(EDGE, top, EDGE + WIDTH, top + boxH), Color.WHITE, BORDER)
            c.rect(RectF(EDGE, top, EDGE + 6f, top + boxH), ACCENT)
            c.text(title, EDGE + 18f, top + 20f, paint(ACCENT, 12f, Typeface.BOLD))
            c.y = top + 38f
            lines.forEach { line ->
                c.text(line, EDGE + 18f, c.y, paint(INK, 11f))
                c.y += 15f
            }
            c.y = top + boxH + 12f
        }
    }

    /** Removes any raw "Analysis Finding:" blob already carried inside Objective. */
    private fun dedupeObjective(objective: String, narrative: String?): String {
        if (narrative.isNullOrBlank()) return objective
        val marker = "Analysis Finding:"
        val idx = objective.indexOf(marker, ignoreCase = true)
        return if (idx >= 0) objective.substring(0, idx).trim() else objective
    }

    private fun drawDifferentials(c: PdfCursor, items: List<Pair<String, Double>>) {
        val titleH = 30f
        val boxH = titleH + items.size * 28f + 12f
        c.ensure(boxH + 10f)
        val top = c.y
        c.rect(RectF(EDGE, top, EDGE + WIDTH, top + boxH), Color.WHITE, BORDER)
        c.text("Ranked Suspected Conditions", EDGE + 12f, top + 20f, paint(ACCENT, 12f, Typeface.BOLD))
        var y = top + titleH
        items.take(6).forEach { (name, confidence) ->
            c.text(name, EDGE + 12f, y, paint(INK, 10f))
            val pct = (confidence * 100).toInt().coerceIn(0, 100)
            c.text("$pct%", EDGE + WIDTH - 46f, y, paint(MUTED, 10f))
            c.rect(RectF(EDGE + 12f, y + 5f, EDGE + WIDTH - 56f, y + 13f), TINT)
            c.rect(RectF(EDGE + 12f, y + 5f, EDGE + 12f + (WIDTH - 68f) * pct / 100f, y + 13f), ACCENT)
            y += 28f
        }
        c.y = top + boxH + 12f
    }

    private fun drawPrescriptions(c: PdfCursor, d: HospitalReportData) {
        val rx = (d.prescriptions + d.soap.plan.lines().filter { it.contains("mg/kg", ignoreCase = true) })
            .distinct().filter { it.isNotBlank() }
        if (rx.isEmpty() && d.supportiveCare.isEmpty()) return
        val care = d.supportiveCare.ifEmpty {
            d.soap.plan.lines().filterNot { it.contains("mg/kg", ignoreCase = true) }
        }
        val rxH = maxOf(46f, rx.size * 15f + 30f)
        val careH = maxOf(46f, care.size * 15f + 30f)
        c.ensure(rxH + careH + 24f)
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
        c.canvas.drawBitmap(bitmap, null, RectF(EDGE + 8f, top + 24f, EDGE + 8f + w, top + 24f + h), paint(INK, 11f))
        c.y = top + h + 40f
        bitmap.recycle()
    }

    private fun drawDisclaimer(c: PdfCursor) {
        val text = "VETGUIDE Clinical Decision Support — Not a Substitute for Veterinary Examination"
        val lines = wrap(text, paint(MUTED, 9f), WIDTH)
        c.ensure(lines.size * 13f + 20f)
        lines.forEach { line ->
            c.text(line, EDGE, c.y, paint(MUTED, 9f))
            c.y += 13f
        }
    }

}
