package com.prudent66.vetguide.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filenames stay safe, readable and never truncate a name mid-word. */
class PdfFileNameTest {

    @Test
    fun `short patient names are kept intact`() {
        val name = PdfFileWriter.buildFileName("Fluffy", 0L)
        assertTrue(name.startsWith("VETGUIDE-Fluffy-"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `long names truncate on a word boundary`() {
        val name = PdfFileWriter.buildFileName(
            "Golden Retriever Crossbreed Study Group Alpha", 0L,
        )
        assertTrue(name.startsWith("VETGUIDE-Golden_Retriever_Crossbreed_Study_Group-"))
        assertFalse(name.contains("Alpha"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `unsafe characters are replaced and safe names are preserved`() {
        val name = PdfFileWriter.buildFileName("Fluffy/Bello #2", 0L)
        assertTrue(name.startsWith("VETGUIDE-Fluffy_Bello"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains('#'))
    }

    @Test
    fun `blank names fall back to case`() {
        assertEquals("case", PdfFileWriter.buildFileName(null, 0L)
            .substringAfter("VETGUIDE-").substringBefore('-'))
        assertTrue(PdfFileWriter.buildFileName("   ", 0L).startsWith("VETGUIDE-case-"))
    }
}
