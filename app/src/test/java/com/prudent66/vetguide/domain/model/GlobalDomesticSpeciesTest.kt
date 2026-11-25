package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 23: global domestic species and breeds stay isolated from wildlife. */
class GlobalDomesticSpeciesTest {

    private val globalSpecies = listOf(
        Species.CATTLE, Species.GOAT, Species.SHEEP, Species.POULTRY,
        Species.PIG, Species.EQUINE, Species.DOG, Species.CAT,
        Species.BUFFALO, Species.CAMEL, Species.RABBIT, Species.FISH,
    )

    @Test
    fun `all global domestic species are offered with unique labels`() {
        val labels = ClinicalSpeciesCatalog.domesticLabels
        assertEquals(13, labels.size)
        globalSpecies.forEach { species ->
            val label = ClinicalSpeciesCatalog.label(species)
            assertTrue("$species missing from picker", label in labels)
            assertEquals(species, ClinicalSpeciesCatalog.domesticSpecies(label))
        }
        assertEquals("Other Domestic", ClinicalSpeciesCatalog.label(Species.OTHER))
        assertEquals(Species.OTHER, ClinicalSpeciesCatalog.domesticSpecies("Other Domestic"))
        assertEquals(13, ClinicalSpeciesCatalog.domesticValues().size)
    }

    @Test
    fun `legacy other records remain resolvable as other domestic`() {
        assertEquals(Species.OTHER, ClinicalSpeciesCatalog.domesticSpecies("Other"))
        assertEquals("Other Domestic", ClinicalSpeciesCatalog.label(Species.OTHER))
        val legacy = patient(Species.OTHER)
        assertEquals("Other Domestic", ClinicalSpeciesCatalog.display(legacy))
        assertEquals("Other Domestic", ClinicalSpeciesCatalog.reportLabel(legacy))
    }

    @Test
    fun `domestic labels never collide with wildlife groups`() {
        val wildLabels = WildlifeGroup.entries.map { it.label }.toSet()
        ClinicalSpeciesCatalog.domesticLabels.forEach { label ->
            assertFalse("domestic label collides with wildlife: $label", label in wildLabels)
            assertFalse(
                "domestic label resolves as wildlife: $label",
                ClinicalSpeciesCatalog.wildlifeGroup(label) != null,
            )
        }
        WildlifeGroup.entries.forEach { group ->
            assertNull(ClinicalSpeciesCatalog.domesticSpecies(group.label))
        }
    }

    @Test
    fun `every species has global and local breeds including crossbreed`() {
        globalSpecies.forEach { species ->
            val breeds = BreedCatalog.forSpecies(species)
            assertTrue("no breeds for $species", breeds.isNotEmpty())
            if (species == Species.FISH) {
                assertTrue("fish has no ornamental option", "Ornamental / Koi" in breeds)
            } else {
                assertTrue(
                    "$species has no crossbreed option",
                    breeds.any { it.contains("Mixed", ignoreCase = true) },
                )
            }
        }
        assertTrue(BreedCatalog.forSpecies(Species.CATTLE).containsAll(
            listOf("Holstein-Friesian", "Brahman", "White Fulani", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.GOAT).containsAll(
            listOf("Boer", "Anglo-Nubian", "Red Sokoto", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.SHEEP).containsAll(
            listOf("Merino", "Dorper", "Yankasa", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.POULTRY).containsAll(
            listOf("Ross / Cobb Broiler", "Guinea Fowl", "Local Chicken", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.PIG).containsAll(
            listOf("Duroc", "Yorkshire", "Large White", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.EQUINE).containsAll(
            listOf("Quarter Horse", "Donkey / Mule", "Arabian", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.DOG).containsAll(
            listOf("Golden Retriever", "Rottweiler", "Labrador", "Crossbreed / Mixed"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.CAT).containsAll(
            listOf("Maine Coon", "Sphynx", "Persian", "Crossbreed / Mixed"),
        ))
    }

    @Test
    fun `farmed species have their named global breeds`() {
        assertTrue(BreedCatalog.forSpecies(Species.BUFFALO).containsAll(
            listOf("Murrah", "Nili-Ravi", "Swamp Buffalo", "African Domestic Buffalo"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.CAMEL).containsAll(
            listOf("Dromedary (One-Humped)", "Bactrian (Two-Humped)", "Sahelian Dromedary"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.RABBIT).containsAll(
            listOf("New Zealand White", "Flemish Giant", "Chinchilla"),
        ))
        assertTrue(BreedCatalog.forSpecies(Species.FISH).containsAll(
            listOf("African Catfish (Clarias)", "Nile Tilapia", "Rainbow Trout", "Ornamental / Koi"),
        ))
    }

    @Test
    fun `domestic and wild subjects stay isolated`() {
        val goat = AnimalSubject.domestic(Species.GOAT)
        assertEquals("Goat", goat.label)
        assertNull(goat.wildlifeGroup)

        val wild = AnimalSubject.wild("Jackal / Fox / Wild Dog", "African Wild Dog")
        assertNull(wild.species)
        assertEquals("African Wild Dog · Jackal / Fox / Wild Dog", wild.label)
        assertFalse(wild.label.contains("Goat"))
        assertEquals(AnimalCategory.WILD, AnimalSubject.fromPatient(
            patient(Species.OTHER, AnimalCategory.WILD, "Jackal / Fox / Wild Dog", "African Wild Dog"),
        ).category)
    }

    private fun patient(
        species: Species,
        category: AnimalCategory = AnimalCategory.DOMESTIC,
        group: String? = null,
        breed: String? = null,
    ) = Patient(
        id = "p",
        name = "Patient",
        animalCategory = category,
        wildlifeGroup = group,
        species = species,
        breed = breed,
        sex = PatientSex.UNKNOWN,
        neuterStatus = NeuterStatus.UNKNOWN,
        dateOfBirthEpochMs = null,
        ageValue = null,
        ageUnit = null,
        weightKg = null,
        weightUpdatedAtEpochMs = null,
        ownerName = null,
        ownerPhone = null,
        ownerNotes = null,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
    )
}
