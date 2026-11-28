package com.prudent66.vetguide.ui.components

import com.prudent66.vetguide.domain.formulary.DoseRange
import com.prudent66.vetguide.domain.formulary.DoseRoute
import com.prudent66.vetguide.domain.formulary.bestDomesticDrug
import com.prudent66.vetguide.domain.formulary.bestWildDrug
import com.prudent66.vetguide.domain.formulary.calculateDosage
import com.prudent66.vetguide.domain.formulary.calculateWildlifeDosage
import com.prudent66.vetguide.domain.formulary.domesticDrugChoices
import com.prudent66.vetguide.domain.formulary.domesticMedicationsFor
import com.prudent66.vetguide.domain.formulary.findDrug
import com.prudent66.vetguide.domain.formulary.findWildlifeDrug
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Group 2 Phase 3: one resolved dose rule drives numbers, note and fallbacks. */
class DoseRuleConsistencyTest {

    @Test
    fun `plan mg per kg overrides domestic formulary midpoint`() {
        val text = "Oxytetracycline 20% 5 mg/kg IM"
        val selected = bestDomesticDrug(text, null, Species.CATTLE)!!
        val result = calculateDosage(selected, Species.CATTLE, 10.0)!!
        assertEquals(DoseRange(5.0, 5.0), result.doseRange)
        assertEquals(5.0, result.selectedMgPerKg, 0.001)
        assertEquals(50.0, result.doseMg, 0.001)
    }

    @Test
    fun `plan mg per kg overrides wildlife formulary midpoint`() {
        val text = "Oxytetracycline 20% (wild) 5 mg/kg IM"
        val selected = bestWildDrug(text, null, WildlifeGroup.WILD_RUMINANTS)!!
        val result = calculateWildlifeDosage(selected, WildlifeGroup.WILD_RUMINANTS, 10.0)!!
        assertEquals(DoseRange(5.0, 5.0), result.doseRange)
        assertEquals(5.0, result.selectedMgPerKg, 0.001)
        assertEquals(50.0, result.doseMg, 0.001)
    }

    @Test
    fun `card and manual picker share the same resolved dose rule`() {
        val text = "Oxytetracycline 20% 5 mg/kg IM"
        val card = domesticMedicationsFor(text).single()
        val picker = domesticDrugChoices(
            text, bestDomesticDrug(text, null, Species.CATTLE),
        ).first { it.id == card.drug.id }
        val cardResult = calculateDosage(card.drug, Species.CATTLE, 10.0)!!
        val pickerResult = calculateDosage(picker, Species.CATTLE, 10.0)!!
        assertEquals(cardResult.doseRange, pickerResult.doseRange)
        assertEquals(cardResult.selectedMgPerKg, pickerResult.selectedMgPerKg, 0.001)
        assertEquals(cardResult.syringeMl, pickerResult.syringeMl, 0.001)
    }

    @Test
    fun `domestic narrative note repeats the calculated numbers`() {
        val result = calculateDosage(findDrug("oxytetracycline-5")!!, Species.CATTLE, 10.0)!!
        val note = domesticDoseNote(result)
        assertTrue(note.contains("15 mg/kg"))
        assertTrue(note.contains("range 10–20 mg/kg"))
        assertTrue(note.contains("150 mg"))
        assertTrue(note.contains("3 mL"))
        assertTrue(note.contains("50 mg/mL"))
    }

    @Test
    fun `wildlife narrative note repeats the calculated numbers`() {
        val drug = findWildlifeDrug("wild-oxytetracycline-20")!!
        val result = calculateWildlifeDosage(drug, WildlifeGroup.WILD_RUMINANTS, 10.0)!!
        val note = wildDoseNote(result)
        assertTrue(note.contains("15 mg/kg"))
        assertTrue(note.contains("range 10–20 mg/kg"))
        assertTrue(note.contains("150 mg"))
        assertTrue(note.contains("0.75 mL"))
    }

