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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prudent66.vetguide.domain.formulary.DoseRoute
import com.prudent66.vetguide.domain.formulary.DosageResult
import com.prudent66.vetguide.domain.formulary.WildlifeDosageResult
import com.prudent66.vetguide.domain.formulary.DomesticMedication
import com.prudent66.vetguide.domain.formulary.WildMedication
import com.prudent66.vetguide.domain.formulary.bestDomesticDrug
import com.prudent66.vetguide.domain.formulary.bestWildDrug
import com.prudent66.vetguide.domain.formulary.calculateDosage
import com.prudent66.vetguide.domain.formulary.calculateWildlifeDosage
import com.prudent66.vetguide.domain.formulary.domesticDrugChoices
import com.prudent66.vetguide.domain.formulary.domesticHasAlternatives
import com.prudent66.vetguide.domain.formulary.domesticMedicationsFor
import com.prudent66.vetguide.domain.formulary.wildDrugChoices
import com.prudent66.vetguide.domain.formulary.wildHasAlternatives
import com.prudent66.vetguide.domain.formulary.wildMedicationsFor
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
        StableBottomSheet(onDismiss = { showSheet = false }) {
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
        Text(RESOLVED_DOSE_PRECEDENCE_NOTE, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var weightText by rememberSaveable { mutableStateOf(weightKg?.let(::formatAmount).orEmpty()) }
    val weight = weightText.toDoubleOrNull() ?: 0.0
    val medications = remember(text) { domesticMedicationsFor(text) }
    val auto = remember(text, condition, selected) { bestDomesticDrug(text, condition, selected) }
    val drugs = remember(text, auto) { domesticDrugChoices(text, auto) }
    val replaceable = remember(text) { domesticHasAlternatives(text) }
    var chosenId by rememberSaveable(text) { mutableStateOf<String?>(null) }
    var routeOverride by remember(text) { mutableStateOf<DoseRoute?>(null) }
    val drug = remember(drugs, auto, chosenId) {
        drugs.firstOrNull { it.id == (chosenId ?: auto?.id) } ?: drugs.first()
    }
    val activeDrug = remember(drug, routeOverride) { routeOverride?.let { drug.copy(route = it) } ?: drug }
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    val result = remember(activeDrug, selected, weight, concentration) {
        calculateDosage(activeDrug, selected, weight, concentration)
    }
    WeightField(weightText) { weightText = it }
    Text("Medications named in this assessment", style = MaterialTheme.typography.titleSmall)
    AlternativesNote(replaceable)
    if (medications.isEmpty()) {
        Text(
            "No medication was named — use the formulary picker below.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    medications.forEach { medication ->
        key(medication.drug.id) {
            DomesticMedicationCard(medication, selected, weight, replaceable)
        }
    }
    HorizontalDivider()
    Text("Formulary picker — adjust one drug manually", style = MaterialTheme.typography.titleSmall)
    SelectionDialogField("Species", speciesName, domesticSpeciesNames) { speciesName = it }
    SelectionDialogField("Drug", drug.name, drugs.map { it.name }) { name ->
        drugs.firstOrNull { it.name == name }?.let { chosenId = it.id; routeOverride = null }
    }
    RouteField(activeDrug.route) { routeOverride = it }
    if (activeDrug.isAnalyzedFallback) AnalyzedDrugNote()
    SelectionDialogField(
        label = "Bottle concentration",
        value = "${formatAmount(concentration)} mg/mL",
        options = activeDrug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
    ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
    val pickerOcular = medications.firstOrNull { it.drug.id == activeDrug.id }?.ocular ?: false
    DosageResultPanel(result, activeDrug.contraindications[selected], weight <= 0.0, activeDrug.isAnalyzedFallback, pickerOcular, domesticDoseFallback(activeDrug, selected, activeDrug.route, concentration))
}

@Composable
private fun DomesticMedicationCard(
    medication: DomesticMedication,
    species: Species,
    weightKg: Double,
    replaceable: Boolean = false,
) {
    val drug = medication.drug
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    val result = calculateDosage(drug, species, weightKg, concentration)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(medication.label, style = MaterialTheme.typography.titleSmall)
        SelectionDialogField(
            label = "Bottle concentration",
            value = "${formatAmount(concentration)} mg/mL",
            options = drug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
        ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
        DosageResultPanel(
            result, drug.contraindications[species], weightKg <= 0.0,
            drug.isAnalyzedFallback && !replaceable, medication.ocular,
            domesticDoseFallback(drug, species, drug.route, concentration),
        )
    }
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
    var weightText by rememberSaveable { mutableStateOf(weightKg?.let(::formatAmount).orEmpty()) }
    val weight = weightText.toDoubleOrNull() ?: 0.0
    val medications = remember(text, group) { wildMedicationsFor(text, group) }
    val auto = remember(text, condition, group) { bestWildDrug(text, condition, group) }
    val drugs = remember(text, group, auto) { wildDrugChoices(text, group, auto) }
    val replaceable = remember(text) { wildHasAlternatives(text) }
    var chosenId by rememberSaveable(text) { mutableStateOf<String?>(null) }
    var routeOverride by remember(text) { mutableStateOf<DoseRoute?>(null) }
    val drug = remember(drugs, auto, chosenId) {
        drugs.firstOrNull { it.id == (chosenId ?: auto?.id) } ?: drugs.first()
    }
    val activeDrug = remember(drug, routeOverride) { routeOverride?.let { drug.copy(route = it) } ?: drug }
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    val result = remember(activeDrug, group, wildAnimal, weight, concentration) {
        calculateWildlifeDosage(activeDrug, group, weight, concentration, wildAnimal.takeIf { it.isNotBlank() })
    }
    WeightField(weightText) { weightText = it }
    Text("Medications named in this assessment", style = MaterialTheme.typography.titleSmall)
    AlternativesNote(replaceable)
    if (medications.isEmpty()) {
        Text("No medication was named — use the formulary picker below.",
            style = MaterialTheme.typography.bodySmall)
    }
    medications.forEach { medication ->
        key(medication.drug.id) { WildMedicationCard(medication, group, wildAnimal, weight, replaceable) }
    }
    HorizontalDivider()
    Text("Formulary picker — adjust one drug manually", style = MaterialTheme.typography.titleSmall)
    SelectionDialogField("Wild species", groupLabel, ClinicalSpeciesCatalog.wildlifeLabels) {
        groupLabel = it
    }
    val animals = ClinicalSpeciesCatalog.wildAnimalsFor(group)
    SelectionDialogField("Wild animal", wildAnimal.ifBlank { animals.first() }, animals) {
        wildAnimal = it
    }
    SelectionDialogField("Drug (wildlife formulary)", drug.name, drugs.map { it.name }) {
        name -> drugs.firstOrNull { it.name == name }?.let { chosenId = it.id; routeOverride = null }
    }
    RouteField(activeDrug.route) { routeOverride = it }
    if (activeDrug.isAnalyzedFallback) AnalyzedDrugNote()
    SelectionDialogField(
        label = "Bottle concentration",
        value = "${formatAmount(concentration)} mg/mL",
        options = activeDrug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
    ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
    val pickerOcular = medications.firstOrNull { it.drug.id == activeDrug.id }?.ocular ?: false
    WildlifeDosagePanel(result, activeDrug.contraindications[group], weight <= 0.0, activeDrug.isAnalyzedFallback, pickerOcular, wildDoseFallback(activeDrug, group, activeDrug.route, concentration))
}

@Composable
private fun WildMedicationCard(
    medication: WildMedication,
    group: WildlifeGroup,
    wildAnimal: String,
    weightKg: Double,
    replaceable: Boolean = false,
) {
    val drug = medication.drug
    var concentration by rememberSaveable(drug.id) {
        mutableStateOf(drug.defaultConcentrationMgPerMl)
    }
    val result = calculateWildlifeDosage(
        drug, group, weightKg, concentration, wildAnimal.takeIf { it.isNotBlank() },
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(medication.label, style = MaterialTheme.typography.titleSmall)
        SelectionDialogField(
            label = "Bottle concentration",
            value = "${formatAmount(concentration)} mg/mL",
            options = drug.concentrationsMgPerMl.map { "${formatAmount(it)} mg/mL" },
        ) { label -> label.removeSuffix(" mg/mL").toDoubleOrNull()?.let { concentration = it } }
        WildlifeDosagePanel(
            result, drug.contraindications[group], weightKg <= 0.0,
            drug.isAnalyzedFallback && !replaceable, medication.ocular,
            wildDoseFallback(drug, group, drug.route, concentration),
        )
    }
}

private val domesticSpeciesNames: List<String> = ClinicalSpeciesCatalog.domesticLabels

/** One shared explanation instead of a repeated fallback block per option. */
@Composable
private fun AlternativesNote(visible: Boolean) {
    if (!visible) return
    Text(
        REPLACEABLE_ALTERNATIVES_NOTE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DosageResultPanel(
    result: DosageResult?, speciesAlert: String?, weightMissing: Boolean = false,
    isAnalyzed: Boolean = false, ocular: Boolean = false, fallback: String = "",
) {
    if (isAnalyzed) AnalyzedDrugNote()
    if (result == null) {
        Text(
            dosageMessage(weightMissing, fallback),
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
                Text(domesticDoseNote(result), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                topicalGuidance(result.route, ocular)?.let { guidance ->
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
    ocular: Boolean = false,
    fallback: String = "",
) {
    if (isAnalyzed) AnalyzedDrugNote()
    if (result == null) {
        Text(
            dosageMessage(weightMissing, fallback),
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
                Text(wildDoseNote(result), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                topicalGuidance(result.route, ocular)?.let { guidance ->
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
