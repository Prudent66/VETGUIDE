package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.formulary.DoseRoute
import java.util.Locale

/** Shared pieces of the prescription sheet, split out to keep file size low. */

@Composable
internal fun AnalyzedDrugNote() {
    Text(ANALYZED_DRUG_NOTE, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun WeightField(value: String, onChange: (String) -> Unit) {
    NumericTextField(
        value = value,
        onValueChange = onChange,
        label = "Weight (kg)",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SelectionDialogField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().clickable { open = true }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        TextButton(
                            onClick = { onSelect(option); open = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** Route picker; the displayed value follows the selected card until changed here. */
@Composable
internal fun RouteField(route: DoseRoute, onSelect: (DoseRoute) -> Unit) {
    SelectionDialogField(
        label = "Route",
        value = route.label,
        options = DoseRoute.entries.map { it.label },
    ) { label -> DoseRoute.entries.firstOrNull { it.label == label }?.let(onSelect) }
}

@Composable
internal fun SafetyAlertCard(alerts: List<String>) {
    if (alerts.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Species Safety / Contraindication Alert",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            alerts.forEach { alert ->
                Text("• $alert", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
internal fun ResultLine(label: String, value: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

internal fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }

/** Semisolids are applied as a layer, not drawn up in a syringe. */
internal fun isSemisolidTopical(route: DoseRoute, drugName: String): Boolean =
    route == DoseRoute.TOPICAL &&
        listOf("ointment", "cream", "paste", "gel", "salve", "powder")
            .any { it in drugName.lowercase() }

/** Non-syringe value for semisolid topicals; null keeps the mL line for liquids. */
internal fun topicalApplicationText(
    route: DoseRoute,
    drugName: String,
    ocular: Boolean = false,
): String? = if (isSemisolidTopical(route, drugName)) {
    if (ocular) {
        "Thin layer on the affected eye as directed — do not draw into a syringe"
    } else {
        "Thin layer on cleaned skin or wound — apply enough to cover the treated area; " +
            "do not draw into a syringe"
    }
} else {
    null
}

/** One dose-note phrase shared by the domestic and wildlife result panels. */
internal fun administrationPhrase(
    route: DoseRoute,
    amountMl: Double,
    concentration: Double,
    semisolid: Boolean,
    ocular: Boolean = false,
): String = if (semisolid) {
    if (ocular) {
        "as an estimated thin layer on the affected eye; do not draw into a syringe"
    } else {
        "as an estimated thin layer on cleaned skin or wound; do not draw into a syringe"
    }
} else {
    "gives ${formatAmount(amountMl)} mL from a ${formatAmount(concentration)} mg/mL bottle " +
        "by ${route.label}"
}
