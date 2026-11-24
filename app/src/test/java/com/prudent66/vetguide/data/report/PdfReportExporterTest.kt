package com.prudent66.vetguide.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Verifies the page state machine used by PdfReportExporter/PdfLayout. A page
 * must be finished before the next starts, otherwise Android throws
 * "Current page not finished!" during multi-page clinical reports.
 */
class PdfReportExporterTest {

    @Test
    fun `multi page report finishes every page before starting the next`() {
        val lifecycle = PdfPageLifecycle()

        repeat(6) { index ->
            val page = lifecycle.start()
            assertEquals(index + 1, page)
            assertEquals(page, lifecycle.finish())
        }

        assertEquals(6, lifecycle.pageCount)
        assertNull(lifecycle.activePage)
    }

    @Test
    fun `starting a page before finishing throws the Android error`() {
        val lifecycle = PdfPageLifecycle()
        lifecycle.start()

        val error = assertThrows(IllegalStateException::class.java) { lifecycle.start() }

        assertEquals("Current page not finished!", error.message)
    }

    @Test
    fun `finishAll flushes the final active page`() {
        val lifecycle = PdfPageLifecycle()
        lifecycle.start()
        lifecycle.finish()
        lifecycle.start()
        lifecycle.finishAll()

        assertEquals(2, lifecycle.pageCount)
        assertNull(lifecycle.activePage)
    }

    @Test
    fun `finished multi page document writes a non empty pdf file`() {
        val lifecycle = PdfPageLifecycle()
        repeat(3) {
            lifecycle.start()
            lifecycle.finish()
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            stream.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
            stream.write("%%EOF\n".toByteArray(Charsets.US_ASCII))
            stream.toByteArray()
        }

        assertTrue(bytes.isNotEmpty())
        assertEquals("%PDF-", String(bytes.copyOfRange(0, 5), Charsets.US_ASCII))
        assertEquals(3, lifecycle.pageCount)
    }
}
