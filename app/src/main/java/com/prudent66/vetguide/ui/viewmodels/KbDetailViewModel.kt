package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.KbConditionDetail
import com.prudent66.vetguide.domain.repository.KnowledgeRepository
import com.prudent66.vetguide.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface KbDetailUiState {
    data object Loading : KbDetailUiState
    data class Ready(val detail: KbConditionDetail) : KbDetailUiState
    data object NotFound : KbDetailUiState
    data class Error(val message: String) : KbDetailUiState
}

/** Loads one KB condition with its symptoms and references. */
@HiltViewModel
class KbDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    knowledgeRepository: KnowledgeRepository,
) : ViewModel() {

    private val conditionId: String =
        checkNotNull(savedStateHandle[Destinations.ARG_CONDITION_ID])

    val uiState: StateFlow<KbDetailUiState> =
        knowledgeRepository.observeConditionDetail(conditionId)
            .map<KbConditionDetail?, KbDetailUiState> { detail ->
                if (detail == null) KbDetailUiState.NotFound else KbDetailUiState.Ready(detail)
            }
            .catch { e ->
                emit(KbDetailUiState.Error(e.message ?: "Failed to load condition"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = KbDetailUiState.Loading,
            )
}
