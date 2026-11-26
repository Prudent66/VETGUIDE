package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Build 27: every analyzed medication is calculated; warning-only drugs are not. */
class MedicationRecommendationsTest {

    private val multiItem = listOf(
        "Apply Oxytetracycline 2% ophthalmic ointment twice daily.",
        "Apply Flurbiprofen 0.03% ophthalmic solution twice daily.",
        "Do not use human eye drops containing steroids (like prednisolone) " +
            "unless an ulcer has been ruled out.",
    ).joinToString(" ")

    @Test
    fun `all analyzed domestic medications calculate from weight and exclude warnings`() {
        val meds = domesticMedicationsFor(multiItem)
        assertEquals(2, meds.size)
        assertFalse(meds.any { it.label.contains("prednisolone", ignoreCase = true) })
        assertTrue(meds.all { it.drug.isAnalyzedFallback })
        assertTrue(meds.all { it.drug.route == DoseRoute.TOPICAL })
        assertTrue(meds.all { it.label.contains("ophthalmic") })

        val light = meds.map { calculateDosage(it.drug, Species.CATTLE, 10.0)!! }
        val heavy = meds.map { calculateDosage(it.drug, Species.CATTLE, 20.0)!! }
        assertEquals(2, light.size)
        light.forEachIndexed { index, dose ->
            assertEquals(75.0, dose.doseMg, 0.001)
            assertEquals(1.5, dose.syringeMl, 0.001)
            assertEquals(DoseRoute.TOPICAL, dose.route)
            assertEquals("Twice daily", dose.frequency)
            assertEquals(dose.doseMg * 2.0, heavy[index].doseMg, 0.001)
            assertEquals(dose.syringeMl * 2.0, heavy[index].syringeMl, 0.001)
        }
    }

    @Test
    fun `domestic and wild medication lists stay strictly isolated`() {
        val domestic = domesticMedicationsFor(multiItem)
        val wild = wildMedicationsFor(multiItem, WildlifeGroup.WILD_RUMINANTS)
        assertEquals(2, wild.size)
        assertTrue(domestic.all { it.drug.id.startsWith("analyzed-domestic-") })
        assertTrue(wild.all { it.drug.id.startsWith("analyzed-wild-") })
        assertTrue(domestic.all { it.drug.doseBySpecies.keys.none { key -> key == Species.OTHER } })
        assertTrue(wild.all { it.drug.doseByGroup.keys.all { key -> key is WildlifeGroup } })
        assertTrue(domestic.map { it.drug.id }.none { it.startsWith("wild-") })
        assertTrue(wild.map { it.drug.id }.none { it.startsWith("oxytetracycline") })
        assertTrue(wild.all { it.drug.name.contains("wild") })

        val wildLight = wild.map {
            calculateWildlifeDosage(it.drug, WildlifeGroup.WILD_RUMINANTS, 10.0)!!
        }
        val wildHeavy = wild.map {
            calculateWildlifeDosage(it.drug, WildlifeGroup.WILD_RUMINANTS, 20.0)!!
        }
        wildLight.forEachIndexed { index, dose ->
            assertEquals(dose.doseMg * 2.0, wildHeavy[index].doseMg, 0.001)
            assertEquals(dose.syringeMl * 2.0, wildHeavy[index].syringeMl, 0.001)
        }
    }

    @Test
    fun `warned prednisolone is absent from prescriptions defaults and choices`() {
        val domesticDefault = bestDomesticDrug(multiItem, null, Species.CATTLE)
        val wildDefault = bestWildDrug(multiItem, null, WildlifeGroup.WILD_RUMINANTS)
        val offered = listOf(
            domesticMedicationsFor(multiItem).map { it.drug },
            domesticDrugsFor(multiItem),
            domesticDrugChoices(multiItem, domesticDefault),
            wildMedicationsFor(multiItem, WildlifeGroup.WILD_RUMINANTS).map { it.drug },
            wildDrugsFor(multiItem, WildlifeGroup.WILD_RUMINANTS),
            wildDrugChoices(multiItem, WildlifeGroup.WILD_RUMINANTS, wildDefault),
        )
        offered.forEach { drugs ->
            assertTrue(drugs.none {
                it.name.contains("prednisolone", ignoreCase = true) ||
                    it.id.contains("prednisolone", ignoreCase = true)
            })
        }
        assertTrue(domesticDefault?.name?.contains("prednisolone", true) != true)
        assertTrue(wildDefault?.name?.contains("prednisolone", true) != true)
    }

