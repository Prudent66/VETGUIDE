package com.prudent66.vetguide.data.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.prudent66.vetguide.data.ai.ImageEncoder
import com.prudent66.vetguide.domain.model.SoapPayload

/** All data the PDF renderer needs — assembled by the ViewModel (Phase 7). */
data class SoapReportData(
    val patientLine: String,
    val ownerLine: String?,
    val caseId: String,
    val complaint: String,
    val status: String,
    val generatedAt: String,
    val soap: SoapPayload,
    val aiNarrative: String?,
    val imageBase64: String?,
)

/**
 * Builds the Phase 7 clinical-report PDF with Android's PdfDocument API:
 * VETGUIDE branding, patient info, the four SOAP sections, the AI analysis
 * (labeled decision support), the case image, and the standing disclaimers.
 * A4 @ 72dpi; content paginates automatically.
 */
object SoapPdfDocument {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    fun build(data: SoapReportData): PdfDocument {
        val document = PdfDocument()
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val cursor = Cursor(document, body)

        drawHeader(cursor)
        drawPatientInfo(cursor, data)
        drawSection(cursor, "Subjective (history / anamnesis)", data.soap.subjective)
        drawSection(cursor, "Objective (findings)", data.soap.objective)
        if (!data.aiNarrative.isNullOrBlank()) {
            drawSection(cursor, "AI analysis (decision support — not a diagnosis)", data.aiNarrative)
        }
        drawSection(cursor, "Assessment", data.soap.assessment)
        drawSection(cursor, "Plan (treatment & follow-up)", data.soap.plan)
        drawImage(cursor, data.imageBase64)
        drawDisclaimer(cursor)
        cursor.finish()

        return document
    }

    private fun drawHeader(cursor: Cursor) {
        cursor.y = MARGIN
        cursor.canvas.drawText(
            "VETGUIDE",
            MARGIN,
            cursor.y + 18f,
            Paint().apply {
                color = Color.rgb(76, 175, 80)
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            },
        )
        cursor.y += 24f
        cursor.line("AI-powered veterinary clinical assistant — clinical report", cursor.smallPaint)
        cursor.y += 14f
    }

    private fun drawPatientInfo(cursor: Cursor, data: SoapReportData) {
        cursor.ensure(96f)
        cursor.line("Patient: ${data.patientLine}", cursor.boldPaint)
        data.ownerLine?.let { cursor.line("Owner: $it", cursor.smallPaint) }
        cursor.line("Case: ${data.caseId.take(8)} · status: ${data.status}", cursor.smallPaint)
        cursor.line("Presenting complaint: ${data.complaint}", cursor.smallPaint)
        cursor.line("Generated: ${data.generatedAt}", cursor.smallPaint)
        cursor.y += 12f
    }

    private fun drawSection(cursor: Cursor, heading: String, body: String) {
        if (body.isBlank()) return
        cursor.ensure(48f)
        cursor.y += 8f
        cursor.line(heading, cursor.boldPaint)
        cursor.y += 6f
        body.split("\n").forEach { paragraph ->
            wrapText(paragraph, cursor.bodyPaint, CONTENT_WIDTH).forEach { line ->
                cursor.line(line, cursor.bodyPaint)
            }
        }
        cursor.y += 10f
    }

    private fun drawImage(cursor: Cursor, imageBase64: String?) {
        if (imageBase64.isNullOrBlank()) return
        val bitmap = decodeImage(imageBase64) ?: return
        val drawWidth = CONTENT_WIDTH.toInt()
        val drawHeight = (bitmap.height * (drawWidth.toFloat() / bitmap.width)).toInt()
        cursor.ensure(24f + drawHeight + 24f)
        cursor.y += 8f
        cursor.line("Case image", cursor.boldPaint)
        cursor.y += 6f
        cursor.canvas.drawBitmap(bitmap, null, android.graphics.RectF(MARGIN, cursor.y, MARGIN + drawWidth, cursor.y + drawHeight), cursor.bodyPaint)
        cursor.y += drawHeight + 16f
        bitmap.recycle()
    }

    private fun decodeImage(imageBase64: String): Bitmap? = try {
        val bytes = ImageEncoder.fromBase64(imageBase64)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun drawDisclaimer(cursor: Cursor) {
        cursor.ensure(96f)
        cursor.y += 12f
        val disclaimer = "Decision support — not a diagnosis. The veterinarian remains " +
            "responsible for all clinical decisions. Match/confidence values are " +
            "placeholder or model estimates and must not guide treatment without " +
            "professional review."
        wrapText(disclaimer, cursor.smallPaint, CONTENT_WIDTH).forEach { line ->
            cursor.line(line, cursor.smallPaint)
        }
    }

    /** Simple greedy word-wrap that preserves blank lines. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> = buildList {
        text.split("\n").forEach { rawLine ->
            var current = ""
            rawLine.split(" ").forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) add(current)
                    current = word
                }
            }
            add(current)
        }
    }

    /** Page bookkeeping: draws on the current page, rolling to a new one on demand. */
    private class Cursor(
        private val document: PdfDocument,
        val bodyPaint: Paint,
    ) {
        val boldPaint = Paint(bodyPaint).apply { typeface = Typeface.DEFAULT_BOLD }
        val smallPaint = Paint(bodyPaint).apply { textSize = 10f }
        var pageNumber = 1
        lateinit var canvas: android.graphics.Canvas
        var y = MARGIN

        private var page: PdfDocument.Page? = null

        init {
            startPage()
        }

        fun line(text: String, paint: Paint) {
            canvas.drawText(text, MARGIN, y, paint)
            y += paint.textSize + 4f
        }

        fun ensure(required: Float) {
            if (y + required > PAGE_HEIGHT - MARGIN) {
                startPage()
            }
        }

        /** The last page must be finished before the document is written. */
        fun finish() {
            page?.let { document.finishPage(it) }
            page = null
        }

        private fun startPage() {
            page?.let { document.finishPage(it) }
            val newPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            page = newPage
            canvas = newPage.canvas
            y = MARGIN
            pageNumber += 1
        }
    }
}
