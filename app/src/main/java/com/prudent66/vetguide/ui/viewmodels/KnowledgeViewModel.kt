package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface KnowledgeUiState {
    data object Loading : KnowledgeUiState

    data class Ready(
        val query: String,
        val species: Species?,
        val conditions: List<KbConditionSummary>,
        val error: String? = null,
    ) : KnowledgeUiState
}

/** Search + species filter state for the offline knowledge base. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val speciesFlow = MutableStateFlow<Species?>(null)

    val uiState: StateFlow<KnowledgeUiState> =
        combine(queryFlow, speciesFlow) { query, species -> query to species }
            .flatMapLatest { (query, species) ->
                knowledgeRepository.observeConditions(query = query, species = species)
                    .map<List<KbConditionSummary>, KnowledgeUiState> { conditions ->
                        KnowledgeUiState.Ready(query = query, species = species, conditions = conditions)
                    }
            }
            .catch { e ->
                emit(
                    KnowledgeUiState.Ready(
                        query = queryFlow.value,
                        species = speciesFlow.value,
                        conditions = emptyList(),
                        error = e.message ?: "Failed to load knowledge base",
                    ),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = KnowledgeUiState.Loading,
            )

    fun setQuery(query: String) {
        queryFlow.value = query
    }

    fun setSpecies(species: Species?) {
        speciesFlow.value = species
    }
}
