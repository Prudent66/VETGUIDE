package com.prudent66.vetguide.data.report

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 7: writes the finished clinical-report PDF into the device's public
 * Downloads folder. API 29+ uses MediaStore (no permission needed); API 26–28
 * writes directly, which requires the legacy WRITE_EXTERNAL_STORAGE runtime
 * permission requested by the UI before calling this.
 */
object PdfFileWriter {

    /** Returns a human-readable location (file path or content URI). */
    fun writeToDownloads(context: Context, document: PdfDocument, fileName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeToMediaStore(context, document, fileName)
        }
        return writeToLegacyDownloads(document, fileName)
    }

    /** Collision-free, human-friendly file name (patient name is sanitized). */
    fun buildFileName(patientName: String?, generatedAtMs: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(generatedAtMs))
        val safeName = patientName?.trim()
            ?.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            ?.take(24)
            ?.takeIf { it.isNotEmpty() }
            ?: "case"
        return "VETGUIDE-$safeName-$stamp.pdf"
    }

    private fun writeToMediaStore(
        context: Context,
        document: PdfDocument,
        fileName: String,
    ): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("The Downloads folder refused the PDF file")
        val output = resolver.openOutputStream(uri)
            ?: throw IOException("Could not open the Downloads file for writing")
        output.use { stream -> document.writeTo(stream) }
        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun writeToLegacyDownloads(document: PdfDocument, fileName: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { stream -> document.writeTo(stream) }
        return file.absolutePath
    }
}
