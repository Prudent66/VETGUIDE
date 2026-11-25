package com.prudent66.vetguide.domain.model

import com.prudent66.vetguide.domain.ai.withoutDuplicateSuspectedConditions
import com.prudent66.vetguide.domain.ai.withoutNextSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 22: the selected animal is explicit in prompts and case labels. */
class PhotoAnalysisPromptTest {

    @Test
    fun `wild subject names the exact animal instead of Other`() {
        val subject = AnimalSubject.wild("Jackal / Fox / Wild Dog", "African Wild Dog")

        assertEquals("African Wild Dog · Jackal / Fox / Wild Dog", subject.label)
        assertFalse(subject.label.contains("Other"))
        assertFalse(subject.label.contains("DOG"))
        assertFalse(subject.label.contains("Cattle"))
        assertTrue(subject.constraint.contains("Wild pathway"))
        assertTrue(subject.constraint.contains("Jackal / Fox / Wild Dog"))
        assertTrue(subject.constraint.contains("never diagnose"))
    }

    @Test
    fun `domestic subject never carries a wildlife group or subtype`() {
        val subject = AnimalSubject.domestic(Species.GOAT)

        assertEquals("Goat", subject.label)
        assertTrue(subject.wildlifeGroup == null)
        assertTrue(subject.wildAnimal == null)
        assertTrue(subject.constraint.contains("Goat"))
        assertFalse(subject.constraint.contains("Cattle"))
        assertFalse(subject.constraint.contains("Wild pathway"))
    }

    @Test
    fun `patient to subject keeps domestic and wild paths isolated`() {
        val domestic = patient("domestic", Species.GOAT, null, null, null)
        val wild = patient(
            "wild", Species.OTHER, AnimalCategory.WILD,
            "Jackal / Fox / Wild Dog", "African Wild Dog",
        )

        assertEquals(Species.GOAT, AnimalSubject.fromPatient(domestic).species)
        assertEquals(AnimalCategory.DOMESTIC, AnimalSubject.fromPatient(domestic).category)
        assertEquals(null, AnimalSubject.fromPatient(wild).species)
        assertEquals("African Wild Dog · Jackal / Fox / Wild Dog",
            AnimalSubject.fromPatient(wild).label)
        assertEquals(
            "African Wild Dog · Jackal / Fox / Wild Dog",
            ClinicalSpeciesCatalog.reportLabel(wild),
        )
    }

    @Test
    fun `duplicate next steps clause is removed while findings stay`() {
        val narrative =
            "Findings: swollen joints. Suspected conditions: Caprine arthritis. " +
                "Recommended next steps: Isolate the goat; provide clean water. " +
                "Confidence values are estimates."
        val summary = withoutNextSteps(narrative)

        assertTrue(summary.contains("swollen joints"))
        assertTrue(summary.contains("Caprine arthritis"))
        assertTrue(summary.contains("Confidence values"))
        assertFalse(summary.contains("Isolate the goat"))
        assertFalse(summary.contains("next steps", ignoreCase = true))
    }

    @Test
    fun `repeated suspected conditions block is reduced to one`() {
        val clause = "Suspected conditions (decision support, not a diagnosis): " +
            "Infected abscess (confidence 80%); Parasitic skin disease (confidence 60%)."
        val narrative = "Findings: skin lesion. $clause $clause Requires veterinary review."

        val summary = withoutDuplicateSuspectedConditions(narrative)

        assertEquals(
            1,
            summary.split("Suspected conditions (decision support, not a diagnosis):").size - 1,
        )
        assertTrue(summary.contains("Infected abscess"))
        assertTrue(summary.contains("Parasitic skin disease"))
        assertTrue(summary.contains("Requires veterinary review"))
    }

    @Test
    fun `single suspected conditions block is left alone`() {
        val narrative = "Findings: lesion. Suspected conditions (decision support, not a diagnosis): Mange (confidence 70%)."
        assertEquals(narrative, withoutDuplicateSuspectedConditions(narrative))
    }

    @Test
    fun `top suspected condition is read from an offline narrative`() {
        val narrative = "Findings: skin lesion. Suspected conditions " +
            "(decision support, not a diagnosis): Mange (confidence 70%); " +
            "Infected abscess (confidence 50%). Requires veterinary review."
        assertEquals("Mange", firstNarrativeCondition(narrative))
    }

    @Test
    fun `narrative without a next steps clause is unchanged`() {
        val narrative = "Findings: mild swelling. Requires veterinary review."
        assertEquals(narrative, withoutNextSteps(narrative))
    }

    private fun patient(
        id: String,
        species: Species,
        category: AnimalCategory?,
        group: String?,
        breed: String?,
    ) = Patient(
        id = id,
        name = id,
        animalCategory = category ?: AnimalCategory.DOMESTIC,
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
