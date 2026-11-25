package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline formulary: dose, syringe volume, withdrawal and safety calculations. */
class DosageCalculatorTest {

    @Test
    fun `oxytetracycline five percent dose and syringe volume scale with weight`() {
        val drug = findDrug("oxytetracycline-5")!!
        val result = calculateDosage(drug, Species.CATTLE, weightKg = 300.0)

        assertNotNull(result)
        // Range 10–20 mg/kg → recommended midpoint 15 mg/kg.
        assertEquals(15.0, result!!.selectedMgPerKg, 0.001)
        assertEquals(4500.0, result.doseMg, 0.001)
        // 50 mg/mL bottle → 4500 / 50 = 90 mL.
        assertEquals(90.0, result.syringeMl, 0.001)
        assertEquals(DoseRoute.IM, result.route)
        assertEquals(7, result.meatWithdrawalDays)
        assertEquals(3, result.milkWithdrawalDays)
    }

    @Test
    fun `twenty percent bottle changes the syringe volume but not the milligram dose`() {
        val five = findDrug("oxytetracycline-5")!!
        val twenty = findDrug("oxytetracycline-20")!!
        val low = calculateDosage(five, Species.CATTLE, 300.0)!!
        val high = calculateDosage(twenty, Species.CATTLE, 300.0)!!

        assertEquals(low.doseMg, high.doseMg, 0.001)
        assertEquals(90.0, low.syringeMl, 0.001)
        assertEquals(22.5, high.syringeMl, 0.001)
        assertEquals(14, high.meatWithdrawalDays)
        assertEquals(4, high.milkWithdrawalDays)
    }

    @Test
    fun `selectable bottle concentration changes the draw volume`() {
        val drug = findDrug("albendazole")!!
        val dilute = calculateDosage(drug, Species.CATTLE, 100.0, concentrationMgPerMl = 50.0)!!
        val concentrated = calculateDosage(drug, Species.CATTLE, 100.0, concentrationMgPerMl = 100.0)!!

        assertEquals(750.0, dilute.doseMg, 0.001)
        assertEquals(15.0, dilute.syringeMl, 0.001)
        assertEquals(7.5, concentrated.syringeMl, 0.001)
    }

    @Test
    fun `ivermectin warns about heartworm status in dogs`() {
        val drug = findDrug("ivermectin-1")!!
        val result = calculateDosage(drug, Species.DOG, 20.0)!!

        assertEquals(5.0, result.doseMg, 0.001)
        assertEquals(0.5, result.syringeMl, 0.001)
        assertEquals(1, result.safetyAlerts.size)
        assertTrue(result.safetyAlerts.first().contains("heartworm"))
    }

    @Test
    fun `amprolium reports poultry withdrawal periods`() {
        val drug = findDrug("amprolium")!!
        val result = calculateDosage(drug, Species.POULTRY, 2.0)!!

        assertEquals(60.0, result.doseMg, 0.001)
        assertEquals(0.3, result.syringeMl, 0.001)
        assertEquals(DoseRoute.WATER, result.route)
        assertEquals(5, result.meatWithdrawalDays)
        assertEquals(0, result.milkWithdrawalDays)
        assertTrue(result.safetyAlerts.isEmpty())
    }

    @Test
    fun `unsupported species and invalid weights produce no result`() {
        val penicillin = findDrug("penicillin-streptomycin")!!
        assertNull(calculateDosage(penicillin, Species.POULTRY, 10.0))
        assertNull(calculateDosage(findDrug("oxytetracycline-5")!!, Species.CATTLE, 0.0))
        assertNull(
            calculateDosage(
                findDrug("oxytetracycline-5")!!,
                Species.CATTLE,
                100.0,
                concentrationMgPerMl = 0.0,
            ),
        )
    }

    @Test
    fun `prescription text auto-matches the mentioned formulary drug`() {
        assertEquals(
            "oxytetracycline-20",
            matchDrugFromText("Oxytetracycline 20% 15 mg/kg IM once daily")!!.id,
        )
        assertEquals("meloxicam", matchDrugFromText("Give meloxicam 0.3 mg/kg SC")!!.id)
        assertEquals("enrofloxacin", matchDrugFromText("Enrofloxacin 5 mg/kg PO")!!.id)
        assertEquals("ivermectin-1", matchDrugFromText("Ivermectin 1% pour-on")!!.id)
        assertEquals("albendazole", matchDrugFromText("Albendazole 10 mg/kg")!!.id)
        assertEquals("tylosin", matchDrugFromText("Tylosin in drinking water")!!.id)
        assertEquals(null, matchDrugFromText("Isolate affected birds and disinfect drinkers"))
    }

