package com.prudent66.vetguide.ui.viewmodels

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.repository.KnowledgeRepository
import com.prudent66.vetguide.domain.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Live counts backing the Home dashboard cards. `null` means the first read
 * has not landed yet; a hard database failure degrades to zero instead of
 * leaving the dashboard stuck (guarded, never a silent crash or hang).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    patientRepository: PatientRepository,
    knowledgeRepository: KnowledgeRepository,
) : ViewModel() {

    val patientCount: StateFlow<Int?> = patientRepository.observePatients()
        .catch { throwable ->
            if (throwable is SQLiteException || throwable is IllegalStateException) {
                android.util.Log.e(TAG, "Patient count unavailable", throwable)
                emit(emptyList<Patient>())
            } else {
                throw throwable
            }
        }
        .map { patients -> patients.size }
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
    val recentPatient: StateFlow<Patient?> = patientRepository.observePatients()
        .catch { throwable ->
            if (throwable is SQLiteException || throwable is IllegalStateException) {
                android.util.Log.e(TAG, "Recent patient unavailable", throwable)
                emit(emptyList<Patient>())
            } else {
                throw throwable
            }
        }
        .map { patients -> patients.maxByOrNull { it.createdAtEpochMs } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
