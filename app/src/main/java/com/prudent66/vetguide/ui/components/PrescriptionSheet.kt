package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.clickable
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
import com.prudent66.vetguide.domain.formulary.WildlifeDosageResult
import com.prudent66.vetguide.domain.formulary.calculateDosage
import com.prudent66.vetguide.domain.formulary.calculateWildlifeDosage
import com.prudent66.vetguide.domain.formulary.matchDrugFromText
import com.prudent66.vetguide.domain.formulary.matchWildlifeDrugFromText
import com.prudent66.vetguide.domain.formulary.findDrug
import com.prudent66.vetguide.domain.formulary.findDrugByName
import com.prudent66.vetguide.domain.formulary.formulary
import com.prudent66.vetguide.domain.formulary.wildlifeDrugsFor
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.AnimalSubject
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup
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
    subject: AnimalSubject? = null,
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
            PrescriptionSheetBody(prescriptions, subject, weightKg)
        }
    }
}

@Composable
private fun PrescriptionSheetBody(
    prescriptions: List<String>,
    subject: AnimalSubject?,
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
        DosageCalculatorSection(prescriptions, subject, weightKg)
    }
}

@Composable
private fun DosageCalculatorSection(
    prescriptions: List<String>,
    subject: AnimalSubject?,
    weightKg: Double?,
) {
    val text = prescriptions.joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dosage & Syringe Calculator", style = MaterialTheme.typography.titleSmall)
        if (subject?.category == AnimalCategory.WILD) {
            WildlifeDosageSection(text, subject, weightKg)
        } else {
            DomesticDosageSection(text, subject?.species ?: Species.CATTLE, weightKg)
        }
    }
}

@Composable
private fun DomesticDosageSection(text: String, species: Species, weightKg: Double?) {
    var speciesName by rememberSaveable(species) {
        mutableStateOf(if (species == Species.OTHER) Species.CATTLE.name else species.name)
    }
    val selected = Species.entries.firstOrNull { it.name == speciesName && it != Species.OTHER }
        ?: Species.CATTLE
    val matched = remember(text) { matchDrugFromText(text) }
    var drugId by rememberSaveable(matched?.id) {
        mutableStateOf(matched?.id ?: formulary.first().id)
    }
    val drug = remember(drugId) { findDrug(drugId) ?: formulary.first() }
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    var weightText by rememberSaveable {
        mutableStateOf(weightKg?.let(::formatAmount).orEmpty())
    }
    val result = remember(drug, selected, weightText, concentration) {
        calculateDosage(drug, selected, weightText.toDoubleOrNull() ?: 0.0, concentration)
    }
    SelectionDialogField("Species", speciesName, domesticSpeciesNames) { speciesName = it }
    SelectionDialogField("Drug", drug.name, formulary.map { it.name }) { name ->
        findDrugByName(name)?.let { found -> drugId = found.id }
    }
    SelectionDialogField(
        label = "Bottle concentration",
        value = "${formatAmount(concentration)} mg/mL",
        options = drug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
    ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
    OutlinedTextField(
        value = weightText,
        onValueChange = { weightText = it },
        label = { Text("Weight (kg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    DosageResultPanel(result, drug.contraindications[selected])
}

@Composable
private fun WildlifeDosageSection(text: String, subject: AnimalSubject, weightKg: Double?) {
    var groupLabel by rememberSaveable(subject) {
        mutableStateOf(subject.groupLabel ?: ClinicalSpeciesCatalog.first(AnimalCategory.WILD))
    }
    val group = ClinicalSpeciesCatalog.wildlifeGroup(groupLabel) ?: WildlifeGroup.WILD_RUMINANTS
    var wildAnimal by rememberSaveable(subject) { mutableStateOf(subject.wildAnimal.orEmpty()) }
    val drugs = remember(group) { wildlifeDrugsFor(group) }
    val matched = remember(text, group) { matchWildlifeDrugFromText(text, group) }
    var drugId by rememberSaveable(group, matched?.id) {
        mutableStateOf(matched?.id ?: drugs.firstOrNull()?.id.orEmpty())
    }
    val drug = remember(drugId, group) {
        wildlifeDrugsFor(group).firstOrNull { it.id == drugId } ?: wildlifeDrugsFor(group).firstOrNull()
    }
    var concentration by rememberSaveable(drug?.id) {
        mutableStateOf(drug?.defaultConcentrationMgPerMl ?: 200.0)
    }
    var weightText by rememberSaveable {
        mutableStateOf(weightKg?.let(::formatAmount).orEmpty())
    }
    val result = remember(drug, group, wildAnimal, weightText, concentration) {
        drug?.let {
            calculateWildlifeDosage(
                it, group, weightText.toDoubleOrNull() ?: 0.0, concentration,
                wildAnimal.takeIf { animal -> animal.isNotBlank() },
            )
        }
    }
    SelectionDialogField("Wild species", groupLabel, ClinicalSpeciesCatalog.wildlifeLabels) {
        groupLabel = it
    }
    val animals = ClinicalSpeciesCatalog.wildAnimalsFor(group)
    SelectionDialogField("Wild animal", wildAnimal.ifBlank { animals.first() }, animals) {
        wildAnimal = it
    }
    SelectionDialogField("Drug (wildlife formulary)", drug?.name.orEmpty(), drugs.map { it.name }) {
        name -> drugs.firstOrNull { it.name == name }?.let { drugId = it.id }
    }
    val concentrations = drug?.concentrationsMgPerMl ?: listOf(concentration)
    SelectionDialogField(
        label = "Bottle concentration",
        value = "${formatAmount(concentration)} mg/mL",
        options = concentrations.map { "${formatAmount(it)} mg/mL" },
    ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
    OutlinedTextField(
        value = weightText,
        onValueChange = { weightText = it },
        label = { Text("Weight (kg)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    WildlifeDosagePanel(result, drug?.contraindications?.get(group))
}

private val domesticSpeciesNames: List<String> = Species.entries
    .filter { it != Species.OTHER }
    .map { it.name }

@Composable
private fun SelectionDialogField(
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
private fun WildlifeDosagePanel(result: WildlifeDosageResult?, speciesAlert: String?) {
    if (result == null) {
        Text(
            "No wildlife dose is defined for this group and weight. Adjust the " +
                "selection and verify against a current wildlife formulary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ResultLine(
                    "Recommended Dose (mg)",
                    "${formatAmount(result.doseMg)} mg — ${formatAmount(result.selectedMgPerKg)} mg/kg " +
                        "(range ${formatAmount(result.doseRange.minMgPerKg)}–" +
                        "${formatAmount(result.doseRange.maxMgPerKg)} mg/kg)",
                )
                ResultLine(
                    "Syringe Volume to Draw (mL)",
                    "${formatAmount(result.syringeMl)} mL from a " +
                        "${formatAmount(result.concentrationMgPerMl)} mg/mL bottle for " +
                        "${formatAmount(result.weightKg)} kg",
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