    @Test
    fun `generic ivermectin matches the one percent product`() {
        assertEquals("ivermectin-1", matchDrugFromText("ivermectin")!!.id)
        assertEquals("ivermectin-1", matchDrugFromText("Give IVERMECTIN 1% SC")!!.id)
        assertEquals("oxytetracycline-5", matchDrugFromText("oxytetracycline 10 mg/kg")!!.id)
        assertEquals("oxytetracycline-20", matchDrugFromText("oxytetracycline 20 mg/kg")!!.id)
        assertEquals("penicillin-streptomycin", matchDrugFromText("penicillin streptomycin")!!.id)
    }

    @Test
    fun `all ten formulary entries are discoverable by id and name`() {
        assertEquals(10, formulary.size)
        assertEquals("Enrofloxacin", findDrugByName("enrofloxacin")!!.name)
        assertEquals("Diminazene Aceturate", findDrug("diminazene-aceturate")!!.name)
        assertTrue(findDrugByName("not-a-real-drug") == null)
    }

    @Test
    fun `wild and domestic formularies are strictly isolated`() {
        // Domestic keys never include the wild placeholder or a wildlife group.
        formulary.forEach { drug ->
            assertTrue(drug.doseBySpecies.keys.none { it == Species.OTHER })
        }
        // The domestic calculator never resolves Species.OTHER (wild patients).
        assertNull(calculateDosage(findDrug("oxytetracycline-20")!!, Species.OTHER, 15.0))
        assertNull(calculateDosage(findDrug("oxytetracycline-5")!!, Species.DOG, 15.0, 0.0))

        // Wildlife entries are keyed only by WildlifeGroup / wild subtype.
        wildlifeFormulary.forEach { drug ->
            assertTrue(drug.doseByGroup.isNotEmpty())
            assertTrue(drug.doseByGroup.keys.all { it is WildlifeGroup })
        }
        assertTrue(wildlifeFormulary.none { it.id in formulary.map { drug -> drug.id } })
        assertNull(findWildlifeDrug("oxytetracycline-20"))
    }

    @Test
    fun `wild patient resolves directly in wildlife formulary with syringe volume`() {
        val drug = matchWildlifeDrugFromText(
            "Oxytetracycline 20% 15 mg/kg IM", WildlifeGroup.WILD_CANIDS,
        )!!
        assertEquals("wild-oxytetracycline-20", drug.id)

        val result = calculateWildlifeDosage(drug, WildlifeGroup.WILD_CANIDS, 15.0, 200.0)!!

        assertEquals(15.0, result.selectedMgPerKg, 0.001)
        assertEquals(225.0, result.doseMg, 0.001)
        // 225 mg / 200 mg/mL = 1.125 mL syringe draw for a 15 kg animal.
        assertEquals(1.125, result.syringeMl, 0.001)
        assertEquals(DoseRoute.IM, result.route)
        assertEquals("Jackal / Fox / Wild Dog", result.animalLabel)
        assertEquals(14, result.meatWithdrawalDays)
    }

    @Test
    fun `wild subtype dose overrides group dose and carries wildlife warning`() {
        val drug = findWildlifeDrug("wild-oxytetracycline-20")!!
        val generic = calculateWildlifeDosage(drug, WildlifeGroup.WILD_CANIDS, 15.0, 200.0)!!
        val specific = calculateWildlifeDosage(
            drug, WildlifeGroup.WILD_CANIDS, 15.0, 200.0, "African Wild Dog",
        )!!

        assertEquals(15.0, generic.selectedMgPerKg, 0.001)
        assertEquals(16.0, specific.selectedMgPerKg, 0.001)
        assertEquals("African Wild Dog · Jackal / Fox / Wild Dog", specific.animalLabel)
        assertEquals(240.0, specific.doseMg, 0.001)
        assertEquals(1.2, specific.syringeMl, 0.001)

        val birds = calculateWildlifeDosage(
            drug, WildlifeGroup.WILD_BIRDS, 2.0, 200.0,
        )!!
        assertEquals(1, birds.safetyAlerts.size)
        assertTrue(birds.safetyAlerts.first().contains("birds"))
        assertEquals(DoseRoute.IM, birds.route)
    }

    @Test
    fun `dart and oral wildlife routes are available by group`() {
        val dart = findWildlifeDrug("wild-ketamine-xylazine")!!
        val result = calculateWildlifeDosage(dart, WildlifeGroup.WILD_RUMINANTS, 100.0, 100.0)!!
        assertEquals(DoseRoute.DART, result.route)
        assertTrue(result.safetyAlerts.first().contains("myopathy"))

        val oral = findWildlifeDrug("wild-enrofloxacin")!!
        assertEquals(
            DoseRoute.ORAL,
            calculateWildlifeDosage(oral, WildlifeGroup.WILD_FELIDS, 10.0, 50.0)!!.route,
        )
        assertEquals(4, wildlifeDrugsFor(WildlifeGroup.WILD_FELIDS).size)
    }
}