    @Test
    fun `static products named only in warnings are not offered`() {
        val text = "Apply Neosporin ointment twice daily. " +
            "Do not use ivermectin 1% in this patient."
        assertTrue(domesticDrugsFor(text).none { it.id == "ivermectin-1" })
        assertTrue(
            wildDrugsFor(text, WildlifeGroup.WILD_RUMINANTS)
                .none { it.id == "wild-ivermectin" },
        )
    }

    @Test
    fun `replaceable alternatives are identified and recalculate in both pathways`() {
        val alternatives = "Apply Oxytetracycline 2% ophthalmic ointment twice daily " +
            "or Flurbiprofen 0.03% ophthalmic solution twice daily."
        assertTrue(domesticHasAlternatives(alternatives))
        assertTrue(wildHasAlternatives(alternatives))
        assertFalse(domesticHasAlternatives(multiItem))
        assertFalse(wildHasAlternatives(multiItem))

        val domestic = domesticMedicationsFor(alternatives)
        val wild = wildMedicationsFor(alternatives, WildlifeGroup.WILD_RUMINANTS)
        assertEquals(2, domestic.size)
        assertEquals(2, wild.size)
        domestic.forEach { medication ->
            val light = calculateDosage(medication.drug, Species.CATTLE, 10.0)!!
            val heavy = calculateDosage(medication.drug, Species.CATTLE, 20.0)!!
            assertEquals(light.doseMg * 2.0, heavy.doseMg, 0.001)
            assertEquals(light.syringeMl * 2.0, heavy.syringeMl, 0.001)
        }
        wild.forEach { medication ->
            val light = calculateWildlifeDosage(
                medication.drug, WildlifeGroup.WILD_RUMINANTS, 10.0,
            )!!
            val heavy = calculateWildlifeDosage(
                medication.drug, WildlifeGroup.WILD_RUMINANTS, 20.0,
            )!!
            assertEquals(light.doseMg * 2.0, heavy.doseMg, 0.001)
            assertEquals(light.syringeMl * 2.0, heavy.syringeMl, 0.001)
        }
    }

    @Test
    fun `conditional alternatives are identified without being warning filtered`() {
        val text = "Apply Oxytetracycline ointment twice daily. " +
            "If no improvement, apply Flurbiprofen solution twice daily."
        assertTrue(domesticHasAlternatives(text))
        assertTrue(wildHasAlternatives(text))
        assertEquals(2, recommendedMedicationMentions(text, domesticAnalyzedNames).size)
    }

    @Test
    fun `standalone calculator keeps the full formulary when no assessment exists`() {
        assertEquals(formulary.size, domesticDrugsFor("").size)
        assertEquals(formulary.size, domesticDrugChoices("").size)
        assertEquals(
            wildlifeDrugsFor(WildlifeGroup.WILD_RUMINANTS).size,
            wildDrugsFor("", WildlifeGroup.WILD_RUMINANTS).size,
        )
    }

    @Test
    fun `user facing prescription and report sources never mention AI`() {
        val offending = mutableListOf<String>()
        sourceDirs().forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") ||
                        trimmed.startsWith("*") ||
                        trimmed.startsWith("/*")
                    ) return@forEachIndexed
                    stringLiteral.findAll(line).forEach { match ->
                        if (aiWord.containsMatchIn(match.value)) {
                            offending += "${file.name}:${index + 1}"
                        }
                    }
                }
            }
        }
        assertTrue(offending.toString(), offending.isEmpty())
    }

    private fun sourceDirs(): List<File> {
        var root = File(".").absoluteFile
        val marker = "app/src/main/java/com/prudent66/vetguide"
        while (root.parentFile != null && !File(root, marker).isDirectory) {
            root = root.parentFile!!
        }
        val base = File(root, marker)
        return listOf("ui/components", "domain/formulary", "domain/ai", "data/report")
            .map { File(base, it) }
    }

    private companion object {
        val stringLiteral = Regex("\"[^\"]*\"")
        val aiWord = Regex("\\bAI\\b")
    }
}
