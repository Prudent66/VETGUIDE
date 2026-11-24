package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Herd/flock epidemiology and whole-group logistics calculations. */
class HerdCalculatorTest {

    private val cattleHerd = HerdSnapshot(
        unitName = "North pen",
        species = Species.CATTLE,
        headcount = 100,
        sickCount = 20,
        deadCount = 5,
        averageWeightKg = 300.0,
    )

    @Test
    fun `morbidity and case fatality rates use affected animals`() {
        val metrics = herdMetrics(cattleHerd)

        assertNotNull(metrics)
        assertEquals(20.0, metrics!!.morbidityPercent, 0.001)
        assertEquals(20.0, metrics.caseFatalityPercent, 0.001)
        assertEquals(25, metrics.affectedCount)
    }

    @Test
    fun `zero deaths yields zero case fatality`() {
        val metrics = herdMetrics(cattleHerd.copy(deadCount = 0, sickCount = 10))

        assertEquals(10.0, metrics!!.morbidityPercent, 0.001)
        assertEquals(0.0, metrics.caseFatalityPercent, 0.001)
    }

    @Test
    fun `invalid headcount yields no metrics`() {
        assertNull(herdMetrics(cattleHerd.copy(headcount = 0)))
    }

    @Test
    fun `batch treatment computes total milligrams litres and vial count`() {
        val drug = findDrug("oxytetracycline-5")!!
        val batch = batchTreatment(drug, cattleHerd)

        assertNotNull(batch)
        assertEquals(15.0, batch!!.doseMgPerKg, 0.001)
        assertEquals(450_000.0, batch.totalMg, 0.001)
        assertEquals(9_000.0, batch.totalMl, 0.001)
        assertEquals(9.0, batch.totalLiters, 0.001)
        assertEquals(90, batch.bottleCount)
        assertEquals(null, batch.waterVolumeLiters)
        assertEquals(7, batch.meatWithdrawalDays)
        assertEquals(3, batch.milkWithdrawalDays)
    }

    @Test
    fun `poultry water medication reports a drinking water mixing volume`() {
        val flock = HerdSnapshot(
            unitName = "Broiler house",
            species = Species.POULTRY,
            headcount = 1_000,
            sickCount = 80,
            deadCount = 20,
            averageWeightKg = 2.0,
        )
        val batch = batchTreatment(findDrug("amprolium")!!, flock)!!

        assertEquals(60_000.0, batch.totalMg, 0.001)
        assertEquals(300.0, batch.totalMl, 0.001)
        assertEquals(3.0, batch.waterVolumeLiters!!, 0.001)
        assertEquals(3, batch.bottleCount)
    }

    @Test
    fun `unsupported species and invalid weights yield no batch`() {
        assertNull(batchTreatment(findDrug("enrofloxacin")!!, cattleHerd.copy(species = Species.EQUINE)))
        assertNull(batchTreatment(findDrug("oxytetracycline-5")!!, cattleHerd.copy(averageWeightKg = 0.0)))
    }

    @Test
    fun `wildlife quarantine checklist is biosafety first`() {
        val steps = wildlifeQuarantineChecklist()

        assertTrue(steps.any { it.contains("PPE", ignoreCase = true) })
        assertTrue(steps.any { it.contains("anthrax", ignoreCase = true) })
        assertTrue(steps.any { it.contains("domestic animals", ignoreCase = true) })
    }

    @Test
    fun `quarantine checklist includes universal and disease specific steps`() {
        val checklist = quarantineChecklist(listOf("foot-and-mouth-disease", "newcastle-disease"))

        assertTrue(checklist.any { it.contains("Isolate") })
        assertTrue(checklist.any { it.contains("FMD") })
        assertTrue(checklist.any { it.contains("Newcastle") })
        assertEquals(8, checklist.size)
    }
}
