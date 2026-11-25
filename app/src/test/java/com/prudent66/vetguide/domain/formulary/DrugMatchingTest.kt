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
        assertEquals(100.0, result.doseMg, 0.001)
        assertEquals(2.0, result.syringeMl, 0.001)
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
        assertEquals(100.0, result.doseMg, 0.001)
        assertEquals(2.0, result.syringeMl, 0.001)
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

    @Test
    fun `domestic picker pins analyzed anthelmintics and selects them on open`() {
        val text = "Deworm with fenbendazole (50 mg/kg orally) or pyrantel pamoate (5-10 mg/kg orally)"
        val drugs = domesticDrugsFor(text)
        assertEquals(2, drugs.count { it.isAnalyzedFallback })
        assertTrue(drugs.take(2).all { it.isAnalyzedFallback })

        val selected = bestDomesticDrug(text, "Helminthosis", Species.CATTLE)!!
        assertTrue(selected.isAnalyzedFallback)
        assertEquals("Fenbendazole (analyzed — verify dose)", selected.name)
        assertEquals(DoseRoute.ORAL, selected.route)
        val result = calculateDosage(selected, Species.CATTLE, 100.0)!!
        assertEquals(5000.0, result.doseMg, 0.001)
        assertEquals(100.0, result.syringeMl, 0.001)
    }

    @Test
    fun `wild picker pins analyzed anthelmintics instead of oxytetracycline`() {
        val text = "Deworm with fenbendazole (50 mg/kg orally) or pyrantel pamoate (5-10 mg/kg orally)"
        val drugs = wildDrugsFor(text, WildlifeGroup.WILD_RUMINANTS)
        assertTrue(drugs.first().isAnalyzedFallback)
        assertEquals(2, drugs.count { it.isAnalyzedFallback })

        val selected = bestWildDrug(text, null, WildlifeGroup.WILD_RUMINANTS)!!
        assertTrue(selected.isAnalyzedFallback)
        assertEquals("Fenbendazole (wild; analyzed — verify dose)", selected.name)
        assertEquals(DoseRoute.ORAL, selected.route)
        val result = calculateWildlifeDosage(selected, WildlifeGroup.WILD_RUMINANTS, 20.0)!!
        assertEquals(1000.0, result.doseMg, 0.001)
        assertEquals(20.0, result.syringeMl, 0.001)
    }

    @Test
    fun `ivermectin matches normalized static products in both pathways`() {
        val domesticText = "Ivermectin 1% for mange"
        assertEquals(0, domesticDrugsFor(domesticText).count { it.isAnalyzedFallback })
        assertEquals(
            "ivermectin-1",
            bestDomesticDrug(domesticText, null, Species.CATTLE)!!.id,
        )

        val wildText = "Ivermectin 1% (wild) dart"
        assertEquals(0, wildDrugsFor(wildText, WildlifeGroup.WILD_RUMINANTS)
            .count { it.isAnalyzedFallback })
        assertEquals(
            "wild-ivermectin",
            bestWildDrug(wildText, null, WildlifeGroup.WILD_RUMINANTS)!!.id,
        )
    }

    @Test
    fun `prednisolone is added dynamically to the wild picker with parsed dose`() {
        val text = "Prednisolone 1 mg/kg orally for seven days"
        val drugs = wildDrugsFor(text, WildlifeGroup.WILD_CANIDS)
        val analyzed = drugs.first { it.isAnalyzedFallback }
        assertEquals("Prednisolone (wild; analyzed — verify dose)", analyzed.name)
        assertEquals(DoseRange(1.0, 1.0), analyzed.doseByGroup[WildlifeGroup.WILD_CANIDS])
        assertEquals(DoseRoute.ORAL, analyzed.route)
        assertEquals(analyzed.id, bestWildDrug(text, null, WildlifeGroup.WILD_CANIDS)!!.id)
    }

    @Test
    fun `changing weight or drug recalculates an analyzed dose immediately`() {
        val text = "Fenbendazole 50 mg/kg orally"
        val drug = domesticDrugsFor(text).first { it.isAnalyzedFallback }
        val light = calculateDosage(drug, Species.GOAT, 10.0)!!
        val heavier = calculateDosage(drug, Species.GOAT, 20.0)!!
        val switched = calculateDosage(findDrug("albendazole")!!, Species.GOAT, 20.0)!!

        assertEquals(500.0, light.doseMg, 0.001)
        assertEquals(1000.0, heavier.doseMg, 0.001)
        assertEquals(10.0, light.syringeMl, 0.001)
        assertEquals(20.0, heavier.syringeMl, 0.001)
        assertEquals(DoseRange(5.0, 10.0), switched.doseRange)
    }

    @Test
    fun `Build 25 analyzed additions stay isolated to their own pathway`() {
        val text = "Fenbendazole 50 mg/kg or Prednisolone"
        val domestic = domesticDrugsFor(text).filter { it.isAnalyzedFallback }
        val wild = wildDrugsFor(text, WildlifeGroup.WILD_RUMINANTS)
            .filter { it.isAnalyzedFallback }

        assertEquals(2, domestic.size)
        assertEquals(2, wild.size)
        assertTrue(domestic.all { it.id.startsWith("analyzed-domestic-") })
        assertTrue(wild.all { it.id.startsWith("analyzed-wild-") })
        assertTrue(domestic.all { !it.name.contains("wild") })
        assertTrue(wild.all { it.name.contains("wild") })
        assertTrue(domestic.all { it.doseBySpecies.keys.none { key -> key == Species.OTHER } })
        assertTrue(wild.all { it.doseByGroup.keys.all { key -> key is WildlifeGroup } })
    }

    @Test
    fun `Build 26 domestic topical recommendation selects a topical instead of oxytetracycline`() {
        val text = "Apply Neosporin antibiotic ointment to the lesion twice daily."
        val drugs = domesticDrugsFor(text)
        assertTrue(drugs.any { it.isAnalyzedFallback && it.name.startsWith("Neosporin") })

        val selected = bestDomesticDrug(text, "Skin infection", Species.DOG)!!
        assertEquals("topical-antibiotic-ointment", selected.id)
        assertEquals(DoseRoute.TOPICAL, selected.route)
        assertTrue(selected.id != "oxytetracycline-5")

        val result = calculateDosage(selected, Species.DOG, 10.0)!!
        assertEquals(DoseRoute.TOPICAL, result.route)
        assertEquals(0.15, result.syringeMl, 0.001)
        assertTrue(result.frequency.contains("Apply"))
    }

    @Test
    fun `Build 26 domestic condition outranks an incidental oxytetracycline keyword`() {
        val text = "Rule out oxytetracycline-responsive pneumonia; treat the skin abscess."
        val selected = bestDomesticDrug(text, "Skin abscess", Species.DOG)!!
        assertEquals("topical-antibiotic-ointment", selected.id)
        assertEquals(DoseRoute.TOPICAL, selected.route)
    }

    @Test
    fun `Build 26 recommended oxytetracycline still selects and calculates in both pathways`() {
        val domestic = bestDomesticDrug("Oxytetracycline 20% 15 mg/kg IM", "Pneumonia", Species.CATTLE)!!
        assertEquals("oxytetracycline-20", domestic.id)
        val domesticResult = calculateDosage(domestic, Species.CATTLE, 100.0)!!
        assertEquals(1500.0, domesticResult.doseMg, 0.001)
        assertEquals(7.5, domesticResult.syringeMl, 0.001)
        assertEquals(DoseRoute.IM, domesticResult.route)

        val wildText = "Oxytetracycline 20% (wild) 15 mg/kg IM"
        val wild = bestWildDrug(wildText, null, WildlifeGroup.WILD_RUMINANTS)!!
        assertEquals("wild-oxytetracycline-20", wild.id)
        val wildResult = calculateWildlifeDosage(wild, WildlifeGroup.WILD_RUMINANTS, 100.0)!!
        assertEquals(1500.0, wildResult.doseMg, 0.001)
        assertEquals(7.5, wildResult.syringeMl, 0.001)
    }

    @Test
    fun `Build 26 topical weight recalculation stays in the domestic pathway`() {
        val text = "Mupirocin ointment apply twice daily"
        val domestic = domesticDrugsFor(text).first { it.isAnalyzedFallback }
        assertEquals(DoseRoute.TOPICAL, domestic.route)
        val light = calculateDosage(domestic, Species.CAT, 2.0)!!
        val heavier = calculateDosage(domestic, Species.CAT, 4.0)!!
        assertEquals(light.doseMg * 2.0, heavier.doseMg, 0.001)
        assertEquals(light.syringeMl * 2.0, heavier.syringeMl, 0.001)
        assertEquals(DoseRoute.TOPICAL, heavier.route)

        val wild = wildDrugsFor(text, WildlifeGroup.WILD_CANIDS).filter { it.isAnalyzedFallback }
        assertTrue(domestic.id.startsWith("analyzed-domestic-"))
        assertTrue(wild.all { it.id.startsWith("analyzed-wild-") })
        assertTrue(wild.none { it.name == domestic.name })
    }
}
