package com.prudent66.vetguide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.formulary.DosageResult
import com.prudent66.vetguide.domain.formulary.WildlifeDosageResult
import com.prudent66.vetguide.domain.formulary.bestDomesticDrug
import com.prudent66.vetguide.domain.formulary.bestWildDrug
import com.prudent66.vetguide.domain.formulary.calculateDosage
import com.prudent66.vetguide.domain.formulary.calculateWildlifeDosage
import com.prudent66.vetguide.domain.formulary.domesticDrugsFor
import com.prudent66.vetguide.domain.formulary.wildDrugsFor
import com.prudent66.vetguide.domain.model.AnimalCategory
import com.prudent66.vetguide.domain.model.AnimalSubject
import com.prudent66.vetguide.domain.model.ClinicalSpeciesCatalog
import com.prudent66.vetguide.domain.model.Species
import com.prudent66.vetguide.domain.model.WildlifeGroup

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
    condition: String? = null,
    analysisText: String = "",
) {
    if (prescriptions.isEmpty()) return
    var showSheet by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
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
            PrescriptionSheetBody(prescriptions, subject, weightKg, condition, analysisText)
        }
    }
}

@Composable
private fun PrescriptionSheetBody(
    prescriptions: List<String>,
    subject: AnimalSubject?,
    weightKg: Double?,
    condition: String?,
    analysisText: String,
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
        DosageCalculatorSection(prescriptions, subject, weightKg, condition, analysisText)
    }
}

@Composable
private fun DosageCalculatorSection(
    prescriptions: List<String>,
    subject: AnimalSubject?,
    weightKg: Double?,
    condition: String?,
    analysisText: String,
) {
    val text = (prescriptions + listOf(analysisText))
        .filter { it.isNotBlank() }
        .joinToString(" ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dosage & Syringe Calculator", style = MaterialTheme.typography.titleSmall)
        if (subject?.category == AnimalCategory.WILD) {
            WildlifeDosageSection(text, subject, weightKg, condition)
        } else {
            DomesticDosageSection(text, subject?.species ?: Species.CATTLE, weightKg, condition)
        }
    }
}

