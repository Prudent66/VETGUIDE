package com.prudent66.vetguide.ui.screens.diagnose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.SuspectedCondition
import kotlin.math.roundToInt

/**
 * Structured medical report for a fully decoded cloud analysis (§10): the
 * sectioned layout replaces the single-narrative dump. The provider/model
 * provenance stays in the banner above — the body carries findings,
 * differentials, and next steps only.
 */
@Composable
internal fun StructuredReport(data: AiAnalysisData) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FindingsSection(data.findings)
        DifferentialSection(data.suspectedConditions)
        NextStepsSection(data.recommendedNextSteps)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun FindingsSection(findings: List<String>) {
    if (findings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Visual Findings")
        findings.forEach { finding ->
            Text(
                text = "• $finding",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DifferentialSection(conditions: List<SuspectedCondition>) {
    if (conditions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Differential Diagnoses")
        conditions.forEach { condition ->
            ConditionCard(condition)
        }
    }
}

@Composable
private fun ConditionCard(condition: SuspectedCondition) {
    val percent = (condition.confidence * 100).roundToInt()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = condition.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { condition.confidence.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NextStepsSection(steps: List<String>) {
    if (steps.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Recommended Next Steps")
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
