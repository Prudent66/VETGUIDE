package com.prudent66.vetguide.ui.viewmodels

import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.SuspectedCondition
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.AnimalSubject
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseArtifactType
import com.prudent66.vetguide.domain.model.CasePayloadCodec
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.SoapPayload
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Build 15: Save-to-Case lists Room patients and preselects patient and species. */
class SaveToCaseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val patients = listOf(
        patient("p1", "Chicken", Species.POULTRY),
        patient("p2", "Bruno", Species.DOG),
    )

    private fun viewModel() = SaveToCaseViewModel(
        caseRepository = FakeCaseRepository(),
        patientRepository = FakePatientRepository(patients),
    )

    @Test
    fun `image analysis lists all registered patients and preselects the chosen patient`() {
        val viewModel = viewModel()

        viewModel.openForImage(
            ImageAnalysisUiState.Result(
                species = Species.POULTRY,
                patientId = "p1",
                providerId = "test",
                narrative = "findings",
                degraded = false,
            ),
        )

        val visible = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals(2, visible.patients.size)
        assertEquals("Chicken", visible.patients.first().name)
        assertEquals("p1", visible.selectedPatientId)
        assertEquals(Species.POULTRY, visible.quickAddSpecies)
    }

    @Test
    fun `image analysis with no patient still defaults new-patient species to the analyzed species`() {
        val viewModel = viewModel()

        viewModel.openForImage(
            ImageAnalysisUiState.Result(
                species = Species.POULTRY,
                providerId = "test",
                narrative = "findings",
                degraded = false,
            ),
        )

        val visible = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals(2, visible.patients.size)
        assertEquals(null, visible.selectedPatientId)
        assertEquals(Species.POULTRY, visible.quickAddSpecies)
    }

    @Test
    fun `questionnaire inherits the selected patient and species`() {
        val viewModel = viewModel()

        viewModel.openForQuestionnaire(
            DiagnosticUiState.Results(
                matches = emptyList(),
                species = Species.CAT,
                patientId = "p2",
            ),
        )

        val visible = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals(2, visible.patients.size)
        assertEquals("p2", visible.selectedPatientId)
        assertEquals(Species.CAT, visible.quickAddSpecies)
    }

    @Test
    fun `typed new patient name creates a patient instead of reusing the preselected one`() {
        val patientRepo = FakePatientRepository(patients)
        val caseRepo = FakeCaseRepository()
        val viewModel = SaveToCaseViewModel(caseRepository = caseRepo, patientRepository = patientRepo)
        viewModel.openForImage(
            ImageAnalysisUiState.Result(
                species = Species.POULTRY,
                patientId = "p1",
                providerId = "test",
                narrative = "findings",
                degraded = false,
            ),
        )

        viewModel.setQuickAddName("  New chick  ")
        viewModel.save()

        val created = patientRepo.upserted.single()
        assertEquals("New chick", created.name)
        assertEquals(AnimalCategory.DOMESTIC, created.animalCategory)
        assertEquals(Species.POULTRY, created.species)
        assertEquals(created.id, caseRepo.savedCases.single().patientId)
    }

    @Test
    fun `new wild patient keeps the active category and wildlife group`() {
        val patientRepo = FakePatientRepository(patients)
        val caseRepo = FakeCaseRepository()
        val viewModel = SaveToCaseViewModel(caseRepository = caseRepo, patientRepository = patientRepo)
        viewModel.openForQuestionnaire(
            DiagnosticUiState.Results(matches = emptyList(), species = Species.OTHER),
        )

        viewModel.setQuickAddCategory(AnimalCategory.WILD, "Lion / Leopard / Cheetah")
        viewModel.setQuickAddName("Lion cub")
        viewModel.save()

        val created = patientRepo.upserted.single()
        assertEquals("Lion cub", created.name)
        assertEquals(AnimalCategory.WILD, created.animalCategory)
        assertEquals("Lion / Leopard / Cheetah", created.wildlifeGroup)
        assertEquals(Species.OTHER, created.species)
        assertEquals(created.id, caseRepo.savedCases.single().patientId)
    }

    @Test
    fun `new patient name and existing patient radio are mutually exclusive`() {
        val viewModel = viewModel()
        viewModel.openForImage(
            ImageAnalysisUiState.Result(
                species = Species.POULTRY,
                patientId = "p1",
                providerId = "test",
                narrative = "findings",
                degraded = false,
            ),
        )
        val selected = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals("p1", selected.selectedPatientId)

        // Typing a new name clears the existing-patient radio.
        viewModel.setQuickAddName("New chick")
        val typed = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals("New chick", typed.quickAddName)
        assertEquals(null, typed.selectedPatientId)

        // Tapping an existing patient clears the typed name.
        viewModel.selectPatient("p2")
        val picked = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals("p2", picked.selectedPatientId)
        assertEquals("", picked.quickAddName)

        // Blank name leaves a valid existing selection untouched.
        viewModel.setQuickAddName("")
        val blank = viewModel.uiState.value as SaveToCaseUiState.Visible
        assertEquals("p2", blank.selectedPatientId)
    }

    @Test
    fun `saving an assessment pre-fills objective assessment and plan SOAP fields`() {
        val caseRepo = FakeCaseRepository()
        val viewModel = SaveToCaseViewModel(
            caseRepository = caseRepo,
            patientRepository = FakePatientRepository(patients),
        )
        val analysis = AiAnalysisData(
            provider = "test-provider",
            model = "test-model",
            findings = listOf("Lesion on flank"),
            suspectedConditions = listOf(
                SuspectedCondition("Secondary wound infection", 0.8),
            ),
            recommendedNextSteps = listOf("Isolate the affected animal", "Provide clean water"),
            prescriptions = listOf("Oxytetracycline 20% 15 mg/kg IM once daily"),
        )
        viewModel.openForImage(
            ImageAnalysisUiState.Result(
                species = Species.CATTLE,
                patientId = "p1",
                providerId = "test",
                narrative = "Findings: lesion on flank.",
                degraded = false,
                analysisData = analysis,
            ),
        )

        viewModel.save()

        val soap = caseRepo.savedArtifacts
            .first { it.type == CaseArtifactType.SOAP }
            .let { CasePayloadCodec.decodeOrNull<SoapPayload>(it.content)!! }
        assertTrue(soap.objective.contains("Lesion on flank"))
        assertTrue(soap.assessment.contains("Secondary wound infection"))
        assertTrue(soap.assessment.contains("80%"))
        assertTrue(soap.plan.contains("Oxytetracycline"))
        assertTrue(soap.plan.contains("Isolate the affected animal"))
    }

    @Test
    fun `saving a wild photo keeps the exact wild animal in the case title`() {
        val caseRepo = FakeCaseRepository()
        val viewModel = SaveToCaseViewModel(
            caseRepository = caseRepo,
            patientRepository = FakePatientRepository(patients),
        )
        viewModel.openForImage(
            ImageAnalysisUiState.Result(
                species = Species.OTHER,
                subject = AnimalSubject.wild(
                    "Jackal / Fox / Wild Dog", "African Wild Dog",
                ),
                patientId = "p1",
                providerId = "test",
                narrative = "findings",
                degraded = false,
            ),
        )

        viewModel.save()

        assertEquals(
            "Photo analysis — African Wild Dog · Jackal / Fox / Wild Dog",
            caseRepo.savedCases.single().presentingComplaint,
        )
    }

    private fun patient(id: String, name: String, species: Species) = Patient(
        id = id,
        name = name,
        species = species,
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

    private class FakePatientRepository(
        private val all: List<Patient>,
    ) : PatientRepository {
        val upserted = mutableListOf<Patient>()

        override fun observePatients(): Flow<List<Patient>> = flowOf(all)
        override fun observePatient(id: String): Flow<Patient?> =
            flowOf(all.firstOrNull { it.id == id })
        override suspend fun getPatient(id: String): Patient? = all.firstOrNull { it.id == id }
        override suspend fun upsertPatient(patient: Patient): String {
            upserted += patient
            return patient.id
        }
        override suspend fun deletePatient(id: String): Boolean = true
    }

    private class FakeCaseRepository : CaseRepository {
        val savedCases = mutableListOf<Case>()
        val savedArtifacts = mutableListOf<CaseArtifact>()

        override fun observeCases(): Flow<List<Case>> = flowOf(emptyList())
        override fun observeCasesForPatient(patientId: String): Flow<List<Case>> = flowOf(emptyList())
        override fun observeCase(id: String): Flow<Case?> = flowOf(null)
        override suspend fun getCase(id: String): Case? = null
        override suspend fun upsertCase(case: Case): String {
            savedCases += case
            return case.id
        }
        override suspend fun setCaseStatus(id: String, status: CaseStatus) = Unit
        override suspend fun deleteCase(id: String) = Unit
        override fun observeArtifactsForCase(caseId: String): Flow<List<CaseArtifact>> = flowOf(emptyList())
        override suspend fun upsertArtifact(artifact: CaseArtifact): String {
            savedArtifacts += artifact
            return artifact.id
        }
        override suspend fun deleteArtifact(id: String) = Unit
    }
}