@Composable
private fun DomesticDosageSection(
    text: String, species: Species, weightKg: Double?, condition: String?,
) {
    var speciesName by rememberSaveable(species) {
        mutableStateOf(ClinicalSpeciesCatalog.label(species))
    }
    val selected = ClinicalSpeciesCatalog.domesticSpecies(speciesName) ?: Species.CATTLE
    val drugs = remember(text) { domesticDrugsFor(text) }
    val auto = remember(text, condition, selected) { bestDomesticDrug(text, condition, selected) }
    var chosenId by rememberSaveable(text) { mutableStateOf<String?>(null) }
    val drug = remember(drugs, auto, chosenId) {
        drugs.firstOrNull { it.id == (chosenId ?: auto?.id) } ?: drugs.first()
    }
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    var weightText by rememberSaveable { mutableStateOf(weightKg?.let(::formatAmount).orEmpty()) }
    val result = remember(drug, selected, weightText, concentration) {
        calculateDosage(drug, selected, weightText.toDoubleOrNull() ?: 0.0, concentration)
    }
    val weightMissing = weightText.toDoubleOrNull()?.let { it <= 0.0 } ?: true
    SelectionDialogField("Species", speciesName, domesticSpeciesNames) { speciesName = it }
    SelectionDialogField("Drug", drug.name, drugs.map { it.name }) { name ->
        drugs.firstOrNull { it.name == name }?.let { chosenId = it.id }
    }
    if (drug.isAnalyzedFallback) AnalyzedDrugNote()
    SelectionDialogField(
        label = "Bottle concentration",
        value = "${formatAmount(concentration)} mg/mL",
        options = drug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
    ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
    WeightField(weightText) { weightText = it }
    DosageResultPanel(
        result, drug.contraindications[selected], weightMissing, drug.isAnalyzedFallback,
    )
}

@Composable
private fun WildlifeDosageSection(
    text: String, subject: AnimalSubject, weightKg: Double?, condition: String?,
) {
    var groupLabel by rememberSaveable(subject) {
        mutableStateOf(subject.groupLabel ?: ClinicalSpeciesCatalog.first(AnimalCategory.WILD))
    }
    val group = ClinicalSpeciesCatalog.wildlifeGroup(groupLabel) ?: WildlifeGroup.WILD_RUMINANTS
    var wildAnimal by rememberSaveable(subject) { mutableStateOf(subject.wildAnimal.orEmpty()) }
    val drugs = remember(text, group) { wildDrugsFor(text, group) }
    val auto = remember(text, condition, group) { bestWildDrug(text, condition, group) }
    var chosenId by rememberSaveable(text) { mutableStateOf<String?>(null) }
    val drug = remember(drugs, auto, chosenId) {
        drugs.firstOrNull { it.id == (chosenId ?: auto?.id) } ?: drugs.first()
    }
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    var weightText by rememberSaveable { mutableStateOf(weightKg?.let(::formatAmount).orEmpty()) }
    val result = remember(drug, group, wildAnimal, weightText, concentration) {
        calculateWildlifeDosage(
            drug, group, weightText.toDoubleOrNull() ?: 0.0, concentration,
            wildAnimal.takeIf { it.isNotBlank() },
        )
    }
    val weightMissing = weightText.toDoubleOrNull()?.let { it <= 0.0 } ?: true
    SelectionDialogField("Wild species", groupLabel, ClinicalSpeciesCatalog.wildlifeLabels) {
        groupLabel = it
    }
    val animals = ClinicalSpeciesCatalog.wildAnimalsFor(group)
    SelectionDialogField("Wild animal", wildAnimal.ifBlank { animals.first() }, animals) {
        wildAnimal = it
    }
    SelectionDialogField("Drug (wildlife formulary)", drug.name, drugs.map { it.name }) {
        name -> drugs.firstOrNull { it.name == name }?.let { chosenId = it.id }
    }
    if (drug.isAnalyzedFallback) AnalyzedDrugNote()
    SelectionDialogField(
        label = "Bottle concentration",
        value = "${formatAmount(concentration)} mg/mL",
        options = drug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
    ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
    WeightField(weightText) { weightText = it }
    WildlifeDosagePanel(
        result, drug.contraindications[group], weightMissing, drug.isAnalyzedFallback,
    )
}

private val domesticSpeciesNames: List<String> = ClinicalSpeciesCatalog.domesticLabels

@Composable
private fun DosageResultPanel(
    result: DosageResult?, speciesAlert: String?, weightMissing: Boolean = false,
    isAnalyzed: Boolean = false,
) {
    if (isAnalyzed) AnalyzedDrugNote()
    if (result == null) {
        Text(
            dosageMessage(
                weightMissing,
                "No standard dose is defined for this species and weight. Adjust the " +
                    "selection and verify against a current formulary.",
            ),
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
                    volumeLabel(result.route),
                    "${formatAmount(result.syringeMl)} mL from a " +
                        "${formatAmount(result.concentrationMgPerMl)} mg/mL bottle",
                )
                ResultLine("Route & Schedule", "${result.route.label} · ${result.frequency}")
                topicalGuidance(result.route)?.let { guidance ->
                    Text(
                        guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ResultLine("Meat Withdrawal", "${result.meatWithdrawalDays} days")
                ResultLine("Milk Withdrawal", "${result.milkWithdrawalDays} days")
            }
        }
    }
    val alerts = (listOfNotNull(speciesAlert) + result?.safetyAlerts.orEmpty()).distinct()
    SafetyAlertCard(alerts)
}

@Composable
private fun WildlifeDosagePanel(
    result: WildlifeDosageResult?,
    speciesAlert: String?,
    weightMissing: Boolean = false,
    isAnalyzed: Boolean = false,
) {
    if (isAnalyzed) AnalyzedDrugNote()
    if (result == null) {
        Text(
            dosageMessage(
                weightMissing,
                "No wildlife dose is defined for this group and weight. Adjust the " +
                    "selection and verify against a current wildlife formulary.",
            ),
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
                    volumeLabel(result.route),
                    "${formatAmount(result.syringeMl)} mL from a " +
                        "${formatAmount(result.concentrationMgPerMl)} mg/mL bottle for " +
                        "${formatAmount(result.weightKg)} kg",
                )
                ResultLine("Route & Schedule", "${result.route.label} · ${result.frequency}")
                topicalGuidance(result.route)?.let { guidance ->
                    Text(
                        guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ResultLine("Meat Withdrawal", "${result.meatWithdrawalDays} days")
                ResultLine("Milk Withdrawal", "${result.milkWithdrawalDays} days")
            }
        }
    }
    val alerts = (listOfNotNull(speciesAlert) + result?.safetyAlerts.orEmpty()).distinct()
    SafetyAlertCard(alerts)
}