    @Test
    fun `narrative note follows the plan dose instead of the free text number`() {
        val text = "Apply Ivermectin 0.2 mg/kg Topical for mites"
        val drug = bestDomesticDrug(text, null, Species.CATTLE)!!
        val result = calculateDosage(drug, Species.CATTLE, 10.0)!!
        val note = domesticDoseNote(result)
        assertEquals(0.2, result.selectedMgPerKg, 0.001)
        assertTrue(note.contains("0.20 mg/kg"))
        assertFalse(note.contains("7.5 mg/kg"))
    }

    @Test
    fun `semisolid topical note uses application wording instead of syringe volume`() {
        val drug = findDrug("topical-antibiotic-ointment")!!
        val result = calculateDosage(drug, Species.CATTLE, 10.0)!!
        val note = domesticDoseNote(result)
        assertTrue(note.contains("thin layer"))
        assertTrue(note.contains("15 mg"))
        assertFalse(note.contains("mL"))
        assertFalse(note.contains("bottle"))
    }

    @Test
    fun `ophthalmic ointment card uses eye application wording without syringe volume`() {
        val text = "Apply Oxytetracycline ophthalmic ointment to the affected eye twice daily."
        val medication = domesticMedicationsFor(text).single()
        val result = calculateDosage(medication.drug, Species.CATTLE, 10.0)!!
        assertTrue(medication.ocular)
        assertTrue(isSemisolidTopical(result.route, result.drugName, medication.ocular))
        assertEquals("Estimated Topical Amount", volumeLabel(result.route, true))
        val note = domesticDoseNote(result, medication.ocular)
        assertTrue(note.contains("affected eye"))
        assertTrue(note.contains("thin layer"))
        assertFalse(note.contains("mL"))
        assertFalse(note.contains("bottle"))
    }

    @Test
    fun `liquid ophthalmic solution keeps mL bottle note framing`() {
        val text = "Apply Flurbiprofen 0.03% ophthalmic solution to the affected eye twice daily."
        val medication = domesticMedicationsFor(text).single()
        val result = calculateDosage(medication.drug, Species.CATTLE, 10.0)!!
        assertTrue(medication.ocular)
        assertFalse(isSemisolidTopical(result.route, result.drugName, medication.ocular))
        val note = domesticDoseNote(result, medication.ocular)
        assertTrue(note.contains("mL"))
        assertTrue(note.contains("mg/mL bottle"))
        assertFalse(note.contains("affected eye"))
    }

    @Test
    fun `missing species preset explains how to adjust or verify`() {
        val drug = findDrug("ivermectin-1")!!
        val message = domesticDoseFallback(drug, Species.POULTRY, DoseRoute.SC, 10.0)
        assertTrue(message.contains("No preset mg/kg rule"))
        assertTrue(message.contains("Poultry"))
        assertTrue(message.contains("Choose another species, drug or route"))
        assertTrue(message.contains("adjust manually"))
    }

    @Test
    fun `route mismatch and invalid concentration get specific guidance`() {
        val drug = findDrug("ivermectin-1")!!
        val routeMessage = domesticDoseFallback(drug, Species.CATTLE, DoseRoute.TOPICAL, 10.0)
        assertTrue(routeMessage.contains("Topical"))
        assertTrue(routeMessage.contains("No preset mg/kg rule"))
        val concentrationMessage = domesticDoseFallback(drug, Species.CATTLE, DoseRoute.SC, 0.0)
        assertTrue(concentrationMessage.contains("No usable bottle concentration"))
        assertTrue(concentrationMessage.contains("verify the product strength"))
    }

    @Test
    fun `wild fallback uses only wildlife group presets`() {
        val drug = findWildlifeDrug("wild-ivermectin")!!
        val message = wildDoseFallback(drug, WildlifeGroup.WILD_FELIDS, DoseRoute.SC, 10.0)
        assertTrue(message.contains("No preset mg/kg rule"))
        assertTrue(message.contains("Lion / Leopard / Cheetah"))
        assertTrue(message.contains("adjust manually"))
        val good = wildDoseFallback(drug, WildlifeGroup.WILD_RUMINANTS, DoseRoute.SC, 10.0)
        assertTrue(good.contains("10 mg/mL"))
        assertFalse(good.contains("Choose another species"))
    }
}
