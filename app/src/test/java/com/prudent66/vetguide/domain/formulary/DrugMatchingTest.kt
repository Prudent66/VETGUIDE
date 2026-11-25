package com.prudent66.vetguide.domain.formulary

import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 24: expanded catalogs, condition matching and analyzed-drug fallbacks. */
class DrugMatchingTest {

    private val domesticNames = listOf(
        "Oxytetracycline 5%", "Oxytetracycline 20%", "Penicillin-Streptomycin",
        "Amoxicillin LA", "Amoxicillin", "Doxycycline", "Tylosin", "Enrofloxacin",
        "Gentamicin", "Ceftiofur", "Meloxicam", "Flunixin Meglumine",
        "Ivermectin 1%", "Albendazole", "Diminazene Aceturate", "Amprolium",
        "Calcium Borogluconate", "Vitamin B-Complex", "Topical Antibiotic Ointment",
        "Wound Spray / Antiseptic Oil", "Oxytetracycline (Aquatic)",
        "Florfenicol (Aquatic)",
    )

    private val wildIds = listOf(
        "wild-oxytetracycline-20", "wild-ivermectin", "wild-ketamine-xylazine",
        "wild-enrofloxacin", "wild-albendazole", "wild-meloxicam", "wild-diminazene",
        "wild-amoxicillin", "wild-doxycycline", "wild-ceftiofur", "wild-atropine",
        "wild-topical-wound",
    )

    @Test
    fun `domestic catalog covers injectables topicals oils and aquatic products`() {
        domesticNames.forEach { name ->
            assertNotNull("missing domestic drug: $name", findDrugByName(name))
        }
        assertEquals(22, formulary.size)
        assertEquals(DoseRoute.TOPICAL, findDrugByName("Topical Antibiotic Ointment")!!.route)
        assertEquals(DoseRoute.TOPICAL, findDrugByName("Wound Spray / Antiseptic Oil")!!.route)
        assertEquals(DoseRoute.BATH, findDrugByName("Oxytetracycline (Aquatic)")!!.route)
        assertEquals(DoseRoute.WATER, findDrugByName("Florfenicol (Aquatic)")!!.route)
    }

    @Test
    fun `wild catalog keeps originals and adds wild specific options`() {
        wildIds.forEach { id -> assertNotNull("missing wild drug: $id", findWildlifeDrug(id)) }
        assertEquals(12, wildlifeFormulary.size)
        assertEquals("Amoxicillin (wild)", findWildlifeDrug("wild-amoxicillin")!!.name)
        assertEquals(DoseRoute.TOPICAL, findWildlifeDrug("wild-topical-wound")!!.route)
        assertEquals(DoseRoute.DART, findWildlifeDrug("wild-ketamine-xylazine")!!.route)
    }

    @Test
    fun `new domestic injectables and topicals calculate across species and weights`() {
        val amoxicillin = calculateDosage(findDrug("amoxicillin")!!, Species.CATTLE, 300.0)!!
        assertEquals(22.5, amoxicillin.syringeMl, 0.001)

        val gentamicin = calculateDosage(findDrug("gentamicin")!!, Species.GOAT, 40.0)!!
        assertEquals(120.0, gentamicin.doseMg, 0.001)
        assertEquals(2.4, gentamicin.syringeMl, 0.001)

        val ointment = calculateDosage(
            findDrug("topical-antibiotic-ointment")!!, Species.DOG, 20.0,
        )!!
        assertEquals(DoseRoute.TOPICAL, ointment.route)
        assertEquals(0.3, ointment.syringeMl, 0.001)
        assertTrue(ointment.frequency.contains("Apply"))

        val calcium = calculateDosage(findDrug("calcium-borogluconate")!!, Species.CATTLE, 500.0)!!
        assertEquals(60.0, calcium.syringeMl, 0.001)
        assertTrue(calcium.safetyAlerts.first().contains("slowly"))
    }

