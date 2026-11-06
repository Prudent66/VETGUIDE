package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import com.prudent66.vetguide.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CaseListUiState {
    data object Loading : CaseListUiState
    data class Success(
        val patientName: String,
        val cases: List<Case>,
    ) : CaseListUiState

    data class Error(val message: String) : CaseListUiState
}

/** Timeline of cases for one patient (drill-in from the Patients tab). */
@HiltViewModel
class CaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val caseRepository: CaseRepository,
    patientRepository: PatientRepository,
) : ViewModel() {

    private val patientId: String = checkNotNull(savedStateHandle[Destinations.ARG_PATIENT_ID])

    val uiState: StateFlow<CaseListUiState> = combine(
        patientRepository.observePatient(patientId),
        caseRepository.observeCasesForPatient(patientId),
    ) { patient, cases ->
        CaseListUiState.Success(
            patientName = patient?.name ?: "Unknown patient",
            cases = cases,
        )
    }
        .catch { e ->
            emit(CaseListUiState.Error(message = e.message ?: "Failed to load cases"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaseListUiState.Loading,
        )

    /** Temporary Phase 1B helper: creates a demo case to verify the patient→case relation. */
    fun createCase() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            caseRepository.upsertCase(
                Case(
                    id = UUID.randomUUID().toString(),
                    patientId = patientId,
                    presentingComplaint = "Demo case — created to test the patient→case relation",
                    status = CaseStatus.OPEN,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
    }
}
