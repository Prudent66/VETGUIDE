package com.prudent66.vetguide.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.KnowledgeFilters
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
        val category: AnimalCategory = AnimalCategory.DOMESTIC,
        val speciesTag: String? = null,
        val conditions: List<KbConditionSummary>,
        val error: String? = null,
    ) : KnowledgeUiState
}

/** Search + strict Domestic/Wild species filtering for the offline knowledge base. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val categoryFlow = MutableStateFlow(AnimalCategory.DOMESTIC)
    private val tagFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KnowledgeUiState> =
        combine(queryFlow, categoryFlow, tagFlow) { query, category, tag ->
            Triple(query, category, tag)
        }
            .flatMapLatest { (query, category, tag) ->
                knowledgeRepository.observeConditions(query = query, species = null)
                    .map { rows ->
                        rows.filter { KnowledgeFilters.matches(it, category, tag) }
                    }
                    .map<List<KbConditionSummary>, KnowledgeUiState> { conditions ->
                        KnowledgeUiState.Ready(
                            query = query,
                            category = category,
                            speciesTag = tag,
                            conditions = conditions,
                        )
                    }
            }
            .catch { e ->
                emit(
                    KnowledgeUiState.Ready(
                        query = queryFlow.value,
                        category = categoryFlow.value,
                        speciesTag = tagFlow.value,
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

    fun setCategory(category: AnimalCategory) {
        categoryFlow.value = category
        tagFlow.value = null
    }

    fun setSpeciesTag(tag: String?) {
        tagFlow.value = tag
    }
}
