package com.prudent66.vetguide.ui.screens.diagnose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.ui.viewmodels.DiagnosticUiState
import com.prudent66.vetguide.ui.viewmodels.DiagnosticViewModel

/** Step-by-step wizard: one placeholder question per screen, progressive disclosure. */
@Composable
fun WizardPhase(
    wizard: DiagnosticUiState.Wizard,
    viewModel: DiagnosticViewModel,
) {
    BackHandler { viewModel.previousStep() }

    val question = wizard.questions[wizard.index]
    val selectedOptions = wizard.selections[question.id].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressHeader(wizard)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(question.text, style = MaterialTheme.typography.titleMedium)
                question.options.forEach { option ->
                    OptionRow(
                        label = option.label,
                        checked = option.id in selectedOptions,
                        onToggle = { viewModel.toggleOption(option.id) },
                    )
                }
            }
        }
        WizardButtons(
            canGoBack = wizard.index > 0,
            canProceed = selectedOptions.isNotEmpty(),
            isLast = wizard.index == wizard.questions.lastIndex,
            viewModel = viewModel,
        )
        Text(
            "Placeholder questionnaire — requires veterinary review.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressHeader(wizard: DiagnosticUiState.Wizard) {
    val fraction = (wizard.index + 1).toFloat() / wizard.questions.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Question ${wizard.index + 1} of ${wizard.questions.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WizardButtons(
    canGoBack: Boolean,
    canProceed: Boolean,
    isLast: Boolean,
    viewModel: DiagnosticViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = viewModel::previousStep,
            enabled = canGoBack,
            modifier = Modifier.weight(1f),
        ) { Text("Back") }
        Button(
            onClick = viewModel::nextStep,
            enabled = canProceed,
            modifier = Modifier.weight(1f),
        ) { Text(if (isLast) "See results" else "Next") }
    }
}
