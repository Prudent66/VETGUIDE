package com.prudent66.vetguide.ui.viewmodels

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.repository.CaseRepository
import com.prudent66.vetguide.domain.repository.KnowledgeRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Latest case surfaced on the Home dashboard, with its patient name. */
data class RecentCaseSummary(
    val caseId: String,
    val title: String,
    val patientName: String,
    val status: CaseStatus,
)

/**
 * Live counts and recent records backing the Home dashboard cards. `null`
 * means the first read has not landed yet; a hard database failure degrades
 * to empty instead of leaving the dashboard stuck (guarded, never a silent
 * crash or hang).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    patientRepository: PatientRepository,
    private val caseRepository: CaseRepository,
    knowledgeRepository: KnowledgeRepository,
) : ViewModel() {

    private val patients = patientRepository.observePatients()
        .catch { throwable ->
            if (throwable is SQLiteException || throwable is IllegalStateException) {
                android.util.Log.e(TAG, "Patient data unavailable", throwable)
                emit(emptyList<Patient>())
            } else {
                throw throwable
            }
        }

    val patientCount: StateFlow<Int?> = patients
        .map { list -> list.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val conditionCount: StateFlow<Int?> = knowledgeRepository
        .observeConditions(query = "", species = null)
        .catch { throwable ->
            if (throwable is SQLiteException || throwable is IllegalStateException) {
                android.util.Log.e(TAG, "Knowledge Base count unavailable", throwable)
                emit(emptyList<KbConditionSummary>())
            } else {
                throw throwable
            }
        }
        .map { conditions -> conditions.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Most recently registered patient for the Home dashboard. */
    val recentPatient: StateFlow<Patient?> = patients
        .map { list -> list.maxByOrNull { it.createdAtEpochMs } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Most recently updated case (any patient) for the Home dashboard. */
    val recentCase: StateFlow<RecentCaseSummary?> = combine(
        patients,
        caseRepository.observeCases().catch { throwable ->
            if (throwable is SQLiteException || throwable is IllegalStateException) {
                android.util.Log.e(TAG, "Recent cases unavailable", throwable)
                emit(emptyList<Case>())
            } else {
                throw throwable
            }
        },
    ) { patientList, cases ->
        cases.maxByOrNull { it.updatedAtEpochMs }?.let { latest ->
            RecentCaseSummary(
                caseId = latest.id,
                title = latest.presentingComplaint,
                patientName = patientList.firstOrNull { it.id == latest.patientId }?.name
                    ?: "Unknown patient",
                status = latest.status,
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
