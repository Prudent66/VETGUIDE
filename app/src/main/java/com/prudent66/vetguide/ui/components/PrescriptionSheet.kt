package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.R
import com.prudent66.vetguide.domain.formulary.DosageResult
import com.prudent66.vetguide.domain.formulary.calculateDosage
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
        DosageCalculatorSection(species, weightKg)
    }
}

@Composable
private fun DosageCalculatorSection(species: Species?, weightKg: Double?) {
    var speciesName by rememberSaveable { mutableStateOf((species ?: Species.CATTLE).name) }
    var drugId by rememberSaveable { mutableStateOf(formulary.first().id) }
    var concentration by rememberSaveable {
        mutableStateOf(formulary.first().defaultConcentrationMgPerMl)
    }
    var weightText by rememberSaveable {
        mutableStateOf(weightKg?.let(::formatAmount).orEmpty())
    }

    val drug = remember(drugId) { findDrug(drugId) ?: formulary.first() }
    val selectedSpecies = runCatching { Species.valueOf(speciesName) }.getOrNull() ?: Species.OTHER
    val result: DosageResult? = remember(drug, selectedSpecies, weightText, concentration) {
        calculateDosage(drug, selectedSpecies, weightText.toDoubleOrNull() ?: 0.0, concentration)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dosage & Syringe Calculator", style = MaterialTheme.typography.titleSmall)
        DropdownField("Species", speciesName, Species.entries.map { it.name }) {
            speciesName = it
        }
        DropdownField("Drug", drug.name, formulary.map { it.name }) { name ->
            findDrugByName(name)?.let { found ->
                drugId = found.id
                concentration = found.defaultConcentrationMgPerMl
            }
        }
        DropdownField(
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
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                painter = painterResource(R.drawable.nav_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
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