    @Test
    fun `new wild injectables and topicals calculate across groups`() {
        val amoxicillin = calculateWildlifeDosage(
            findWildlifeDrug("wild-amoxicillin")!!, WildlifeGroup.WILD_CANIDS, 20.0,
        )!!
        assertEquals(150.0, amoxicillin.doseMg, 0.001)
        assertEquals(1.5, amoxicillin.syringeMl, 0.001)

        val atropine = calculateWildlifeDosage(
            findWildlifeDrug("wild-atropine")!!, WildlifeGroup.WILD_BIRDS, 2.0,
        )!!
        assertEquals(0.07, atropine.doseMg, 0.001)
        assertEquals(0.07, atropine.syringeMl, 0.001)

        val ointment = calculateWildlifeDosage(
            findWildlifeDrug("wild-topical-wound")!!, WildlifeGroup.WILD_FELIDS, 10.0,
        )!!
        assertEquals(DoseRoute.TOPICAL, ointment.route)
        assertEquals(0.15, ointment.syringeMl, 0.001)
    }

    @Test
    fun `top suspected condition selects a domestic drug`() {
        assertEquals(
            "penicillin-streptomycin",
            bestDomesticDrug("", "Bovine mastitis", Species.CATTLE)!!.id,
        )
        assertEquals(
            "albendazole",
            bestDomesticDrug("", "Helminthosis in goats", Species.GOAT)!!.id,
        )
        assertEquals(
            "oxytetracycline-20",
            bestDomesticDrug("", "Peste des petits ruminants", Species.GOAT)!!.id,
        )
        assertEquals(
            "oxytetracycline-aquatic",
            bestDomesticDrug("", "Fish water-quality stress", Species.FISH)!!.id,
        )
        assertEquals(
            "oxytetracycline-20",
            bestDomesticDrug("Oxytetracycline 20% 15 mg/kg IM", null, Species.CATTLE)!!.id,
        )
    }

    @Test
    fun `top suspected condition selects a wildlife drug`() {
        assertEquals(
            "wild-topical-wound",
            bestWildDrug("", "Skin lesion on canid", WildlifeGroup.WILD_CANIDS)!!.id,
        )
        assertEquals(
            "wild-atropine",
            bestWildDrug("", "Collapse", WildlifeGroup.WILD_RUMINANTS)!!.id,
        )
        assertEquals(
            "wild-oxytetracycline-20",
            bestWildDrug("", "Respiratory signs", WildlifeGroup.WILD_BIRDS)!!.id,
        )
        assertEquals(
            "wild-albendazole",
            bestWildDrug("", "Parasite burden", WildlifeGroup.WILD_RUMINANTS)!!.id,
        )
        assertEquals(
            "wild-enrofloxacin",
            bestWildDrug("Enrofloxacin 5 mg/kg", null, WildlifeGroup.WILD_FELIDS)!!.id,
        )
    }

    @Test
    fun `analyzed domestic drug absent from catalog is added with fallback dose`() {
        val text = "Metronidazole 10 mg/kg PO for 7 days"
        val drugs = domesticDrugsFor(text)
        assertEquals(23, drugs.size)
        val analyzed = drugs.first { it.isAnalyzedFallback }
        assertEquals("Metronidazole (analyzed — verify dose)", analyzed.name)
        assertEquals(
            analyzed.id,
            bestDomesticDrug(text, null, Species.DOG)!!.id,
        )
        val result = calculateDosage(analyzed, Species.DOG, 10.0)!!
        assertEquals(75.0, result.doseMg, 0.001)
        assertEquals(1.5, result.syringeMl, 0.001)
        assertNull(calculateDosage(analyzed, Species.OTHER, 10.0))
    }

