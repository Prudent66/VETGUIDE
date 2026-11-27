package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Group 2 Phase 1: explicit administration cues outrank class/product routes. */
class RouteResolutionTest {

    @Test
    fun `explicit cues beat preparation class hints`() {
        assertEquals(DoseRoute.SC, AdministrationRoute.resolve("meloxicam 0.2 mg/kg SC once"))
        assertEquals(DoseRoute.IM, AdministrationRoute.resolve("oxytetracycline 20% solution IM once"))
        assertEquals(DoseRoute.IV, AdministrationRoute.resolve("gentamicin IV in water"))
        assertEquals(DoseRoute.ORAL, AdministrationRoute.resolve("fenbendazole 50 mg/kg orally"))
        assertEquals(DoseRoute.TOPICAL, AdministrationRoute.resolve("apply mupirocin ointment to the wound"))
    }

    @Test
    fun `subcutaneous recommendation never becomes an oral default`() {
        val text = "Give albendazole 10 mg/kg SC once."
        assertEquals(DoseRoute.SC, medicationMentions(text, listOf("Albendazole")).single().route)
        val domestic = bestDomesticDrug(text, null, Species.CATTLE)!!
        val wild = bestWildDrug(text, null, WildlifeGroup.WILD_RUMINANTS)!!
        assertEquals(DoseRoute.SC, domestic.route)
        assertEquals(DoseRoute.SC, wild.route)
    }

    @Test
    fun `skin lesion ointment stays topical without ophthalmic wording`() {
        val text = "Apply Flurbiprofen ointment to the skin lesion twice daily."
        val domestic = domesticMedicationsFor(text).single()
        val wild = wildMedicationsFor(text, WildlifeGroup.WILD_CANIDS).single()
        listOf(domestic, wild).forEach { medication ->
            assertEquals(DoseRoute.TOPICAL, medication.drug.route)
            assertFalse(medication.ocular)
            assertFalse(medication.label.contains("ophthalmic"))
            assertTrue(medication.label.contains("wound care"))
        }
    }

    @Test
    fun `ophthalmic wording requires explicit ocular involvement`() {
        val text = "Apply Flurbiprofen 0.03% ophthalmic solution to the affected eye twice daily."
        val domestic = domesticMedicationsFor(text).single()
        val wild = wildMedicationsFor(text, WildlifeGroup.WILD_CANIDS).single()
        assertTrue(domestic.ocular)
        assertTrue(wild.ocular)
        assertTrue(domestic.label.contains("ophthalmic"))
        assertTrue(wild.label.contains("ophthalmic"))
        assertEquals(DoseRoute.TOPICAL, domestic.drug.route)
    }

    @Test
    fun `picker reuses card route and frequency in both pathways`() {
        val domesticText = "Give meloxicam 0.2 mg/kg IV every 8 hours for 3 days."
        val domesticCard = domesticMedicationsFor(domesticText).single()
        val domesticChoice = domesticDrugChoices(
            domesticText, bestDomesticDrug(domesticText, null, Species.CATTLE),
        ).first { it.id == domesticCard.drug.id }
        assertEquals(DoseRoute.IV, domesticCard.drug.route)
        assertEquals(domesticCard.drug.route, domesticChoice.route)
        assertEquals(domesticCard.drug.frequency, domesticChoice.frequency)
        assertEquals("Every 8 hours", domesticChoice.frequency)

        val wildText = "Give meloxicam 0.2 mg/kg SC every 12 hours for 5 days."
        val wildCard = wildMedicationsFor(wildText, WildlifeGroup.WILD_CANIDS).single()
        val wildChoice = wildDrugChoices(
            wildText, WildlifeGroup.WILD_CANIDS,
            bestWildDrug(wildText, null, WildlifeGroup.WILD_CANIDS),
        ).first { it.id == wildCard.drug.id }
        assertEquals(DoseRoute.SC, wildCard.drug.route)
        assertEquals(wildCard.drug.route, wildChoice.route)
        assertEquals(wildCard.drug.frequency, wildChoice.frequency)
        assertEquals("Every 12 hours", wildChoice.frequency)
    }

    @Test
    fun `applying a route never crosses domestic and wildlife formularies`() {
        val mention = medicationMentions(
            "Enrofloxacin 5 mg/kg SC once", listOf("Enrofloxacin"),
        ).single()
        val domestic = AdministrationRoute.apply(findDrug("enrofloxacin")!!, mention)
        val wild = AdministrationRoute.apply(findWildlifeDrug("wild-enrofloxacin")!!, mention)
        assertEquals(DoseRoute.SC, domestic.route)
        assertEquals(DoseRoute.SC, wild.route)
        assertTrue(domestic.doseBySpecies.keys.none { it == Species.OTHER })
        assertTrue(wild.doseByGroup.keys.all { it is WildlifeGroup })
        assertTrue(domestic.frequency.isNotBlank())
        assertTrue(wild.frequency.isNotBlank())
    }
}
