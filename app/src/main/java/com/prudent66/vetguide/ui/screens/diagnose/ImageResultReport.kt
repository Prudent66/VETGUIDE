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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.SuspectedCondition
import com.prudent66.vetguide.domain.ai.buildPrescriptionPlan
import com.prudent66.vetguide.ui.components.PrescriptionCard
import kotlin.math.roundToInt

/**
 * Structured medical report for a fully decoded cloud analysis (§10): the
 * sectioned layout replaces the single-narrative dump. The provider/model
 * provenance stays in the banner above — the body carries findings,
 * differentials, prescriptions, and non-drug supportive care.
 */
@Composable
internal fun StructuredReport(data: AiAnalysisData) {
    val plan = remember(data) { buildPrescriptionPlan(data) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FindingsSection(data.findings)
        DifferentialSection(data.suspectedConditions)
        PrescriptionCard(plan.prescriptions, Modifier.fillMaxWidth())
        SupportiveCareSection(plan.supportiveCare)
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
        SectionHeader("What I See (Observations)")
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
        SectionHeader("Possible Conditions")
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
private fun SupportiveCareSection(steps: List<String>) {
    if (steps.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Supportive Field Care & Follow-up")
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
