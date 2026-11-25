package com.prudent66.vetguide.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.prudent66.vetguide.domain.model.AgeUnit
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.SoapPayload
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.ageLabel
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
    fun `plan is rendered once before the separated treatment boxes`() {
        val data = HospitalReportData(
            patientLine = "Nandi · Cattle",
            caseId = "case-1",
            complaint = "Fever",
            status = "Investigating",
            generatedAt = "24 Nov 2026",
            soap = SoapPayload(
                subjective = "Fever for two days",
                objective = "Depressed",
                assessment = "PPR suspected",
                plan = "Oxytetracycline 15 mg/kg IM once daily\nIsolate affected animals",
            ),
        )

        val titles = reportSectionTitles(data)

        assertEquals(1, titles.count { it.startsWith("P —") })
        assertEquals(PLAN_TITLE, titles.last())
        assertEquals(listOf("Oxytetracycline 15 mg/kg IM once daily"), planPrescriptions(data))
        assertEquals(listOf("Isolate affected animals"), planSupportiveCare(data))
    }

    @Test
    fun `inner content width subtracts both card paddings`() {
        assertEquals(WIDTH - BOX_PADDING * 2f, innerContentWidth(WIDTH), 0.001f)
    }

    @Test
    fun `wrap keeps every rendered line inside the inner content width`() {
        val measure: (String) -> Float = { value -> value.length * 6f }
        val lines = wrap(
            "Prescribe fluids and rest daily for the affected herd",
            measure,
            120f,
        )

        assertTrue(lines.size > 1)
        lines.forEach { line -> assertTrue(measure(line) <= 120f) }
    }

    @Test
    fun `numbered and unnumbered prescription lines are deduplicated`() {
        val data = HospitalReportData(
            patientLine = "Nandi · Cattle",
            caseId = "case-1",
            complaint = "Fever",
            status = "Investigating",
            generatedAt = "24 Nov 2026",
            soap = SoapPayload(),
            prescriptions = listOf(
                "Prescribe a course of antibiotics for seven days",
                "1. Prescribe a course of antibiotics for seven days",
                "- Prescribe a course of antibiotics for seven days",
            ),
        )

        assertEquals(
            listOf("Prescribe a course of antibiotics for seven days"),
            planPrescriptions(data),
        )
    }

    @Test
    fun `numbered and unnumbered supportive care lines are deduplicated`() {
        val data = HospitalReportData(
            patientLine = "Nandi · Cattle",
            caseId = "case-1",
            complaint = "Fever",
            status = "Investigating",
            generatedAt = "24 Nov 2026",
            soap = SoapPayload(
                plan = "Isolate affected animals\n- Isolate affected animals\n• Provide shade and water",
            ),
        )

        assertEquals(
            listOf("Isolate affected animals", "Provide shade and water"),
            planSupportiveCare(data),
        )
    }

    @Test
    fun `ranked condition bar starts below every wrapped label line`() {
        val lineCount = 4
        val top = 200f
        val lastBaseline = top + (lineCount - 1) * 13f

        val barTop = differentialBarTop(top, lineCount)

        assertTrue(barTop > lastBaseline + 10f)
        assertTrue(differentialRowHeight(lineCount) > differentialBarTop(0f, lineCount) + 8f)
        assertTrue(differentialRowHeight(lineCount) > differentialTextHeight(lineCount))
        assertEquals(13f, differentialTextHeight(1), 0.001f)
    }

    @Test
    fun `wild patient metadata uses subtype and group instead of Other`() {
        val wild = Patient(
            id = "w1",
            name = "Kito",
            animalCategory = AnimalCategory.WILD,
            wildlifeGroup = "Jackal / Fox / Wild Dog",
            species = Species.OTHER,
            breed = "African Wild Dog",
            sex = PatientSex.UNKNOWN,
            neuterStatus = NeuterStatus.UNKNOWN,
            dateOfBirthEpochMs = null,
            ageValue = 3.0,
            ageUnit = AgeUnit.YEARS,
            weightKg = 15.0,
            weightUpdatedAtEpochMs = null,
            ownerName = null,
            ownerPhone = null,
            ownerNotes = null,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

        assertEquals(
            "African Wild Dog · Jackal / Fox / Wild Dog",
            ClinicalSpeciesCatalog.reportLabel(wild),
        )
        assertEquals("3 Years", wild.ageLabel())
        assertTrue(ClinicalSpeciesCatalog.reportLabel(wild) != "Other")
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
