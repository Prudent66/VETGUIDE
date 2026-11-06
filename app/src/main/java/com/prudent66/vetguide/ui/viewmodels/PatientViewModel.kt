package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientDraft
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PatientListUiState {
    data object Loading : PatientListUiState
    data class Success(val patients: List<Patient>) : PatientListUiState
    data class Error(val message: String) : PatientListUiState
}

/** List + creation state for patient management (roadmap Phase 1). */
@HiltViewModel
class PatientViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
) : ViewModel() {

    val uiState: StateFlow<PatientListUiState> = patientRepository.observePatients()
        .map<List<Patient>, PatientListUiState> { patients ->
            PatientListUiState.Success(patients = patients)
        }
        .catch { e ->
            emit(PatientListUiState.Error(message = e.message ?: "Failed to load patients"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PatientListUiState.Loading,
        )

    fun addPatient(draft: PatientDraft, onSaved: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val patient = Patient(
                id = UUID.randomUUID().toString(),
                name = draft.name,
                species = draft.species,
                breed = draft.breed,
                sex = draft.sex,
                neuterStatus = NeuterStatus.UNKNOWN,
                dateOfBirthEpochMs = null,
                estimatedAgeMonths = draft.ageMonths,
                weightKg = draft.weightKg,
                weightUpdatedAtEpochMs = draft.weightKg?.let { now },
                ownerName = null,
                ownerPhone = null,
                ownerNotes = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
            patientRepository.upsertPatient(patient)
            onSaved()
        }
    }
}
