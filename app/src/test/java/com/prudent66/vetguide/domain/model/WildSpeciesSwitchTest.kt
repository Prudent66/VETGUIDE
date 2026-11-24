package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Species lists used by Add Patient and Herd/Flock when switching pathways. */
class WildSpeciesSwitchTest {

    @Test
    fun `domestic path lists the eight supported species`() {
        val labels = ClinicalSpeciesCatalog.labels(AnimalCategory.DOMESTIC)

        assertEquals(8, labels.size)
        assertEquals(
            listOf("Cattle", "Goat", "Sheep", "Poultry", "Pig", "Equine", "Dog", "Cat"),
            labels,
        )
        assertEquals("Cattle", ClinicalSpeciesCatalog.first(AnimalCategory.DOMESTIC))
        assertEquals(Species.CATTLE, ClinicalSpeciesCatalog.domesticSpecies("Cattle"))
    }

    @Test
    fun `wild path lists the seven wildlife groups and auto-selects the first`() {
        val labels = ClinicalSpeciesCatalog.labels(AnimalCategory.WILD)

        assertEquals(7, labels.size)
        assertEquals("Wild Ruminants / Antelope", ClinicalSpeciesCatalog.first(AnimalCategory.WILD))
        assertTrue(WildlifeGroup.WILD_CANIDS.label in labels)
        assertTrue(WildlifeGroup.REPTILES.label in labels)
        assertEquals(
            WildlifeGroup.WILD_BIRDS,
            ClinicalSpeciesCatalog.wildlifeGroup(WildlifeGroup.WILD_BIRDS.label),
        )
        assertNull(ClinicalSpeciesCatalog.wildlifeGroup("Cattle"))
    }

    @Test
    fun `wild patient displays its group in lists and pickers`() {
        val patient = Patient(
            id = "p1",
            name = "Antelope",
            animalCategory = AnimalCategory.WILD,
            wildlifeGroup = WildlifeGroup.WILD_RUMINANTS.label,
            species = Species.OTHER,
            breed = null,
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

        assertEquals(WildlifeGroup.WILD_RUMINANTS.label, ClinicalSpeciesCatalog.display(patient))
    }
}
