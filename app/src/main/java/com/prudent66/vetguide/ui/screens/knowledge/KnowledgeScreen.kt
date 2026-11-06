package com.prudent66.vetguide.ui.screens.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.ui.viewmodels.KnowledgeUiState
import com.prudent66.vetguide.ui.viewmodels.KnowledgeViewModel

/**
 * Offline Knowledge Base (Phase 2): search bar + species filter + condition
 * cards → detail view. All content is placeholder pending veterinary review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onConditionClick: (String) -> Unit,
    viewModel: KnowledgeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Knowledge Base") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SearchBar(
                query = queryOf(state),
                onQueryChange = viewModel::setQuery,
            )
            SpeciesFilter(
                selected = speciesOf(state),
                onSelected = viewModel::setSpecies,
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
            ) {
                when (val s = state) {
                    KnowledgeUiState.Loading -> LoadingBox()
                    is KnowledgeUiState.Ready -> KnowledgeList(
                        conditions = s.conditions,
                        error = s.error,
                        onConditionClick = onConditionClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun queryOf(state: KnowledgeUiState): String = when (state) {
    is KnowledgeUiState.Ready -> state.query
    KnowledgeUiState.Loading -> ""
}

private fun speciesOf(state: KnowledgeUiState): Species? = when (state) {
    is KnowledgeUiState.Ready -> state.species
    KnowledgeUiState.Loading -> null
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search conditions") },
        singleLine = true,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    )
}

/** "All species" + full Nigerian veterinary scope. */
@Composable
private fun SpeciesFilter(
    selected: Species?,
    onSelected: (Species?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(16.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                text = "Species: " + speciesLabel(selected),
            )
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All species") }, onClick = {
                onSelected(null)
                expanded = false
            })
            Species.entries.forEach { species ->
                DropdownMenuItem(
                    text = { Text(speciesLabel(species)) },
                    onClick = {
                        onSelected(species)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun KnowledgeList(
    conditions: List<KbConditionSummary>,
    error: String?,
    onConditionClick: (String) -> Unit,
    modifier: Modifier,
) {
    when {
        error != null -> ErrorBox(error, modifier)
        conditions.isEmpty() -> EmptyState(modifier)
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(conditions, key = { it.id }) { condition ->
                ConditionCard(condition, onConditionClick)
            }
        }
    }
}

@Composable
private fun ConditionCard(
    condition: KbConditionSummary,
    onConditionClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConditionClick(condition.id) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(condition.name, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = condition.species.joinToString(" · ") { speciesLabel(it) }
                        .ifBlank { "Cross-species" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = labelOf(condition.urgency),
                style = MaterialTheme.typography.labelMedium,
                color = urgencyColor(condition.urgency),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.nav_knowledge),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("No matching conditions", style = MaterialTheme.typography.titleLarge)
            Text(
                "Try a different search term or species filter",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
