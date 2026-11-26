package com.prudent66.vetguide.domain.formulary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 25: dose/route parsing and suffix-insensitive medication matching. */
class AnalyzedDrugParserTest {

    @Test
    fun `reads dose rates and oral route from an anthelmintic recommendation`() {
        val mentions = medicationMentions(
            "Deworm with fenbendazole (50 mg/kg orally) or pyrantel pamoate (5-10 mg/kg orally)",
            listOf("Fenbendazole", "Pyrantel Pamoate"),
        )
        assertEquals(2, mentions.size)
        assertEquals("Fenbendazole", mentions[0].name)
        assertEquals(DoseRange(50.0, 50.0), mentions[0].doseRange)
        assertEquals(DoseRoute.ORAL, mentions[0].route)
        assertEquals("Pyrantel Pamoate", mentions[1].name)
        assertEquals(DoseRange(5.0, 10.0), mentions[1].doseRange)
        assertEquals(DoseRoute.ORAL, mentions[1].route)
    }

    @Test
    fun `normalizes strength category and wild suffixes`() {
        assertTrue(namesMatch("Ivermectin", "Ivermectin 1%"))
        assertTrue(namesMatch("Ivermectin", "Ivermectin 1% (wild)"))
        assertTrue(namesMatch("Oxytetracycline", "Oxytetracycline 20% (wild dart)"))
        assertTrue(namesMatch("Amoxicillin", "Amoxicillin LA"))
        assertTrue(namesMatch("Oxytetracycline", "Oxytetracycline (Aquatic)"))
        assertTrue(namesMatch("Fenbendazole", "Fenbendazole (wild; analyzed — verify dose)"))
        assertEquals(
            "topical wound antiseptic ointment",
            normalizedDrugName("Topical Wound / Antiseptic Ointment (wild)"),
        )
    }

    @Test
    fun `recognizes a capitalized topical preparation without a dictionary hit`() {
        val mentions = medicationMentions(
            "apply Veterinarian Wound Ointment to the lesion twice daily.",
            listOf("Fenbendazole"),
        )
        assertEquals(1, mentions.size)
        assertEquals("Veterinarian Wound Ointment", mentions[0].name)
        assertEquals(DoseRoute.TOPICAL, mentions[0].route)
        assertEquals("Twice daily", mentions[0].frequency)
    }

    @Test
    fun `recognized topical brand keeps the application route and schedule`() {
        val mentions = medicationMentions(
            "Apply Neosporin antibiotic ointment twice daily.",
            listOf("Neosporin"),
        )
        assertEquals(1, mentions.size)
        assertEquals("Neosporin", mentions[0].name)
        assertEquals(DoseRoute.TOPICAL, mentions[0].route)
        assertEquals("Twice daily", mentions[0].frequency)
    }

    @Test
    fun `drops drugs that appear only in do not use warnings`() {
        val text = "Apply Neosporin ointment twice daily. " +
            "Do not use prednisolone eye drops unless an ulcer has been ruled out."
        val mentions = recommendedMedicationMentions(
            text, listOf("Neosporin", "Prednisolone"),
        )
        assertEquals(1, mentions.size)
        assertEquals("Neosporin", mentions[0].name)
        assertEquals(DoseRoute.TOPICAL, mentions[0].route)
        assertTrue(mentions[0].frequency.contains("Twice"))
    }

    @Test
    fun `extracts named drugs case insensitively from narrative text`() {
        assertEquals(
            listOf("Ivermectin", "Fenbendazole"),
            extractedDrugNames(
                "Deworm with IVERMECTIN first, then fenbendazole if needed.",
                listOf("Ivermectin", "Fenbendazole"),
            ),
        )
    }
}
