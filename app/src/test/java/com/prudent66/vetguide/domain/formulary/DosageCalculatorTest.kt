package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
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
    fun `all ten formulary entries are discoverable by id and name`() {
        assertEquals(10, formulary.size)
        assertEquals("Enrofloxacin", findDrugByName("enrofloxacin")!!.name)
        assertEquals("Diminazene Aceturate", findDrug("diminazene-aceturate")!!.name)
        assertTrue(findDrugByName("not-a-real-drug") == null)
    }
}