    @Test
    fun `analyzed wild drug absent from catalog is added for the selected group`() {
        val text = "Metronidazole (wild) 10 mg/kg IM"
        val drugs = wildDrugsFor(text, WildlifeGroup.WILD_CANIDS)
        assertEquals(wildlifeDrugsFor(WildlifeGroup.WILD_CANIDS).size + 1, drugs.size)
        val analyzed = drugs.first { it.isAnalyzedFallback }
        assertEquals("Metronidazole (wild; analyzed — verify dose)", analyzed.name)
        assertEquals(analyzed.id, bestWildDrug(text, null, WildlifeGroup.WILD_CANIDS)!!.id)
        val result = calculateWildlifeDosage(analyzed, WildlifeGroup.WILD_CANIDS, 10.0)!!
        assertEquals(75.0, result.doseMg, 0.001)
        assertEquals(1.5, result.syringeMl, 0.001)
    }

    @Test
    fun `weight and selected drug changes recalculate domestic dose immediately`() {
        val five = findDrug("oxytetracycline-5")!!
        val twenty = findDrug("oxytetracycline-20")!!
        val first = calculateDosage(five, Species.CATTLE, 100.0)!!
        val heavier = calculateDosage(five, Species.CATTLE, 200.0)!!
        val switched = calculateDosage(twenty, Species.CATTLE, 200.0)!!

        assertEquals(30.0, first.syringeMl, 0.001)
        assertEquals(60.0, heavier.syringeMl, 0.001)
        assertEquals(heavier.doseMg, switched.doseMg, 0.001)
        assertEquals(15.0, switched.syringeMl, 0.001)
        assertEquals(14, switched.meatWithdrawalDays)
    }

    @Test
    fun `weight and selected wild drug changes recalculate dose immediately`() {
        val oxytetracycline = findWildlifeDrug("wild-oxytetracycline-20")!!
        val ivermectin = findWildlifeDrug("wild-ivermectin")!!
        val first = calculateWildlifeDosage(oxytetracycline, WildlifeGroup.WILD_RUMINANTS, 50.0)!!
        val heavier = calculateWildlifeDosage(oxytetracycline, WildlifeGroup.WILD_RUMINANTS, 100.0)!!
        val switched = calculateWildlifeDosage(ivermectin, WildlifeGroup.WILD_RUMINANTS, 100.0)!!

        assertEquals(3.75, first.syringeMl, 0.001)
        assertEquals(7.5, heavier.syringeMl, 0.001)
        assertEquals(25.0, switched.doseMg, 0.001)
        assertEquals(2.5, switched.syringeMl, 0.001)
    }

    @Test
    fun `condition maps and analyzed fallbacks never cross the two pathways`() {
        val domestic = listOf("mastitis", "helminthosis", "pneumonia", "abscess")
            .mapNotNull(::domesticConditionDrug)
        val wild = listOf("skin lesion", "respiratory signs", "parasite burden")
            .mapNotNull { wildConditionDrug(it, WildlifeGroup.WILD_RUMINANTS) }

        assertTrue(domestic.all { it.id in formulary.map { drug -> drug.id } })
        assertTrue(wild.all { it.id in wildlifeFormulary.map { drug -> drug.id } })
        assertTrue(domestic.map { it.id }.none { it in wild.map { drug -> drug.id } })
        assertTrue(domestic.all { it.doseBySpecies.keys.none { key -> key == Species.OTHER } })

        val dynamicDomestic = domesticDrugsFor("Metronidazole").filter { it.isAnalyzedFallback }
        val dynamicWild = wildDrugsFor("Metronidazole (wild)", WildlifeGroup.WILD_CANIDS)
            .filter { it.isAnalyzedFallback }
        assertTrue(dynamicDomestic.all { it.doseBySpecies.containsKey(Species.DOG) })
        assertTrue(dynamicWild.all { it.doseByGroup.containsKey(WildlifeGroup.WILD_CANIDS) })
        assertTrue(dynamicDomestic.none { it.id.startsWith("analyzed-wild") })
        assertTrue(dynamicWild.none { it.id.startsWith("analyzed-domestic") })
    }
}
