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
    val clinicName: String? = null,
    val vetName: String? = null,
    val clinicContact: String? = null,
)

/**
 * Builds the Phase 7 clinical-report PDF with Android's PdfDocument API:
 * VETGUIDE branding, patient info, the four SOAP sections, the analysis
 * finding, the case image, and the standing disclaimers.
 * A4 @ 72dpi; content paginates automatically.
 */
object SoapPdfDocument {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    /** Footer baseline sits this far above the page bottom; leave a clear gap. */
    private const val FOOTER_BASELINE_OFFSET = 28f
    private const val FOOTER_GAP = 8f
    private const val LINE_GAP = 4f

    /** Cap the case image so the report/disclaimer do not strand on a new page. */
    private const val MAX_IMAGE_HEIGHT = 260f

    /** Single source for the header subtitle — rendered exactly once per report. */
    private const val HEADER_SUBTITLE = "Veterinary clinical assistant - clinical report"

    fun build(data: SoapReportData): PdfDocument {
        val document = PdfDocument()
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val cursor = Cursor(document, body, footerLines(data))

        drawHeader(cursor, data)
        drawPatientInfo(cursor, data)
        drawSection(cursor, "Subjective (history / anamnesis)", data.soap.subjective)
        drawSection(cursor, "Objective (findings)", data.soap.objective)
        if (!data.aiNarrative.isNullOrBlank()) {
            drawSection(cursor, "Analysis Finding:", data.aiNarrative)
        }
        drawSection(cursor, "Assessment", data.soap.assessment)
        drawSection(cursor, "Plan (treatment & follow-up)", data.soap.plan)
        drawImage(cursor, data.imageBase64)
        drawDisclaimer(cursor)
        cursor.finish()

        return document
    }

