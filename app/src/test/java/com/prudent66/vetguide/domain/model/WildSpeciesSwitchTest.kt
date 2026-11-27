package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Species lists used by Add Patient and Herd/Flock when switching pathways. */
class WildSpeciesSwitchTest {

    @Test
    fun `domestic path lists global species and the legacy other option`() {
        val labels = ClinicalSpeciesCatalog.labels(AnimalCategory.DOMESTIC)

        assertEquals(13, labels.size)
        assertEquals(
            listOf(
                "Cattle", "Goat", "Sheep", "Poultry", "Pig", "Equine", "Dog", "Cat",
                "Buffalo", "Camel", "Rabbit", "Fish", "Other Domestic",
            ),
            labels,
        )
        assertEquals("Cattle", ClinicalSpeciesCatalog.first(AnimalCategory.DOMESTIC))
        assertEquals(Species.CATTLE, ClinicalSpeciesCatalog.domesticSpecies("Cattle"))
    }

    @Test
    fun `wild path lists the seven wildlife groups and auto-selects the first`() {
        val labels = ClinicalSpeciesCatalog.labels(AnimalCategory.WILD)

        assertEquals(7, labels.size)
        assertEquals("Antelope / Gazelle / Buffalo", ClinicalSpeciesCatalog.first(AnimalCategory.WILD))
        assertTrue(WildlifeGroup.WILD_CANIDS.label in labels)
        assertTrue(WildlifeGroup.REPTILES.label in labels)
        assertEquals(
            WildlifeGroup.WILD_BIRDS,
            ClinicalSpeciesCatalog.wildlifeGroup(WildlifeGroup.WILD_BIRDS.label),
        )
        assertNull(ClinicalSpeciesCatalog.wildlifeGroup("Cattle"))
    }

    @Test
    fun `wildlife groups resolve enum names current and previous labels`() {
        listOf(
            "WILD_RUMINANTS",
            "Wild Ruminants / Antelope",
            "Antelope / Gazelle / Buffalo",
        ).forEach { label ->
            assertEquals(WildlifeGroup.WILD_RUMINANTS, ClinicalSpeciesCatalog.wildlifeGroup(label))
        }
        assertEquals(
            WildlifeGroup.SMALL_WILD_MAMMALS,
            ClinicalSpeciesCatalog.wildlifeGroup("Pangolin / Grasscutter / Bat"),
        )
    }

    @Test
    fun `every wildlife group offers concrete wild animals and no local unknown`() {
        WildlifeGroup.entries.forEach { group ->
            val animals = ClinicalSpeciesCatalog.wildAnimalsFor(group)
            assertTrue("$group has no wild animals", animals.size > 1)
            assertEquals("Other / Unidentified", animals.last())
            assertTrue("$group leaked Local / Unknown", "Local / Unknown" !in animals)
        }
        assertEquals(
            listOf(
                "Duiker", "Gazelle", "Impala", "Bushbuck", "Kob", "Kudu", "Roan Antelope",
                "African Buffalo", "Giraffe", "Other / Unidentified",
            ),
            ClinicalSpeciesCatalog.wildAnimalsFor(WildlifeGroup.WILD_RUMINANTS),
        )
    }

    @Test
    fun `assessment context follows a wild patient group instead of cattle`() {
        val jackal = Patient(
            id = "p2",
            name = "Jackal",
            animalCategory = AnimalCategory.WILD,
            wildlifeGroup = WildlifeGroup.WILD_CANIDS.label,
            species = Species.OTHER,
            breed = "Side-Striped Jackal",
            sex = PatientSex.UNKNOWN,
            neuterStatus = NeuterStatus.UNKNOWN,
            dateOfBirthEpochMs = null,
            ageValue = null,
            ageUnit = null,
            weightKg = 15.0,
            weightUpdatedAtEpochMs = null,
            ownerName = null,
            ownerPhone = null,
            ownerNotes = null,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        val (category, group) = ClinicalSpeciesCatalog.assessmentContext(jackal)
        assertEquals(AnimalCategory.WILD, category)
        assertEquals("Jackal / Fox / Wild Dog", group)
        assertEquals(group, AnimalSubject.fromPatient(jackal).groupLabel)
        assertEquals("Side-Striped Jackal", AnimalSubject.fromPatient(jackal).wildAnimal)

        val (domestic, _) = ClinicalSpeciesCatalog.assessmentContext(null)
        assertEquals(AnimalCategory.DOMESTIC, domestic)
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

        val legacy = patient.copy(wildlifeGroup = "Wild Ruminants / Antelope")
        assertEquals(WildlifeGroup.WILD_RUMINANTS.label, ClinicalSpeciesCatalog.display(legacy))
    }
}
