package com.prudent66.vetguide.ui.viewmodels

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.NeuterStatus
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.model.PatientDraft
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * List + create/edit/delete state for patient management (roadmap Phase 1).
 * The form route supplies [Destinations.ARG_PATIENT_ID] when editing; the
 * list route has no argument and only uses [uiState].
 */
@HiltViewModel
class PatientViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val editPatientId: String? = savedStateHandle[Destinations.ARG_PATIENT_ID]

    /** True when the form was opened to edit an existing patient. */
    val isEditing: Boolean = editPatientId != null

    /** Patient being edited; null while loading or in add mode. */
    val editingPatient: StateFlow<Patient?> =
        if (editPatientId == null) {
            MutableStateFlow<Patient?>(null)
        } else {
            patientRepository.observePatient(editPatientId)
                .catch { throwable ->
                    Log.e(TAG, "Patient being edited unavailable", throwable)
                    emit(null)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = null,
                )
        }

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

    /**
     * Creates a patient, or updates the routed patient while preserving its
     * id, creation time, and owner/neutering details not exposed by the form.
     */
    fun savePatient(draft: PatientDraft, onSaved: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = editPatientId?.let { patientRepository.getPatient(it) }
            val patient = existing?.copy(
                name = draft.name,
                animalCategory = draft.animalCategory,
                species = draft.species,
                breed = draft.breed,
                sex = draft.sex,
                ageValue = draft.ageValue,
                ageUnit = draft.ageUnit,
                weightKg = draft.weightKg,
                weightUpdatedAtEpochMs = if (draft.weightKg != existing.weightKg) {
                    now
                } else {
                    existing.weightUpdatedAtEpochMs
                },
                updatedAtEpochMs = now,
            ) ?: Patient(
                id = UUID.randomUUID().toString(),
                name = draft.name,
                animalCategory = draft.animalCategory,
                species = draft.species,
                breed = draft.breed,
                sex = draft.sex,
                neuterStatus = NeuterStatus.UNKNOWN,
                dateOfBirthEpochMs = null,
                ageValue = draft.ageValue,
                ageUnit = draft.ageUnit,
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

    /** Deletes the patient; Room's FK cascade removes the linked cases too. */
    fun deletePatient(patientId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                patientRepository.deletePatient(patientId)
                onDeleted()
            } catch (e: SQLiteException) {
                Log.e(TAG, "Could not delete patient $patientId", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Could not delete patient $patientId", e)
            }
        }
    }

    private companion object {
        const val TAG = "PatientViewModel"
    }
}