    private fun drawHeader(cursor: Cursor, data: SoapReportData) {
        cursor.y = MARGIN
        val titlePaint = Paint().apply {
            color = Color.rgb(76, 175, 80)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val titleBaseline = cursor.y + 18f
        cursor.canvas.drawText("VETGUIDE", MARGIN, titleBaseline, titlePaint)
        // Advance past the title's descent so the subtitle never overlaps it.
        cursor.y = titleBaseline + titlePaint.fontMetrics.descent + 12f
        cursor.line(HEADER_SUBTITLE, cursor.smallPaint)
        data.clinicName?.takeIf { it.isNotBlank() }?.let {
            cursor.line("Clinic: $it", cursor.smallPaint)
        }
        data.vetName?.takeIf { it.isNotBlank() }?.let {
            cursor.line("Veterinarian: $it", cursor.smallPaint)
        }
        data.clinicContact?.takeIf { it.isNotBlank() }?.let {
            cursor.line("Contact / license: $it", cursor.smallPaint)
        }
        cursor.y += 10f
    }

    /** Clinic identity (when set) + the standing decision-support line per page. */
    private fun footerLines(data: SoapReportData): List<String> = buildList {
        val clinicLine = listOfNotNull(
            data.clinicName?.takeIf { it.isNotBlank() },
            data.vetName?.takeIf { it.isNotBlank() },
            data.clinicContact?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (clinicLine.isNotBlank()) add(clinicLine)
        add("VETGUIDE — clinical decision support; not a diagnosis.")
    }

    private fun drawPatientInfo(cursor: Cursor, data: SoapReportData) {
        cursor.ensure(96f)
        cursor.line("Patient: ${data.patientLine}", cursor.boldPaint)
        data.ownerLine?.let { cursor.line("Owner: $it", cursor.smallPaint) }
        cursor.line("Case: ${data.caseId.take(8)} · status: ${data.status}", cursor.smallPaint)
        cursor.line("Presenting complaint: ${data.complaint}", cursor.smallPaint)
        cursor.line("Generated: ${data.generatedAt}", cursor.smallPaint)
        cursor.y += 8f
    }

    private fun drawSection(cursor: Cursor, heading: String, body: String) {
        if (body.isBlank()) return
        cursor.ensure(48f)
        cursor.y += 6f
        cursor.line(heading, cursor.boldPaint)
        cursor.y += 4f
        body.split("\n").forEach { paragraph ->
            wrapText(paragraph, cursor.bodyPaint, CONTENT_WIDTH).forEach { line ->
                cursor.line(line, cursor.bodyPaint)
            }
        }
        cursor.y += 6f
    }

    private fun drawImage(cursor: Cursor, imageBase64: String?) {
        if (imageBase64.isNullOrBlank()) return
        val bitmap = decodeImage(imageBase64) ?: return
        val scale = minOf(CONTENT_WIDTH / bitmap.width, MAX_IMAGE_HEIGHT / bitmap.height)
        val drawWidth = (bitmap.width * scale).toInt()
        val drawHeight = (bitmap.height * scale).toInt()
        cursor.ensure(24f + drawHeight + 24f)
        cursor.y += 6f
        cursor.line("Case image", cursor.boldPaint)
        cursor.y += 4f
        cursor.canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(MARGIN, cursor.y, MARGIN + drawWidth, cursor.y + drawHeight),
            cursor.bodyPaint,
        )
        cursor.y += drawHeight + 10f
        bitmap.recycle()
    }

    private fun decodeImage(imageBase64: String): Bitmap? = try {
        val bytes = ImageEncoder.fromBase64(imageBase64)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: IllegalArgumentException) {
        android.util.Log.w("SoapPdfDocument", "Case image could not be decoded for the PDF", e)
        null
    }

    private fun drawDisclaimer(cursor: Cursor) {
        cursor.ensure(32f)
        cursor.y += 8f
        val disclaimer = "Decision support — not a diagnosis. The veterinarian remains " +
            "responsible for all clinical decisions. Match/confidence values are " +
            "seed or model estimates and must not guide treatment without " +
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
        private val footerLines: List<String>,
    ) {
        val boldPaint = Paint(bodyPaint).apply { typeface = Typeface.DEFAULT_BOLD }
        val smallPaint = Paint(bodyPaint).apply { textSize = 10f }
        private val footerPaint = Paint(bodyPaint).apply {
            textSize = 8f
            color = Color.DKGRAY
        }
        var pageNumber = 1
        lateinit var canvas: android.graphics.Canvas
        var y = MARGIN

        private var page: PdfDocument.Page? = null

        /** Lowest baseline whose line still clears the footer zone on a page. */
        private val contentBottom: Float
            get() = PAGE_HEIGHT - FOOTER_BASELINE_OFFSET - (-footerPaint.ascent()) - FOOTER_GAP

        init {
            startPage()
        }

        /** Never draw a line that would enter the footer zone — paginate first. */
        fun line(text: String, paint: Paint) {
            val advance = paint.textSize + LINE_GAP
            if (y + advance > contentBottom) {
                startPage()
            }
            canvas.drawText(text, MARGIN, y, paint)
            y += advance
        }

        fun ensure(required: Float) {
            if (y + required > contentBottom) {
                startPage()
            }
        }

        /** The last page must be finished before the document is written. */
        fun finish() {
            page?.let {
                drawFooter(it.canvas)
                document.finishPage(it)
            }
            page = null
        }

        private fun startPage() {
            page?.let {
                drawFooter(it.canvas)
                document.finishPage(it)
            }
            val newPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            page = newPage
            canvas = newPage.canvas
            y = MARGIN
            pageNumber += 1
        }

        /** Draws the clinic/disclaimer footer above the bottom margin. */
        private fun drawFooter(canvas: android.graphics.Canvas) {
            var baseline = PAGE_HEIGHT - FOOTER_BASELINE_OFFSET
            footerLines.forEach { line ->
                canvas.drawText(line, MARGIN, baseline, footerPaint)
                baseline += 11f
            }
        }
    }
}
