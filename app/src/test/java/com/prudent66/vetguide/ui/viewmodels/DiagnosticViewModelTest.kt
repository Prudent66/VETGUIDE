package com.prudent66.vetguide.ui.viewmodels

import com.prudent66.vetguide.domain.model.DiagnosticCandidate
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientSex
import com.prudent66.vetguide.domain.model.QuestionnaireQuestion
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.domain.repository.QuestionnaireRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Start-phase species/patient linkage: patient selection must adopt its species. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel() = DiagnosticViewModel(
        questionnaireRepository = FakeQuestionnaireRepository(),
        patientRepository = FakePatientRepository(),
    )

    private fun patient(id: String, species: Species) = Patient(
        id = id,
        name = "Test $id",
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

    @Test
    fun `selecting a registered patient adopts its species by default`() {
        val viewModel = viewModel()
        val initial = viewModel.uiState.value as DiagnosticUiState.Start
        assertEquals(Species.DOG, initial.species)
        assertNull(initial.patientId)

        viewModel.selectPatient(patient("p1", Species.CAT))

        val updated = viewModel.uiState.value as DiagnosticUiState.Start
        assertEquals(Species.CAT, updated.species)
        assertEquals("p1", updated.patientId)
    }

    @Test
    fun `manual species override unlinks the patient`() {
        val viewModel = viewModel()
        viewModel.selectPatient(patient("p1", Species.CAT))

        viewModel.selectSpecies(Species.POULTRY)

        val updated = viewModel.uiState.value as DiagnosticUiState.Start
        assertEquals(Species.POULTRY, updated.species)
        assertNull(updated.patientId)
    }

    @Test
    fun `not recorded keeps the current species and clears the patient link`() {
        val viewModel = viewModel()
        viewModel.selectPatient(patient("p1", Species.CAT))

        viewModel.selectPatient(null)

        val updated = viewModel.uiState.value as DiagnosticUiState.Start
        assertEquals(Species.CAT, updated.species)
        assertNull(updated.patientId)
    }

    private class FakePatientRepository : PatientRepository {
        override fun observePatients(): Flow<List<Patient>> = flowOf(emptyList())
        override fun observePatient(id: String): Flow<Patient?> = flowOf(null)
        override suspend fun getPatient(id: String): Patient? = null
        override suspend fun upsertPatient(patient: Patient): String = patient.id
        override suspend fun deletePatient(id: String): Boolean = true
    }

    private class FakeQuestionnaireRepository : QuestionnaireRepository {
        override suspend fun loadQuestions(species: Species): List<QuestionnaireQuestion> = emptyList()
        override suspend fun loadCandidates(): List<DiagnosticCandidate> = emptyList()
    }
}
