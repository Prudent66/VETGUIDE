package com.prudent66.vetguide.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** Owner handout withdrawal dates and plain-language content. */
class FarmerHandoutTest {

    private val start = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli()

    @Test
    fun `withdrawal safe date adds the configured days`() {
        val meat = FarmerHandoutExporter.withdrawalSafeDate(start, 7)
        val milk = FarmerHandoutExporter.withdrawalSafeDate(start, 3)

        assertEquals("2026-11-27", meat.atOffset(ZoneOffset.UTC).toLocalDate().toString())
        assertEquals("2026-11-23", milk.atOffset(ZoneOffset.UTC).toLocalDate().toString())
    }

    @Test
    fun `handout contains dosing isolation and safe dates`() {
        val text = FarmerHandoutExporter.buildHandout(
            patientName = "Nandi",
            drugName = "Oxytetracycline 5%",
            doseInstructions = "15 mg/kg IM once daily",
            isolationSteps = "Separate from the herd.",
            fromEpochMs = start,
            meatWithdrawalDays = 7,
            milkWithdrawalDays = 3,
        )

        assertTrue(text.contains("Nandi"))
        assertTrue(text.contains("Oxytetracycline 5%"))
        assertTrue(text.contains("Separate from the herd."))
        assertTrue(text.contains("Meat withdrawal safe date"))
        assertTrue(text.contains("Milk withdrawal safe date"))
        assertTrue(text.contains("27 Nov 2026"))
    }
}
