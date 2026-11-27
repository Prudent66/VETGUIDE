package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Group 2 Phase 2: specific agents replace wrappers and keep option grouping. */
class MedicationExtractionHygieneTest {

    private val wrapperText =
        "Apply topical antibiotic ointment (e.g., containing neomycin or bacitracin) " +
            "to the lesion twice daily."

    @Test
    fun `generic wrapper is replaced by its specific active ingredients`() {
        val mentions = recommendedMedicationMentions(wrapperText, domesticAnalyzedNames)
        val names = mentions.map { normalizedDrugName(it.name) }
        assertEquals(2, mentions.size)
        assertTrue(names.containsAll(listOf("neomycin", "bacitracin")))
        assertFalse(names.contains("topical antibiotic ointment"))
        val meds = domesticMedicationsFor(wrapperText)
        assertEquals(2, meds.size)
        assertTrue(meds.all { it.drug.route == DoseRoute.TOPICAL })
    }

    @Test
    fun `specific agents are recorded as mutually exclusive alternatives`() {
        val mentions = recommendedMedicationMentions(wrapperText, domesticAnalyzedNames)
        val groups = alternativeGroups(mentions)
        assertEquals(1, groups.size)
        assertEquals(2, groups.values.single().size)
        assertTrue(mentions.all { it.alternativeGroup != null })
        assertTrue(domesticHasAlternatives(wrapperText))
        assertTrue(wildHasAlternatives(wrapperText))
    }

    @Test
    fun `antifungal Miconazole is captured in both pathways`() {
        val text = "Apply Miconazole 2% cream to the skin lesion twice daily."
        val domestic = domesticMedicationsFor(text)
        val wild = wildMedicationsFor(text, WildlifeGroup.WILD_CANIDS)
        assertEquals(1, domestic.size)
        assertEquals(1, wild.size)
        assertTrue(domestic.single().drug.id.startsWith("analyzed-domestic-"))
        assertTrue(wild.single().drug.id.startsWith("analyzed-wild-"))
        assertEquals(DoseRoute.TOPICAL, domestic.single().drug.route)
        assertEquals(DoseRoute.TOPICAL, wild.single().drug.route)
    }

    @Test
    fun `antimicrobial and antiparasitic supportive options are captured`() {
        val text = "Supportive options include Albendazole 10 mg/kg PO and Enrofloxacin 5 mg/kg PO."
        val domestic = recommendedMedicationMentions(text, domesticAnalyzedNames)
            .map { normalizedDrugName(it.name) }
        val wild = recommendedMedicationMentions(text, wildAnalyzedNames)
            .map { normalizedDrugName(it.name) }
        assertTrue(domestic.containsAll(listOf("albendazole", "enrofloxacin")))
        assertTrue(wild.containsAll(listOf("albendazole", "enrofloxacin")))
    }

    @Test
    fun `systemic alternatives are grouped as mutually exclusive`() {
        val text = "Treat with Oxytetracycline 20 mg/kg IM or Enrofloxacin 5 mg/kg IM."
        val domestic = recommendedMedicationMentions(text, domesticAnalyzedNames)
        val wild = recommendedMedicationMentions(text, wildAnalyzedNames)
        assertEquals(1, alternativeGroups(domestic).size)
        assertEquals(2, alternativeGroups(domestic).values.single().size)
        assertEquals(1, alternativeGroups(wild).size)
        assertTrue(domesticHasAlternatives(text))
        assertTrue(wildHasAlternatives(text))
    }

    @Test
    fun `generic wrapper is retained when no specific agent is named`() {
        val text = "Apply Topical Antibiotic Ointment to the wound twice daily."
        val names = recommendedMedicationMentions(text, domesticAnalyzedNames)
            .map { normalizedDrugName(it.name) }
        assertEquals(listOf("topical antibiotic ointment"), names)
    }

    @Test
    fun `extraction and grouping stay isolated by category`() {
        val text = "Apply Neomycin or Bacitracin ointment to the lesion."
        val domestic = domesticMedicationsFor(text)
        val wild = wildMedicationsFor(text, WildlifeGroup.WILD_CANIDS)
        assertTrue(domestic.all { it.drug.id.startsWith("analyzed-domestic-") })
        assertTrue(wild.all { it.drug.id.startsWith("analyzed-wild-") })
        assertTrue(domestic.none { it.drug.id in wild.map { entry -> entry.drug.id } })
        assertTrue(domestic.all { it.drug.doseBySpecies.keys.none { key -> key == Species.OTHER } })
        assertTrue(wild.all { it.drug.doseByGroup.keys.all { key -> key is WildlifeGroup } })
    }

    @Test
    fun `warning-only agents stay excluded after wrapper suppression`() {
        val text = "Apply Neosporin ointment twice daily. " +
            "Do not use neomycin or bacitracin unless allergy has been ruled out."
        val meds = domesticMedicationsFor(text)
        assertEquals(1, meds.size)
        assertTrue(meds.none {
            it.label.contains("neomycin", true) || it.label.contains("bacitracin", true)
        })
    }
}
