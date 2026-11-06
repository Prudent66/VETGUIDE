package com.prudent66.vetguide.ui.screens.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.model.KbConditionDetail
import com.prudent66.vetguide.domain.model.KbUrgency
import com.prudent66.vetguide.ui.viewmodels.KbDetailUiState
import com.prudent66.vetguide.ui.viewmodels.KbDetailViewModel

/**
 * Condition detail: urgency flag, overview, symptoms, references.
 * Content is placeholder pending veterinary review — no clinical advice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDetailScreen(
    onBack: () -> Unit,
    viewModel: KbDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bodyTitleOf(state)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.nav_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                KbDetailUiState.Loading -> LoadingBox()
                KbDetailUiState.NotFound -> NotFoundBox()
                is KbDetailUiState.Error -> CenteredMessage(
                    s.message,
                    MaterialTheme.colorScheme.error,
                )
                is KbDetailUiState.Ready -> DetailContent(
                    detail = s.detail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun bodyTitleOf(state: KbDetailUiState): String = when (state) {
    is KbDetailUiState.Ready -> state.detail.name
    else -> "Condition"
}

@Composable
private fun DetailContent(detail: KbConditionDetail, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = speciesLine(detail),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = labelOf(detail.urgency),
            style = MaterialTheme.typography.labelLarge,
            color = when (detail.urgency) {
                KbUrgency.EMERGENCY -> MaterialTheme.colorScheme.error
                KbUrgency.URGENT -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        SectionCard(title = "Overview") {
            Text(detail.overview, style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = "Signs / symptoms") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (detail.symptoms.isEmpty()) {
                    Text(
                        "- None recorded in seed",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    detail.symptoms.forEach { symptom ->
                        Text(
                            text = "• " + symptom.name +
                                " (" + symptom.bodySystem + ")",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        SectionCard(title = "References") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.references.isEmpty()) {
                    Text(
                        "- Placeholder reference — requires veterinary review",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    detail.references.forEach { reference ->
                        Column {
                            Text(reference.title, style = MaterialTheme.typography.titleSmall)
                            if (reference.note.isNotBlank()) {
                                Text(
                                    reference.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "Placeholder content — requires veterinary review before clinical use.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun speciesLine(detail: KbConditionDetail): String = detail.species
    .joinToString(" · ") { speciesLabel(it) }
    .ifBlank { "Cross-species" }

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
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
private fun NotFoundBox() {
    CenteredMessage("Condition not found", MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CenteredMessage(message: String, color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
