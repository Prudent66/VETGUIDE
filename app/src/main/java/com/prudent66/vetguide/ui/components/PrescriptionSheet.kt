package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.formulary.DosageResult
import com.prudent66.vetguide.domain.formulary.calculateDosage
import com.prudent66.vetguide.domain.formulary.matchDrugFromText
import com.prudent66.vetguide.domain.formulary.findDrug
import com.prudent66.vetguide.domain.formulary.findDrugByName
import com.prudent66.vetguide.domain.formulary.formulary
import com.prudent66.vetguide.domain.model.Species
import java.util.Locale

/**
 * Dedicated prescription surface shared by the analysis results and the case
 * record. Drug/injection items with dosages live here, apart from the
 * non-pharmacological supportive-care steps rendered by the host screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionCard(
    prescriptions: List<String>,
    species: Species? = null,
    weightKg: Double? = null,
    modifier: Modifier = Modifier,
) {
    if (prescriptions.isEmpty()) return
    var showSheet by rememberSaveable { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Prescriptions & Dosages", style = MaterialTheme.typography.titleMedium)
            Text(
                "Drugs, injections and dosages are kept separate from supportive field care. " +
                    "Verify every dose against the animal's species, weight and condition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Prescriptions & Dosages (${prescriptions.size})")
            }
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            PrescriptionSheetBody(prescriptions, species, weightKg)
        }
    }
}

@Composable
private fun PrescriptionSheetBody(
    prescriptions: List<String>,
    species: Species?,
    weightKg: Double?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Prescriptions & Dosages", style = MaterialTheme.typography.titleLarge)
        Text(
            "Decision support only — requires veterinary review. Confirm drug, dose, " +
                "route, frequency and duration for this species, weight and condition.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        prescriptions.forEachIndexed { index, item ->
            Text("${index + 1}. $item", style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        DosageCalculatorSection(prescriptions, species, weightKg)
    }
}

@Composable
private fun DosageCalculatorSection(
    prescriptions: List<String>,
    species: Species?,
    weightKg: Double?,
) {
    val matched = remember(prescriptions) {
        matchDrugFromText(prescriptions.joinToString(" "))
    }
    var speciesName by rememberSaveable { mutableStateOf((species ?: Species.CATTLE).name) }
    val matchedId = matched?.id ?: formulary.first().id
    var selectedDrugId by rememberSaveable(matchedId) { mutableStateOf(matchedId) }
    var weightText by rememberSaveable {
        mutableStateOf(weightKg?.let(::formatAmount).orEmpty())
    }

    val drug = remember(selectedDrugId) { findDrug(selectedDrugId) ?: formulary.first() }
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    val selectedSpecies = runCatching { Species.valueOf(speciesName) }.getOrNull() ?: Species.OTHER
    val result: DosageResult? = remember(drug, selectedSpecies, weightText, concentration) {
        calculateDosage(drug, selectedSpecies, weightText.toDoubleOrNull() ?: 0.0, concentration)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dosage & Syringe Calculator", style = MaterialTheme.typography.titleSmall)
        SelectionDialogField("Species", speciesName, Species.entries.map { it.name }) {
            speciesName = it
        }
        SelectionDialogField("Drug", drug.name, formulary.map { it.name }) { name ->
            findDrugByName(name)?.let { found -> selectedDrugId = found.id }
        }
        SelectionDialogField(
            label = "Bottle concentration",
            value = "${formatAmount(concentration)} mg/mL",
            options = drug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
        ) { label ->
            label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it }
        }
        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DosageResultPanel(result, drug.contraindications[selectedSpecies])
    }
}

@Composable
private fun SelectionDialogField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $value", modifier = Modifier.weight(1f))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                onSelect(option)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun DosageResultPanel(result: DosageResult?, speciesAlert: String?) {
    if (result == null) {
        Text(
            "No standard dose is defined for this species and weight. Adjust the " +
                "selection and verify against a current formulary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ResultLine(
                    "Recommended Dose (mg)",
                    "${formatAmount(result.doseMg)} mg — ${formatAmount(result.selectedMgPerKg)} mg/kg " +
                        "(range ${formatAmount(result.doseRange.minMgPerKg)}–" +
                        "${formatAmount(result.doseRange.maxMgPerKg)} mg/kg)",
                )
                ResultLine(
                    "Syringe Volume to Draw (mL)",
                    "${formatAmount(result.syringeMl)} mL from a " +
                        "${formatAmount(result.concentrationMgPerMl)} mg/mL bottle",
                )
                ResultLine("Route & Schedule", "${result.route.label} · ${result.frequency}")
                ResultLine("Meat Withdrawal", "${result.meatWithdrawalDays} days")
                ResultLine("Milk Withdrawal", "${result.milkWithdrawalDays} days")
            }
        }
    }
    val alerts = (listOfNotNull(speciesAlert) + result?.safetyAlerts.orEmpty()).distinct()
    if (alerts.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Species Safety / Contraindication Alert",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                alerts.forEach { alert ->
                    Text(
                        "• $alert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
